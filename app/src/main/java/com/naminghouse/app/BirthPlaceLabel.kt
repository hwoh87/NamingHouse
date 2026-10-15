package com.naminghouse.app

import com.samramanshang.manseryeok.orrery.model.City

/** Same-name cities can have different solar-time corrections; keep their region visible. */
val City.namingLabel: String
    get() = if (region.isNullOrBlank() || region == name) name else "$name ($region)"
