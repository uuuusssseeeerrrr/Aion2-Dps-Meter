package com.tbread.entity

import kotlinx.serialization.Serializable

@Serializable
data class NativeKeyPayload(
    val keyCode: Int,
    val keyText: String,
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean
)