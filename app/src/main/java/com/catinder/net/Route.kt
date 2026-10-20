package com.catinder.net

// Маска эндпоинта для имени спана и атрибута http.route.
// Ставится тегом на Request; без тега маршрут не известен — спан именуем сырым путём.
data class Route(val template: String)
