package com.example.huyakon.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpaceErrorDto(
    val stage: String? = null,
    val type: String? = null,
    val message: String? = null,
    val hint: String? = null,
)
