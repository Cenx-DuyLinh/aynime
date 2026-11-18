package dev.animethor

import android.net.Uri

object Addresses {
    const val INTERNAL_HOST = "appassets.androidplatform.net"
    const val INTERNAL_ORIGIN = "https://$INTERNAL_HOST"
    const val HOME_URL = "$INTERNAL_ORIGIN/assets/app/home.html"
    private const val SEARCH_PAGE_URL = "$INTERNAL_ORIGIN/assets/app/search.html"
    private const val ERROR_PAGE_URL = "$INTERNAL_ORIGIN/assets/app/error.html"

    private val addressPattern = Regex("""^(https?://)?[^\s/]+\.[a-zA-Z]{2,}(:\d+)?(/\S*)?$""")
    private val schemePattern = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")

    fun looksLikeAddress(text: String): Boolean = addressPattern.matches(text.trim())

    fun urlFromAddress(text: String): String {
        val trimmedText = text.trim()
        return if (schemePattern.containsMatchIn(trimmedText)) trimmedText else "https://$trimmedText"
    }

    fun searchPageUrl(query: String): String = SEARCH_PAGE_URL + "?q=" + Uri.encode(query)

    fun errorPageUrl(failedUrl: String, reason: String): String =
        ERROR_PAGE_URL + "?url=" + Uri.encode(failedUrl) + "&reason=" + Uri.encode(reason)

    fun isErrorPage(url: String?): Boolean = url != null && url.startsWith(ERROR_PAGE_URL)

    fun googleSearchUrl(query: String): String = "https://www.google.com/search?q=" + Uri.encode(query)

    fun googleSiteSearchUrl(domain: String, query: String): String = googleSearchUrl("site:$domain $query")

    fun isInternal(url: String?): Boolean = url != null && Uri.parse(url).host == INTERNAL_HOST

    /** True only for an outside http(s) address, so a retry can never run a script or open an internal page. */
    fun isRetryableWebAddress(url: String): Boolean {
        val uri = Uri.parse(url)
        val isWeb = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        return isWeb && !uri.host.isNullOrEmpty() && uri.host != INTERNAL_HOST
    }

    fun isGoogleSearch(url: String?): Boolean {
        if (url == null) {
            return false
        }
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        return host.contains("google.") && (uri.path ?: "").startsWith("/search")
    }

    fun isWebPage(url: String?): Boolean = url != null && !isInternal(url) && url.startsWith("http")

    fun siteDomain(url: String?): String? {
        val host = url?.let { Uri.parse(it).host } ?: return null
        return host.removePrefix("www.").lowercase()
    }

    fun registrableDomain(host: String): String {
        val labels = host.lowercase().removePrefix("www.").split(".")
        if (labels.size <= 2) {
            return labels.joinToString(".")
        }
        val hasCountrySecondLevel = labels[labels.size - 1].length == 2 && labels[labels.size - 2].length <= 3
        val labelCount = if (hasCountrySecondLevel) 3 else 2
        return labels.takeLast(labelCount).joinToString(".")
    }

    fun withDomain(url: String, newDomain: String): String {
        val address = Uri.parse(url)
        val oldHost = address.host ?: return url
        val newHost = if (oldHost.startsWith("www.")) "www.$newDomain" else newDomain
        return address.buildUpon().encodedAuthority(newHost).build().toString()
    }

    fun addressBarText(url: String?): String {
        if (url == null || url == HOME_URL) {
            return ""
        }
        if (url.startsWith(SEARCH_PAGE_URL)) {
            return Uri.parse(url).getQueryParameter("q") ?: ""
        }
        if (url.startsWith(ERROR_PAGE_URL)) {
            return addressBarText(Uri.parse(url).getQueryParameter("url"))
        }
        return url.removePrefix("https://").removePrefix("http://")
    }
}
