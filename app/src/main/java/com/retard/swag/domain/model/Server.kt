package com.retard.swag.domain.model

data class Server(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val config: String, // This will hold the Xray config in JSON format
    val ping: Int? = null
)