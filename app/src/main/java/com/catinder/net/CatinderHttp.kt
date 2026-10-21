package com.catinder.net

import com.catinder.logging.MiniLogger
import okhttp3.OkHttpClient

object CatinderHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                var request = chain.request()
                // Спан стартует до инъекции заголовков: traceparent берём из его контекста.
                // Имя: из тега Route, без тега — сырой путь.
                val route = request.tag(Route::class.java)?.template
                val span =
                    ru.ok.tracer.opentelemetry.TracerOpenTelemetry.getTracer("http-client")
                        .spanBuilder(request.method + " " + (route ?: request.url.encodedPath))
                        .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT)
                        .startSpan()
                val scope = span.makeCurrent()
                span.setAttribute("http.method", request.method)
                span.setAttribute("http.url", request.url.toString())
                if (route != null) {
                    span.setAttribute("http.route", route)
                }
                span.setAttribute("net.peer.name", request.url.host)
                span.setAttribute("net.peer.port", request.url.port.toLong())
                val traceHeaders = traceParentHeaders()
                if (traceHeaders.isNotEmpty()) {
                    val builder = request.newBuilder()
                    traceHeaders.forEach { (name, value) -> builder.header(name, value) }
                    request = builder.build()
                    MiniLogger.d("traceparent injected: ${traceHeaders["traceparent"]}")
                }
                MiniLogger.d("http request: ${request.method} ${request.url.encodedPath}")
                val response =
                    try {
                        chain.proceed(request)
                    } catch (t: Throwable) {
                        span.recordException(t)
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
                        span.end()
                        scope.close()
                        MiniLogger.e(
                            "http request failed: ${request.method} ${request.url.encodedPath}",
                            t,
                        )
                        throw t
                    }
                span.setAttribute("http.status_code", response.code.toLong())
                if (response.code >= 400) {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
                }
                span.end()
                scope.close()
                MiniLogger.d("http response: ${response.code} ${request.url.encodedPath}")
                response
            }
            .build()
    }
}
