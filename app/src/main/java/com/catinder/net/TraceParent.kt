package com.catinder.net

@Suppress("SpellCheckingInspection")
fun traceParentHeaders(): Map<String, String> = buildMap {
    try {
        val context = io.opentelemetry.api.trace.Span.current().spanContext
        if (context.isValid) {
            val traceparent =
                "00-${context.traceId}-${context.spanId}-${context.traceFlags.asHex()}"
            this["traceparent"] = traceparent

            val tracestate = buildString {
                context.traceState.forEach { key, value ->
                    if (isNotEmpty()) append(',')
                    append(key).append('=').append(value)
                }
            }
            if (tracestate.isNotEmpty()) {
                this["tracestate"] = tracestate
            }
        }
    } catch (_: Throwable) {}
}
