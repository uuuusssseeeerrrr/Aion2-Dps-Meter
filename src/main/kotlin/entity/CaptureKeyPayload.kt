package com.tbread.entity

import kotlinx.serialization.Serializable

@Serializable
data class CaptureKeyPayload(
    val keyCode: Int,
    val keyName: String,
    val text: String,
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean
)