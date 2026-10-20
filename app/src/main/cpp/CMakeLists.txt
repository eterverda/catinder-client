cmake_minimum_required(VERSION 3.22.1)
project(catcodec C)

# Отладочная информация — оставляем символы в бинаре, без них нативные
# стектрейсы не разобрать.
add_compile_options(-g)

add_library(catcodec SHARED catcodec.c)

# Линковка с выравниванием сегментов на 16k: современные устройства
# используют страницы по 16 КБ.
target_link_options(catcodec PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(catcodec PRIVATE ${log-lib})
