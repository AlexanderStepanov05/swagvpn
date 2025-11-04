package com.retard.swag.service

import android.net.Uri
import com.retard.swag.domain.model.Server
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

object ConfigParser {

    fun parse(uriString: String): Server? {
        return when {
            uriString.startsWith("vless://") -> parseVless(uriString)
            else -> null
        }
    }

    fun parseSubscriptionJson(json: String): List<Server> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { index ->
                val item = arr.optJSONObject(index) ?: return@mapNotNull null
                buildServerFromSubscriptionItem(item)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun buildServerFromSubscriptionItem(item: JSONObject): Server? {
        val remarks = item.optString("remarks", "")
        val outbounds = item.optJSONArray("outbounds") ?: return null
        if (outbounds.length() == 0) return null

        val dns = item.optJSONObject("dns")
        val routing = item.optJSONObject("routing")

        val firstOutbound = outbounds.optJSONObject(0) ?: return null
        val tag = firstOutbound.optString("tag", "")
        val settings = firstOutbound.optJSONObject("settings")
        val vnext = settings?.optJSONArray("vnext")
        val firstVnext = vnext?.optJSONObject(0)
        val address = firstVnext?.optString("address", "") ?: ""

        val (displayCountry, displaySuffix, countryCode) = deriveDisplayParts(remarks, tag, address)
        val displayName = listOf(displayCountry, displaySuffix).filter { it.isNotBlank() }.joinToString(" ")

        val configObj = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            // Инбаунды не указываем: TUN предоставляется через tunFd в startup()
            .put("outbounds", outbounds)
        dns?.let { configObj.put("dns", it) }
        routing?.let { configObj.put("routing", it) }

        val config = configObj.toString()

        return Server(
            id = UUID.randomUUID().toString(),
            name = displayName.ifBlank { remarks.ifBlank { tag.ifBlank { "Server" } } },
            country = displayCountry.ifBlank { remarks.ifBlank { tag.ifBlank { "Server" } } },
            countryCode = countryCode,
            config = config
        )
    }

    private fun parseVless(uriString: String): Server? {
        return try {
            val uri = Uri.parse(uriString)
            val raw = uriString.removePrefix("vless://")
            val uuid = raw.substringBefore('@').takeIf { it.isNotBlank() && it.contains('-') } ?: return null
            val address = uri.host ?: return null
            val port = uri.port
            if (port == -1) return null

            val serverName = uri.fragment ?: run {
                val (country, suffix, _) = deriveDisplayParts(null, null, address)
                listOf(country, suffix).filter { it.isNotBlank() }.joinToString(" ")
            }

            val jsonConfig = buildVlessJsonForTun(uuid, address, port, uri)

            Server(
                id = UUID.randomUUID().toString(),
                name = serverName,
                country = serverName,
                countryCode = "XX",
                config = jsonConfig
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun buildVlessJsonForTun(uuid: String, address: String, port: Int, uri: Uri): String {
        val security = uri.getQueryParameter("security") ?: "none"
        val type = uri.getQueryParameter("type") ?: "tcp"

        val streamSettings = when (security) {
            "reality" -> {
                """"network": "$type",
                    "security": "reality",
                    "realitySettings": {
                        "fingerprint": "${uri.getQueryParameter("fp") ?: "chrome"}",
                        "serverName": "${uri.getQueryParameter("sni") ?: address}",
                        "publicKey": "${uri.getQueryParameter("pbk") ?: ""}",
                        "shortId": "${uri.getQueryParameter("sid") ?: ""}"
                    }"""
            }
            "tls" -> {
                """"network": "$type",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${uri.getQueryParameter("sni") ?: address}"
                    }"""
            }
            else -> {
                """"network": "$type",
                    "security": "none""""
            }
        }

        val encryption = uri.getQueryParameter("encryption") ?: "none"
        val flow = uri.getQueryParameter("flow")

        val userBlock = buildString {
            append("""
                                    {
                                        "id": "$uuid",
                                        "encryption": "$encryption"""
            )
            append("\"")
            if (!flow.isNullOrBlank()) {
                append(",\n                                        \"flow\": \"$flow\"")
            }
            append("\n                                    }")
        }

        return """{
            "log": {
                "loglevel": "warning"
            },
            "inbounds": [
                {
                    "protocol": "tun",
                    "tag": "tun-in",
                    "settings": {
                        "name": "tun0",
                        "mtu": 1500,
                        "address": ["172.19.0.1/30"],
                        "auto_route": true,
                        "strict_route": true,
                        "endpoint_independent_nat": true,
                        "stack": "gvisor"
                    }
                }
            ],
            "outbounds": [
                {
                    "protocol": "vless",
                    "settings": {
                        "vnext": [
                            {
                                "address": "$address",
                                "port": $port,
                                "users": [
                                    $userBlock
                                ]
                            }
                        ]
                    },
                    "streamSettings": {
                        $streamSettings
                    },
                    "tag": "proxy"
                }
            ]
        }"""
    }

    private fun deriveDisplayParts(remarks: String?, tag: String?, address: String): Triple<String, String, String> {
        val base = when {
            !remarks.isNullOrBlank() -> remarks
            !tag.isNullOrBlank() -> tag
            else -> "Server"
        }
        val countryUpper = base.uppercase()
        val countryCode = when {
            "GERMAN" in countryUpper || "DE" == countryUpper -> "DE"
            countryUpper.startsWith("RU") || "RUSSIA" in countryUpper -> "RU"
            "USA" in countryUpper || "UNITED STATES" in countryUpper || countryUpper.startsWith("US") -> "US"
            "NL" == countryUpper || "NETHERLAND" in countryUpper -> "NL"
            "FR" == countryUpper || "FRANCE" in countryUpper -> "FR"
            "GB" == countryUpper || "UK" == countryUpper || "UNITED KINGDOM" in countryUpper -> "GB"
            "TR" == countryUpper || "TURKEY" in countryUpper -> "TR"
            else -> "XX"
        }
        val country = base

        val digits = address.filter { it.isDigit() }
        val last4 = if (digits.length >= 4) digits.takeLast(4) else digits
        val suffix = if (last4.isNotBlank()) "***$last4" else ""
        return Triple(country, suffix, countryCode)
    }
}