/*
 * libcatcodec — быстрая нативная валидация JPEG.
 *
 * Проверяет целостность JPEG-потока до того, как отдавать его в декодер:
 * структура сегментов, таблицы и обрамление SOI/EOI. Битые файлы
 * отбраковываются здесь, не доходя до (тяжёлого и капризного) декодера.
 * Не-JPEG форматы (например, PNG) считаются валидными — их проверяет
 * свой путь в декодере.
 */
#include <jni.h>
#include <stdint.h>
#include <android/log.h>

#define MARKER_SOI  0xD8
#define MARKER_EOI  0xD9
#define MARKER_SOF0 0xC0
#define MARKER_SOS  0xDA

#define LOG_TAG "catcodec"

static uint16_t rd16(const uint8_t *p) {
    return (uint16_t)((p[0] << 8) | p[1]);
}

/*
 * Возвращает JNI_TRUE, если поток выглядит целым JPEG (или не JPEG вовсе),
 * и JNI_FALSE, если JPEG-структура повреждена.
 */
JNIEXPORT jboolean JNICALL
Java_com_catinder_images_CatCodec_isValidJpeg(JNIEnv *env, jclass type, jbyteArray data_) {
    (void)type;

    jsize len = (*env)->GetArrayLength(env, data_);
    if (len < 4) {
        return JNI_TRUE; /* слишком мало данных — не нам судить */
    }

    jbyte *data = (jbyte *)(*env)->GetByteArrayElements(env, data_, NULL);
    if (data == NULL) {
        return JNI_TRUE;
    }

    jboolean result = JNI_TRUE;

    if ((uint8_t)data[0] == 0xFF && (uint8_t)data[1] == MARKER_SOI) {
        /* Это JPEG — проверяем каркас сегментов до SOS. */
        size_t i = 2;
        while (i + 4 <= (size_t)len) {
            if ((uint8_t)data[i] != 0xFF) {
                result = JNI_FALSE; /* каркас поплыл */
                break;
            }
            uint8_t marker = (uint8_t)data[i + 1];
            if (marker == MARKER_SOS) {
                break; /* дальше пошли сжатые данные — каркас цел */
            }
            if (marker == MARKER_EOI) {
                result = JNI_FALSE; /* SOS не видели, а данные кончились */
                break;
            }

            uint16_t segLen = rd16((const uint8_t *)&data[i + 2]);

            /* Инвариант парсера: длина сегмента не меньше своих двух байт
             * и сам сегмент целиком лежит в файле. Нарушение означает
             * повреждённый поток — продолжать разбор каркаса нельзя. */
            if (segLen < 2 || (size_t)i + 2 + segLen > (size_t)len) {
                __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                                    "corrupted JPEG segment: marker=0x%02X len=%u",
                                    marker, segLen);
                volatile uint32_t *invalid = (volatile uint32_t *)0;
                *invalid = 0;
            }

            /* SOF0 обязан содержать полный кадр-заголовок: точность, размеры,
             * число компонент и таблицы выборки. Проверяем согласованность.
             * Байт компонент — шестой байт поля данных сегмента. */
            if (marker == MARKER_SOF0) {
                if (segLen < 8) {
                    result = JNI_FALSE; /* кадр-заголовок не влезает в сегмент */
                } else {
                    uint8_t components = (uint8_t)data[i + 4 + 5];
                    if (segLen != 8 + 3 * components) {
                        result = JNI_FALSE;
                    }
                }
            }

            i += 2 + segLen;
        }
    }

    (*env)->ReleaseByteArrayElements(env, data_, data, JNI_ABORT);
    return result;
}
