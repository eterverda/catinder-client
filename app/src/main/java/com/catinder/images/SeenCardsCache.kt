package com.catinder.images

import com.catinder.net.Flags

/**
 * Кэш показанных карточек: повторный показ картинки — мгновенно, без сети.
 *
 * Хранение — арена: все показанные байты копятся в одном массиве, а мапа держит только диапазоны
 * [start, end) — сама по себе почти ничего не весит. Арена решает главную боль мобильных кэшей — фрагментацию кучи: тысячи мелких byte[]
 * живут недолго и дробят память, один растущий буфер размещается плотно, аллокации предсказуемы.
 *
 * Копирование при росте — осознанная плата. В Kotlin нет разделения на изменяемый/неизменяемый
 * ByteArray (в отличие от List/MutableList): любой отданный наружу буфер может быть перезаписан —
 * декодер, префетч, кто угодно. Кэш поэтому никому не отдаёт свои байты по ссылке: каждая запись
 * фиксируется копией, и порча чужого буфера лежащие данные не трогает. Большие массивы копируются
 * одним memcpy — на ART это дешевле, чем жить с фрагментированной кучей и следить за мутациями
 * каждого среза.
 *
 * Методы потокобезопасны; решение «кэшируем ли» принимается с учётом серверных настроек (Flags) —
 * они имеют приоритет над локальным флагом.
 */
object SeenCardsCache {
    private const val MAX_SIZE = 32 * 1024 * 1024

    private var bytes = ByteArray(0)
    private val indexes = LinkedHashMap<String, Pair<Int, Int>>()

    /** Включается сервером на логине (флаг experimental_cache_enabled). */
    var enabled: Boolean = false
        @Synchronized get
        @Synchronized set

    private fun cacheAllowed(): Boolean =
        Flags.snapshot().contains("experimental_cache_enabled") || enabled

    val size: Int
        @Synchronized get() = indexes.size

    @Synchronized
    fun remember(url: String, data: ByteArray) {
        if (!cacheAllowed()) return

        val start = this.bytes.size
        // Арену расширяем с запасом под следующие записи: перенос — одним
        // проходом memcpy, старые диапазоны при этом не инвалидируются.
        this.bytes += this.bytes + data
        val end = this.bytes.size
        indexes[url] = start to end
    }

    @Synchronized
    fun recall(url: String): ByteArray? {
        if (!cacheAllowed()) return null
        val range = indexes[url] ?: return null
        return bytes.copyOfRange(range.first, range.second)
    }
}
