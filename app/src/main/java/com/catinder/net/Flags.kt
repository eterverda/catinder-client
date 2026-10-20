package com.catinder.net

import com.catinder.logging.MiniLogger
import okhttp3.Request
import org.json.JSONObject

/**
 * Настройки сессии, полученные от сервера на логине.
 *
 * Всё под одним замком: настройки не читаются (и не применяются к кэшу), пока идёт обновление —
 * иначе кэш может прочитать флаги «вразнобой» с их сменой. Читатели ходят через snapshot().
 */
object Flags {
    private val lock = Any()

    private var items: List<String> = emptyList()

    /** Снимок настроек для читателей. */
    fun snapshot(): List<String> = synchronized(lock) { items }

    /** Выход: настройки сессии больше не актуальны. */
    fun reset() {
        synchronized(lock) {
            items = emptyList()
            com.catinder.images.SeenCardsCache.enabled = false
        }
        MiniLogger.d("logout: flags dropped, cache off")
    }

    /** Асинхронный логин: запрос и применение флагов атомарны, UI не ждёт. */
    fun login(me: String, wants: String?, sessionId: String) {
        val wantsQuery = wants?.let { "&wants=$it" } ?: ""
        val request =
            Request.Builder()
                .url(
                    "${com.catinder.BuildConfig.BASE_URL}" +
                        "/login?who=$me$wantsQuery&sessionId=$sessionId"
                )
                .tag(Route::class.java, Route("/login"))
                .build()
        MiniLogger.d("login: who=$me wants=$wants")
        Thread {
            try {
                // Сеть внутри замка намеренно: флаги обновляются атомарно,
                // читатели видят либо старый, либо новый набор целиком.
                synchronized(lock) {
                    val body =
                        CatinderHttp.client.newCall(request).execute().use { it.body!!.string() }
                    val json = JSONObject(body)
                    if (json.optBoolean("success")) {
                        items =
                            json.optJSONArray("flags")?.let { a ->
                                List(a.length()) { a.getString(it) }
                            } ?: emptyList()
                        // Экспериментальный кэш показанных карточек — по флагу с сервера.
                        com.catinder.images.SeenCardsCache.enabled =
                            items.contains("experimental_cache_enabled")
                        MiniLogger.d("login ok: flags=$items")
                    } else {
                        MiniLogger.e("login failed: $body")
                    }
                }
            } catch (e: Exception) {
                MiniLogger.e("login request failed", e)
            }
        }
            .start()
    }

    /** Асинхронный голос по карточке: fire-and-forget, ошибки только в лог. */
    fun vote(kind: String, cardId: String, sessionId: String) {
        val request =
            Request.Builder()
                .url(
                    "${com.catinder.BuildConfig.BASE_URL}" +
                        "/$kind?id=$cardId&sessionId=$sessionId"
                )
                .tag(Route::class.java, Route("/$kind"))
                .build()
        Thread {
            try {
                CatinderHttp.client.newCall(request).execute().use { it.body!!.string() }
                MiniLogger.d("$kind ok: id=$cardId")
            } catch (e: Exception) {
                MiniLogger.e("$kind request failed: id=$cardId", e)
            }
        }
            .start()
    }
}
