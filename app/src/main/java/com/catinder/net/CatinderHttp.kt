package com.catinder.net

import com.catinder.logging.MiniLogger
import okhttp3.OkHttpClient

object CatinderHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                var request = chain.request()
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
                        MiniLogger.e(
                            "http request failed: ${request.method} ${request.url.encodedPath}",
                            t,
                        )
                        throw t
                    }
                MiniLogger.d("http response: ${response.code} ${request.url.encodedPath}")
                response
            }
            .build()
    }
}
