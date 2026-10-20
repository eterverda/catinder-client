package com.catinder.images

/**
 * Нативный валидатор JPEG: проверяет целостность файла до декодирования, отбраковывая битые
 * картинки раньше тяжёлого декодера.
 */
object CatCodec {
    init {
        System.loadLibrary("catcodec")
    }

    /** true — поток цел (или не JPEG); false — JPEG повреждён. */
    external fun isValidJpeg(data: ByteArray): Boolean
}
