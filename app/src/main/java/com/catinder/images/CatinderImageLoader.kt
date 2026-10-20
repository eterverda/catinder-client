package com.catinder.images

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.request.CachePolicy
import com.catinder.logging.MiniLogger
import com.catinder.net.CatinderHttp
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

object CatinderImageLoader {
    @JvmStatic
    fun init(context: Context) {
        val client =
            CatinderHttp.client
                .newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    // Уже показанные картинки отдаём из кэша без сети.
                    SeenCardsCache.recall(request.url.toString())?.let { cached ->
                        MiniLogger.d("image cache hit: ${request.url}")
                        return@addInterceptor Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(cached.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }
                    val response = chain.proceed(request)
                    val ct = response.header("Content-Type") ?: ""
                    MiniLogger.d("image content-type: $ct")
                    when {
                        // Нативная проверка целостности JPEG: битые файлы
                        // отбраковываем до декодера. Валидатор — лишь оптимизация:
                        // при любой его ошибке просто отдаём байты дальше,
                        // декодер разберётся.
                        ct.startsWith("image/jpeg") -> {
                            val bytes = response.body!!.bytes()
                            try {
                                check(CatCodec.isValidJpeg(bytes)) {
                                    "corrupted jpeg (catcodec)"
                                }
                            } catch (e: IllegalStateException) {
                                MiniLogger.e("catcodec rejected image", e)
                                throw IOException("corrupted image payload", e)
                            }
                            SeenCardsCache.remember(request.url.toString(), bytes)
                            response
                                .newBuilder()
                                .body(bytes.toResponseBody(ct.toMediaType()))
                                .build()
                        }
                        // PNG проверит декодер.
                        ct.startsWith("image/png") -> response
                        else -> {
                            response.close()
                            throw IOException("unexpected image content-type: $ct")
                        }
                    }
                }
                .build()
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .okHttpClient(client)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        )
    }
}
