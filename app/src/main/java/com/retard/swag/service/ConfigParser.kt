package com.retard.swag.service

import android.net.Uri
import com.retard.swag.domain.model.Server
import java.util.UUID

object ConfigParser {

    fun parse(uriString: String): Server? {
        return when {
            uriString.startsWith("vless://") -> parseVless(uriString)
            else -> null
        }
    }

    private fun parseVless(uriString: String): Server? {
        return try {
            val uri = Uri.parse(uriString)
            val uuid = uri.userInfo ?: return null
            val address = uri.host ?: return null
            val port = uri.port
            if (port == -1) return null

            val serverName = uri.fragment ?: "$address:$port"

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
                        "serverName": "${uri.getQueryParameter("sni") ?: ""}",
                        "publicKey": "${uri.getQueryParameter("pbk") ?: ""}",
                        "shortId": "${uri.getQueryParameter("sid") ?: ""}"
                    }""""
            }
            "tls" -> {
                """"network": "$type",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${uri.getQueryParameter("sni") ?: address}"
                    }""""
            }
            else -> {
                """"network": "$type",
                    "security": "none""""
            }
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
                        "inet4Address": "172.19.0.1/30",
                        "autoRoute": true
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
                                    {
                                        "id": "$uuid",
                                        "encryption": "${uri.getQueryParameter("encryption") ?: "none"}",
                                        "flow": "${uri.getQueryParameter("flow") ?: ""}"
                                    }
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
}