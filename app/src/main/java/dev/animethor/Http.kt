package dev.animethor

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private const val TIMEOUT_MILLISECONDS = 20_000
    private const val USER_AGENT = "Aynime/${BuildConfig.VERSION_NAME}"

    fun downloadBytes(address: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val connection = openConnection(address, headers)
        try {
            throwIfFailed(connection, address)
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun downloadText(address: String, headers: Map<String, String> = emptyMap()): String =
        downloadBytes(address, headers).toString(Charsets.UTF_8)

    fun postJson(address: String, jsonBody: String): String {
        val connection = openConnection(address, emptyMap())
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            throwIfFailed(connection, address)
            return connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(address: String, headers: Map<String, String>): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLISECONDS
        connection.readTimeout = TIMEOUT_MILLISECONDS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }

    private fun throwIfFailed(connection: HttpURLConnection, address: String) {
        val status = connection.responseCode
        if (status !in 200..299) {
            throw IOException("HTTP $status for ${address.substringBefore('?')}")
        }
    }
}
