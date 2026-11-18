package dev.animethor

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

interface BrowserEvents {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onTitleChanged(title: String)
    fun onRequestBlocked()
    fun onPopupBlocked()
    fun onNavigationBlocked(url: String)
    fun onPageLoadFailed(url: String, reason: String)
    fun onAddressChangedInPage(url: String)
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
}

object NavigationRules {
    fun allowsNavigation(currentUrl: String?, targetUrl: Uri): Boolean {
        if (currentUrl == null || Addresses.isInternal(currentUrl) || Addresses.isGoogleSearch(currentUrl)) {
            return true
        }
        val currentHost = Uri.parse(currentUrl).host ?: return true
        val targetHost = targetUrl.host ?: return false
        return Addresses.registrableDomain(currentHost) == Addresses.registrableDomain(targetHost)
    }
}

class BrowserWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val adBlocker: AdBlocker,
    private val events: BrowserEvents,
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == Addresses.INTERNAL_HOST) {
            return assetLoader.shouldInterceptRequest(url)?.apply { responseHeaders = NEVER_CACHE }
        }
        val host = url.host ?: return null
        if (adBlocker.isBlocked(host)) {
            view.post { events.onRequestBlocked() }
            return emptyResponse()
        }
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) {
            return false
        }
        val target = request.url
        val isWebAddress = target.scheme == "http" || target.scheme == "https"
        val targetHost = target.host
        val shouldBlock = when {
            !isWebAddress -> true
            targetHost == null -> true
            adBlocker.isBlocked(targetHost) -> true
            request.isRedirect -> false
            else -> !NavigationRules.allowsNavigation(view.url, target)
        }
        if (shouldBlock) {
            events.onNavigationBlocked(target.toString())
        }
        return shouldBlock
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        events.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        events.onPageFinished(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        events.onAddressChangedInPage(url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !Addresses.isInternal(request.url.toString())) {
            events.onPageLoadFailed(request.url.toString(), error.description?.toString() ?: "unknown error")
        }
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private companion object {
        val NEVER_CACHE = mapOf("Cache-Control" to "no-store")
    }
}

class BrowserChromeClient(private val events: BrowserEvents) : WebChromeClient() {

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        events.onPopupBlocked()
        return false
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        events.onTitleChanged(title ?: "")
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        events.onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        events.onExitFullscreen()
    }
}
