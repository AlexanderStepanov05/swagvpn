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

        val dns = buildDnsSection()
        val routing = buildRoutingSection()

        val firstOutbound = outbounds.optJSONObject(0)
        if (firstOutbound != null) {
            firstOutbound.put("tag", "proxy")
            val stream = firstOutbound.optJSONObject("streamSettings")
            val reality = stream?.optJSONObject("realitySettings")
            if (reality != null && !reality.has("alpn")) {
                reality.put("alpn", JSONArray().put("h2").put("http/1.1"))
            }
        }
        outbounds.put(JSONObject().put("protocol", "freedom").put("tag", "direct"))

        val (displayCountry, displaySuffix, countryCode) = run {
            val firstVnext = firstOutbound?.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0)
            val address = firstVnext?.optString("address", "") ?: ""
            deriveDisplayParts(remarks, firstOutbound?.optString("tag", ""), address)
        }
        val displayName = listOf(displayCountry, displaySuffix).filter { it.isNotBlank() }.joinToString(" ")

        val configObj = JSONObject()
            .put("log", JSONObject().put("loglevel", "info"))
            .put("inbounds", JSONArray())
            .put("outbounds", outbounds)
            .put("dns", dns)
            .put("routing", routing)

        val config = configObj.toString()

        return Server(
            id = UUID.randomUUID().toString(),
            name = displayName.ifBlank { remarks.ifBlank { firstOutbound?.optString("tag", "Server") ?: "Server" } },
            country = displayCountry.ifBlank { remarks.ifBlank { firstOutbound?.optString("tag", "Server") ?: "Server" } },
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
        val alpnParam = uri.getQueryParameter("alpn")
        val alpnArray = when {
            alpnParam.isNullOrBlank() -> "\"h2\",\"http/1.1\""
            else -> alpnParam.split(',').joinToString(separator = "\",") { "\"${it.trim()}\"" }
        }

        val streamSettings = when (security) {
            "reality" -> {
                """"network": "$type",
                    "security": "reality",
                    "realitySettings": {
                        "fingerprint": "${uri.getQueryParameter("fp") ?: "chrome"}",
                        "serverName": "${uri.getQueryParameter("sni") ?: address}",
                        "publicKey": "${uri.getQueryParameter("pbk") ?: ""}",
                        "shortId": "${uri.getQueryParameter("sid") ?: ""}",
                        "alpn": [$alpnArray]
                    }"""
            }
            "tls" -> {
                """"network": "$type",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${uri.getQueryParameter("sni") ?: address}",
                        "alpn": [$alpnArray]
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

        val dnsSection = """
            "dns": {
                "servers": [
                    "https://dns.google/dns-query",
                    "https://cloudflare-dns.com/dns-query"
                ],
                "strategy": "UseIP"
            }
        """.trimIndent()

        val routingSection = """
            "routing": {
                "rules": [
                    {
                        "type": "field",
                        "ip": [
                            "10.0.0.0/8",
                            "172.16.0.0/12",
                            "192.168.0.0/16",
                            "fc00::/7",
                            "fe80::/10"
                        ],
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "network": ["tcp","udp"],
                        "outboundTag": "proxy"
                    }
                ]
            }
        """.trimIndent()

        return """{
            "log": {
                "loglevel": "info"
            },
            "inbounds": [],
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
                },
                {
                    "protocol": "freedom",
                    "tag": "direct"
                }
            ],
            $dnsSection,
            $routingSection
        }"""
    }

    private fun buildDnsSection(): JSONObject {
        return JSONObject()
            .put("servers", JSONArray().put("https://dns.google/dns-query").put("https://cloudflare-dns.com/dns-query"))
            .put("strategy", "UseIP")
    }

    private fun buildRoutingSection(): JSONObject {
        val rules = JSONArray()
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray()
                        .put("10.0.0.0/8")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("fc00::/7")
                        .put("fe80::/10")
                    )
                    .put("outboundTag", "direct")
            )
            .put(
                JSONObject()
                    .put("type", "field")
                    .put("network", JSONArray().put("tcp").put("udp"))
                    .put("outboundTag", "proxy")
            )
        return JSONObject().put("rules", rules)
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