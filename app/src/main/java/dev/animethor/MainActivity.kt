package dev.animethor

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Choreographer
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), BrowserEvents, InternalPageActions {
    private lateinit var store: AppStore
    private lateinit var adBlocker: AdBlocker
    private lateinit var kitsunekko: KitsunekkoClient
    private lateinit var subtitleFinder: SubtitleFinder
    private lateinit var subtitlePicker: SubtitlePicker
    private lateinit var videoBridge: VideoBridge
    private lateinit var subtitleOverlay: SubtitleOverlay
    private lateinit var appUpdater: AppUpdater

    private lateinit var rootView: FrameLayout
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var blockedPill: TextView
    private lateinit var starButton: ImageButton
    private lateinit var offsetLabel: TextView
    private lateinit var subtitleControls: SubtitleControls
    private lateinit var topBar: AutoHidingTopBar
    private lateinit var animeListLookup: AnimeListLookup
    private var isSwallowingTouch = false
    private var focusTapExpectedUntilMs = 0L
    private lateinit var fullscreenContainer: FrameLayout

    private val playbackClock = PlaybackClock()
    private val subtitleSession = SubtitleSession()
    private val ticker = Handler(Looper.getMainLooper())
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var blockedOnThisPage = 0
    private var pendingResumeSeconds: Double? = null
    private var autoLoadJob: Job? = null
    private var lastAutoLoadPageKey: String? = null
    private var lastContinueSaveMs = 0L
    private var isPreviewingOverlayStyle = false
    private var videoScriptForOldWebView = ""
    private var ticksSinceEpisodeCheck = 0
    private var pageEpisodeHint: Int? = null
    private var namesShownOnPage: List<String> = emptyList()
    private var lastSearchReport: SearchReport? = null
    private var lastStatusDetail = "No subtitles loaded"
    private var stickDirection = 0
    private var heldDpadKey: Int? = null
    private var heldDpadKeyWasHandled = false

    private val stickSeekRepeater = object : Runnable {
        override fun run() {
            if (stickDirection == 0) {
                return
            }
            seekVideoBy(stickDirection * SEEK_STEP_SECONDS)
            ticker.postDelayed(this, STICK_REPEAT_MS)
        }
    }
    private var siteBeingOpened: String? = null
    private var siteOpenedAtMs = 0L

    private val openSubtitleDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { loadSubtitleFromDevice(it) } }

    private val tick = object : Runnable {
        override fun run() {
            updateSubtitleOverlay()
            subtitleControls.update(playbackClock.hasVideo(), playbackClock.isPlaying(), subtitleOverlay.bandTopFromBottomPx())
            topBar.update(playbackClock.isPlaying())
            saveContinueWatchingIfDue()
            checkForEpisodeChangeEveryFewSeconds()
            ticker.postDelayed(this, TICK_MS)
        }
    }

    private val keepVideoFramesFlowing = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val isFullscreen = fullscreenCallback != null
            if (isFullscreen) {
                fullscreenContainer.getChildAt(0)?.postInvalidateOnAnimation()
            }
            if (playbackClock.isPlaying()) {
                webView.postInvalidateOnAnimation()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = AppStore(this)
        adBlocker = AdBlocker(this)
        kitsunekko = KitsunekkoClient(filesDir)
        subtitleFinder = SubtitleFinder(kitsunekko, JimakuClient({ store.jimakuApiKey() }, filesDir), store)
        subtitlePicker = SubtitlePicker(
            activity = this,
            kitsunekko = kitsunekko,
            onFileChosen = { file -> loadMatch(subtitleFinder.matchFor(file, currentAnimeName())) },
            onLoadFromDevice = { chooseSubtitleFileOnDevice() },
        )
        appUpdater = AppUpdater(this, store)
        animeListLookup = AnimeListLookup(store)
        lifecycleScope.launch(Dispatchers.IO) { adBlocker.load() }
        bindViews()
        keepContentClearOfSystemBars()
        setUpOverlay()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            showOldWebViewMessage()
            return
        }
        setUpWebView()
        setUpToolbar()
        setUpSubtitleBar()
        setUpBackButton()
        if (savedInstanceState == null) {
            webView.loadUrl(Addresses.HOME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onStart() {
        super.onStart()
        ControllerKeyRouting.handlerWhileAynimeIsOpen = { keyCode -> handleControllerButton(keyCode) }
        ControllerKeyRouting.stickHandlerWhileAynimeIsOpen = { event -> handleLeftStick(event) }
        ControllerKeyRouting.playTranslateUsedWhileAynimeIsOpen = { packageName -> onPlayTranslateUsed(packageName) }
        ControllerKeyRouting.pressWatcherWhileAynimeIsOpen = { keyCode -> pauseIfCaptureButton(keyCode) }
        ControllerKeyRouting.startCatchingStick()
        ticker.post(tick)
        Choreographer.getInstance().postFrameCallback(keepVideoFramesFlowing)
    }

    override fun onStop() {
        ControllerKeyRouting.handlerWhileAynimeIsOpen = null
        ControllerKeyRouting.stickHandlerWhileAynimeIsOpen = null
        ControllerKeyRouting.playTranslateUsedWhileAynimeIsOpen = null
        ControllerKeyRouting.pressWatcherWhileAynimeIsOpen = null
        ticker.removeCallbacks(returnFocusToAynime)
        ticker.removeCallbacks(pauseForCapture)
        ControllerKeyRouting.stopCatchingStick()
        stopStickSeeking()
        ticker.removeCallbacks(tick)
        Choreographer.getInstance().removeFrameCallback(keepVideoFramesFlowing)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        DiagnosticsLog.add("Controller: Aynime ${if (hasFocus) "got" else "lost"} the focus")
    }

    private fun pauseIfCaptureButton(keyCode: Int) {
        val isCaptureButton = keyCode == store.captureButton()
        if (isCaptureButton) {
            ticker.removeCallbacks(pauseForCapture)
            ticker.postDelayed(pauseForCapture, CAPTURE_PAUSE_DELAY_MS)
        }
    }

    private val pauseForCapture = Runnable {
        if (playbackClock.isPlaying()) {
            videoBridge.pause()
            DiagnosticsLog.add("Paused the video for the PlayTranslate capture")
        }
    }

    private fun onPlayTranslateUsed(packageName: String) {
        DiagnosticsLog.add("Controller: $packageName was used")
        ticker.removeCallbacks(returnFocusToAynime)
        ticker.postDelayed(returnFocusToAynime, RETURN_FOCUS_DELAY_MS)
    }

    private val returnFocusToAynime = Runnable {
        if (shouldReturnFocus()) {
            tapAynimeScreen()
        }
    }

    private fun shouldReturnFocus(): Boolean {
        val isStillShownOnItsScreen = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val noAynimeDialogIsOpen = hasWindowFocus()
        return store.returnFocusAfterOtherApps() && isStillShownOnItsScreen && noAynimeDialogIsOpen && playbackClock.hasVideo()
    }

    private fun tapAynimeScreen() {
        val location = IntArray(2)
        rootView.getLocationOnScreen(location)
        val centerX = location[0] + rootView.width / 2f
        val centerY = location[1] + rootView.height / 2f
        focusTapExpectedUntilMs = SystemClock.elapsedRealtime() + FOCUS_TAP_WINDOW_MS
        val started = ControllerKeyRouting.tapScreen(aynimeDisplayId(), centerX, centerY) { completed ->
            DiagnosticsLog.add("Controller: tap on Aynime's screen ${if (completed) "done" else "cancelled"}")
        }
        if (!started) {
            focusTapExpectedUntilMs = 0L
            DiagnosticsLog.add("Controller: could not tap, the accessibility service is not running (turn it off and on)")
        }
    }

    private fun aynimeDisplayId(): Int = rootView.display?.displayId ?: Display.DEFAULT_DISPLAY

    private fun isFocusTap(event: MotionEvent): Boolean {
        val isExpected = SystemClock.elapsedRealtime() < focusTapExpectedUntilMs
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isExpected) {
            focusTapExpectedUntilMs = 0L
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        webView.destroy()
        super.onDestroy()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.root)
        webView = findViewById(R.id.web_view)
        addressBar = findViewById(R.id.address_bar)
        blockedPill = findViewById(R.id.blocked_pill)
        starButton = findViewById(R.id.star_button)
        offsetLabel = findViewById(R.id.offset_label)
        subtitleControls = SubtitleControls(
            pill = findViewById(R.id.subtitle_controls),
            statusDot = findViewById(R.id.status_dot),
            statusText = findViewById(R.id.status_short),
            offsetText = offsetLabel,
        )
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        topBar = AutoHidingTopBar(findViewById(R.id.toolbar), addressBar)
    }

    private fun keepContentClearOfSystemBars() {
        val mainColumn = findViewById<View>(R.id.main_column)
        ViewCompat.setOnApplyWindowInsetsListener(mainColumn) { view, insets ->
            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
            val bars = insets.getInsets(insetTypes)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setUpOverlay() {
        subtitleOverlay = SubtitleOverlay(findViewById(R.id.subtitle_overlay), rootView)
        subtitleOverlay.applyStyle(store.overlayStyle())
        rootView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> subtitleOverlay.updatePlacement() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("""Version/\d+\.\d+\s"""), "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = BrowserWebViewClient(assetLoader, adBlocker, this)
        webView.webChromeClient = BrowserChromeClient(this)

        val videoScript = assets.open("video_bridge.js").bufferedReader().use { it.readText() }
        videoBridge = VideoBridge(::onVideoReport, ::onEpisodeHint, ::onNameHints)
        videoBridge.install(webView, videoScript)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            videoScriptForOldWebView = videoScript
        }
        InternalPagesBridge(store, kitsunekko, animeListLookup, lifecycleScope, this).install(webView)
    }

    private fun setUpToolbar() {
        findViewById<View>(R.id.back_button).setOnClickListener { goBackInBrowser() }
        findViewById<View>(R.id.home_button).setOnClickListener {
            stopWatchingForMovedSite()
            webView.loadUrl(Addresses.HOME_URL)
        }
        findViewById<View>(R.id.reload_button).setOnClickListener { reloadPage() }
        findViewById<View>(R.id.menu_button).setOnClickListener { showMenu(it) }
        starButton.setOnClickListener { toggleSavedSite() }
        addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                addressBar.setText(Addresses.addressBarText(webView.url))
                addressBar.selectAll()
            } else {
                showFriendlyAddress()
            }
        }
        addressBar.setOnEditorActionListener { _, actionId, event ->
            val pressedGo = actionId == EditorInfo.IME_ACTION_GO
            val pressedEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (pressedGo || pressedEnter) {
                submitAddressBar()
            }
            pressedGo || pressedEnter
        }
    }

    private fun setUpSubtitleBar() {
        findViewById<View>(R.id.sync_now_button).setOnClickListener { syncToLineJustHeard() }
        findViewById<View>(R.id.offset_earlier_button).setOnClickListener { shiftSubtitles(-OFFSET_STEP_MS) }
        findViewById<View>(R.id.offset_later_button).setOnClickListener { shiftSubtitles(OFFSET_STEP_MS) }
        findViewById<View>(R.id.find_again_button).setOnClickListener { findSubtitlesAgain() }
        findViewById<View>(R.id.subtitles_button).setOnClickListener { openSubtitlePicker() }
        findViewById<View>(R.id.style_button).setOnClickListener { showOverlayStyleDialog() }
        findViewById<View>(R.id.status_short).setOnClickListener { showSearchDetails() }
        findViewById<View>(R.id.status_dot).setOnClickListener { showSearchDetails() }
        setSubtitleStatus(SubtitleState.MISSING, "No subtitles", "No subtitles loaded")
    }

    private fun setUpBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenCallback == null && !webView.canGoBack()) {
                    finish()
                    return
                }
                goBackInBrowser()
            }
        })
    }

    private fun showOldWebViewMessage() {
        MaterialAlertDialogBuilder(this)
            .setMessage("This app needs a newer Android System WebView. Update it from the Play Store and open the app again.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    private fun submitAddressBar() {
        val text = addressBar.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        hideKeyboard()
        stopWatchingForMovedSite()
        val url = if (Addresses.looksLikeAddress(text)) Addresses.urlFromAddress(text) else Addresses.searchPageUrl(text)
        webView.loadUrl(url)
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(InputMethodManager::class.java)
        inputMethodManager.hideSoftInputFromWindow(addressBar.windowToken, 0)
        addressBar.clearFocus()
        webView.requestFocus()
    }

    private fun reloadPage() {
        val currentUrl = webView.url
        val failedUrl = if (Addresses.isErrorPage(currentUrl)) Uri.parse(currentUrl).getQueryParameter("url") else null
        if (failedUrl != null) {
            webView.loadUrl(failedUrl)
        } else {
            webView.reload()
        }
    }

    private fun goBackInBrowser() {
        stopWatchingForMovedSite()
        val isOnErrorPage = Addresses.isErrorPage(webView.url)
        when {
            fullscreenCallback != null -> leaveFullscreen()
            isOnErrorPage && webView.canGoBackOrForward(-2) -> webView.goBackOrForward(-2)
            webView.canGoBack() -> webView.goBack()
        }
    }

    private fun showMenu(anchor: View) {
        val menuItems = listOf(
            MenuEntry("Find subtitles again", R.drawable.ic_menu_refresh) { findSubtitlesAgain() },
            MenuEntry("Subtitle search details", R.drawable.ic_menu_info) { showSearchDetails() },
            MenuEntry("Choose subtitles", R.drawable.ic_menu_subtitles) { openSubtitlePicker() },
            MenuEntry("Subtitle file on device", R.drawable.ic_menu_folder) { chooseSubtitleFileOnDevice() },
            MenuEntry("Subtitle style", R.drawable.ic_menu_style) { showOverlayStyleDialog() },
            MenuEntry("Controller buttons", R.drawable.ic_menu_gamepad) { ControllerButtonsDialog(this, store).show() },
            MenuEntry("Jimaku API key", R.drawable.ic_menu_cloud_download) { askForJimakuApiKey() },
            MenuEntry("Update subtitle catalog", R.drawable.ic_menu_cloud_download) { refreshSubtitleCatalog() },
            MenuEntry("Update ad block list", R.drawable.ic_menu_shield) { updateAdBlockList() },
            MenuEntry("Save Cocoon artwork to Downloads", R.drawable.ic_menu_image) { saveCocoonArtwork() },
            MenuEntry("Check for app update", R.drawable.ic_menu_update) { appUpdater.checkForUpdate() },
            MenuEntry("About Aynime ${BuildConfig.VERSION_NAME}", R.drawable.ic_menu_info) { showAbout() },
        )
        val popup = PopupMenu(this, anchor)
        menuItems.forEachIndexed { index, entry ->
            popup.menu.add(0, index, index, entry.title).setIcon(entry.iconResource)
        }
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            menuItems[item.itemId].action()
            true
        }
        popup.show()
    }

    private fun toggleSavedSite() {
        val domain = Addresses.siteDomain(webView.url) ?: return
        val willBeSaved = !store.isSiteSaved(domain)
        store.setSiteSaved(domain, willBeSaved)
        val message = if (willBeSaved) "Saved $domain to the home page" else "Removed $domain from saved sites"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updatePageControls(webView.url ?: "")
    }

    private fun updatePageControls(url: String) {
        showFriendlyAddress()
        val isStreamingPage = Addresses.isWebPage(url) && !Addresses.isGoogleSearch(url)
        starButton.isVisible = isStreamingPage
        subtitleControls.setAllowedOnThisPage(isStreamingPage)
        blockedPill.isVisible = Addresses.isWebPage(url)
        blockedPill.text = "$blockedOnThisPage"
        val domain = Addresses.siteDomain(url)
        val isSaved = domain != null && store.isSiteSaved(domain)
        starButton.setImageResource(if (isSaved) R.drawable.ic_bar_star else R.drawable.ic_bar_star_outline)
    }

    private fun showFriendlyAddress() {
        if (addressBar.hasFocus()) {
            return
        }
        addressBar.setText(friendlyAddress(webView.url))
    }

    private fun friendlyAddress(url: String?): String {
        val isStreamingPage = Addresses.isWebPage(url) && !Addresses.isGoogleSearch(url)
        val domain = Addresses.siteDomain(url)
        if (!isStreamingPage || domain == null) {
            return Addresses.addressBarText(url)
        }
        val siteName = domain.substringBefore(".")
        val animeName = currentAnimeName()
        val episode = currentEpisode()
        return listOfNotNull(siteName, animeName.ifBlank { null }, episode?.let { "Ep $it" }).joinToString("  ·  ")
    }

    private fun countBlocked() {
        blockedOnThisPage += 1
        blockedPill.text = "$blockedOnThisPage"
    }

    override fun onPageStarted(url: String) {
        blockedOnThisPage = 0
        pageEpisodeHint = null
        namesShownOnPage = emptyList()
        playbackClock.forget()
        videoBridge.forgetVideo()
        updatePageControls(url)
    }

    override fun onPageFinished(url: String) {
        if (videoScriptForOldWebView.isNotEmpty() && Addresses.isWebPage(url)) {
            webView.evaluateJavascript(videoScriptForOldWebView, null)
        }
        updatePageControls(url)
        followMovedSite(url)
        tryAutoLoadSubtitles()
    }

    override fun onPageLoadFailed(url: String, reason: String) {
        webView.loadUrl(Addresses.errorPageUrl(url, reason))
    }

    private fun followMovedSite(url: String) {
        val oldDomain = siteBeingOpened ?: return
        val stillWatching = SystemClock.elapsedRealtime() - siteOpenedAtMs < MOVED_SITE_WINDOW_MS
        if (!stillWatching) {
            stopWatchingForMovedSite()
            return
        }
        if (!Addresses.isWebPage(url) || Addresses.isGoogleSearch(url)) {
            return
        }
        val newDomain = Addresses.siteDomain(url) ?: return
        if (Addresses.registrableDomain(newDomain) == Addresses.registrableDomain(oldDomain)) {
            return
        }
        stopWatchingForMovedSite()
        moveSiteEverywhere(oldDomain, newDomain)
    }

    private fun moveSiteEverywhere(oldDomain: String, newDomain: String) {
        store.changeSavedSiteAddress(oldDomain, newDomain)
        store.moveSiteInWatchedLists(oldDomain, newDomain)
        DiagnosticsLog.add("Site moved: $oldDomain -> $newDomain (saved sites and My anime updated)")
        updatePageControls(webView.url ?: "")
    }

    private fun watchForMovedSite(url: String) {
        siteBeingOpened = Addresses.siteDomain(url)
        siteOpenedAtMs = SystemClock.elapsedRealtime()
    }

    private fun stopWatchingForMovedSite() {
        siteBeingOpened = null
    }

    override fun openSavedSite(domain: String) {
        val address = "https://$domain"
        watchForMovedSite(address)
        webView.loadUrl(address)
    }

    override fun editSavedSite(domain: String) {
        val choices = arrayOf("Change address", "Remove from saved sites")
        MaterialAlertDialogBuilder(this)
            .setTitle(domain)
            .setItems(choices) { _, position ->
                if (position == 0) {
                    askForNewSiteAddress(domain)
                } else {
                    store.setSiteSaved(domain, false)
                    reloadIfInternalPage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askForNewSiteAddress(oldDomain: String) {
        val field = EditText(this).apply {
            setText(oldDomain)
            setSingleLine()
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New address for $oldDomain")
            .setView(field)
            .setPositiveButton("Save") { _, _ ->
                val newDomain = Addresses.siteDomain(Addresses.urlFromAddress(field.text.toString()))
                if (newDomain != null) {
                    store.changeSavedSiteAddress(oldDomain, newDomain)
                    reloadIfInternalPage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reloadIfInternalPage() {
        if (Addresses.isInternal(webView.url)) {
            webView.reload()
        }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Aynime ${BuildConfig.VERSION_NAME}")
            .setMessage(
                "Top screen: browser with a Japanese subtitle box.\n" +
                    "Bottom screen: PlayTranslate reads the box.\n\n" +
                    "Subtitles: kitsunekko mirror (GitHub). Anime search: AniList.\n" +
                    "Updates: ${store.updateRepository().ifBlank { "not set up" }}",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onTitleChanged(title: String) {
        showFriendlyAddress()
        tryAutoLoadSubtitles()
    }

    override fun onAddressChangedInPage(url: String) {
        tryAutoLoadSubtitles()
    }

    private fun checkForEpisodeChangeEveryFewSeconds() {
        ticksSinceEpisodeCheck += 1
        if (ticksSinceEpisodeCheck >= EPISODE_CHECK_EVERY_TICKS) {
            ticksSinceEpisodeCheck = 0
            tryAutoLoadSubtitles()
        }
    }

    private fun findSubtitlesAgain() {
        val episode = currentEpisode()
        if (episode == null) {
            Toast.makeText(this, "No episode number on this page, so choose the subtitles yourself", Toast.LENGTH_SHORT).show()
            openSubtitlePicker()
            return
        }
        lastAutoLoadPageKey = null
        tryAutoLoadSubtitles()
    }

    override fun onRequestBlocked() = countBlocked()

    override fun onPopupBlocked() = countBlocked()

    override fun onNavigationBlocked(url: String) {
        countBlocked()
        val target = Uri.parse(url)
        val isWebAddress = target.scheme == "http" || target.scheme == "https"
        val snackbar = Snackbar.make(rootView, "Blocked a jump to ${target.host ?: target.scheme}", Snackbar.LENGTH_LONG)
        if (isWebAddress && !adBlocker.isBlocked(target.host ?: "")) {
            snackbar.setAction("Open anyway") { webView.loadUrl(url) }
        }
        snackbar.show()
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenCallback != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenCallback = callback
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        fullscreenContainer.isVisible = true
        setSystemBarsHidden(true)
    }

    override fun onExitFullscreen() {
        fullscreenContainer.removeAllViews()
        fullscreenContainer.isVisible = false
        fullscreenCallback = null
        setSystemBarsHidden(false)
    }

    private fun leaveFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        onExitFullscreen()
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun openUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun resumeWatching() {
        val watching = store.continueWatching() ?: return
        resumeWatchingAt(watching.url, watching.seconds)
    }

    override fun resumeWatchingAt(url: String, seconds: Double) {
        pendingResumeSeconds = seconds
        watchForMovedSite(url)
        webView.loadUrl(url)
    }

    override fun showAnimeOptions(animeName: String, shownName: String) {
        val choices = arrayOf("Choose the right anime (name and cover)", "Remove from my list")
        MaterialAlertDialogBuilder(this)
            .setTitle(shownName.ifBlank { animeName })
            .setItems(choices) { _, position ->
                if (position == 0) {
                    askWhichAnimeOnAniList(animeName, shownName.ifBlank { animeName })
                } else {
                    store.removeAnime(animeName)
                    refreshHomePage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askWhichAnimeOnAniList(animeName: String, searchText: String) {
        val field = EditText(this).apply {
            setText(searchText)
            setSingleLine()
            selectAll()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Search AniList")
            .setView(field)
            .setPositiveButton("Search") { _, _ -> showAniListChoices(animeName, field.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAniListChoices(animeName: String, searchText: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { AniListClient.search(searchText) } }
            val matches = result.getOrDefault(emptyList()).sortedByDescending { it.popularity }
            if (matches.isEmpty()) {
                val reason = result.exceptionOrNull()?.message ?: "nothing found"
                Toast.makeText(this@MainActivity, "AniList: $reason", Toast.LENGTH_LONG).show()
                return@launch
            }
            val labels = matches.map { anime -> aniListChoiceLabel(anime) }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Which one is it?")
                .setItems(labels) { _, position ->
                    store.saveLookedUpAnime(animeName, matches[position])
                    DiagnosticsLog.add("My anime: \"$animeName\" set by hand to #${matches[position].id}")
                    refreshHomePage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun aniListChoiceLabel(anime: AniListAnime): String {
        val romajiTitle = anime.titles.firstOrNull { it != anime.displayTitle }
        val details = listOfNotNull(romajiTitle, readableFormat(anime.format), anime.year?.toString())
        return "${anime.displayTitle}\n${details.joinToString(" · ")}"
    }

    private fun readableFormat(format: String?): String? = when (format) {
        null -> null
        "TV" -> "TV series"
        "TV_SHORT" -> "TV short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA", "ONA" -> format
        "MUSIC" -> "Music video"
        else -> format.lowercase()
    }

    private fun onVideoReport(report: VideoReport) {
        playbackClock.update(report)
        val resumeSeconds = pendingResumeSeconds ?: return
        pendingResumeSeconds = null
        videoBridge.seekTo(resumeSeconds)
    }

    private fun currentAnimeName(): String = PageTitles.animeName(webView.title ?: "")

    private fun currentEpisode(): Int? = EpisodeNumbers.fromPage(webView.title ?: "", webView.url ?: "") ?: pageEpisodeHint

    private fun onNameHints(names: List<String>) {
        if (names == namesShownOnPage) {
            return
        }
        namesShownOnPage = names
        DiagnosticsLog.add("Page shows the names: ${names.joinToString(" | ")}")
        if (subtitleSession.track == null) {
            lastAutoLoadPageKey = null
            tryAutoLoadSubtitles()
        }
    }

    private fun onEpisodeHint(episode: Int) {
        if (episode == pageEpisodeHint) {
            return
        }
        pageEpisodeHint = episode
        tryAutoLoadSubtitles()
    }

    private fun tryAutoLoadSubtitles() {
        val url = webView.url ?: return
        val title = webView.title ?: ""
        if (!Addresses.isWebPage(url) || Addresses.isGoogleSearch(url)) {
            return
        }
        val episode = currentEpisode() ?: return
        val pageKey = PageTitles.animeName(title).lowercase() + "#" + episode
        if (pageKey == lastAutoLoadPageKey) {
            return
        }
        lastAutoLoadPageKey = pageKey
        autoLoadJob?.cancel()
        setSubtitleStatus(SubtitleState.SEARCHING, "Searching ep $episode", "Looking for Japanese subtitles, episode $episode…")
        val report = SearchReport()
        lastSearchReport = report
        autoLoadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { subtitleFinder.findForPage(title, url, namesShownOnPage, episode, report) } }
            result.onFailure {
                report.add("Error", it.message ?: "unknown")
                retryAfterConnectionProblem(it.message)
            }
            result.onSuccess { outcome -> useFinderOutcome(outcome) }
        }
    }

    private fun useFinderOutcome(outcome: SubtitleFinder.Outcome) {
        when (outcome) {
            is SubtitleFinder.Outcome.Found -> loadMatch(outcome.match)
            is SubtitleFinder.Outcome.NotFound -> {
                subtitleSession.clear()
                setSubtitleStatus(SubtitleState.MISSING, "No subs · tap for details", "${outcome.shortReason}. Tap Subs to choose, or ↻ to search again.")
            }
        }
    }

    private fun askForJimakuApiKey() {
        val field = EditText(this).apply {
            setText(store.jimakuApiKey())
            hint = "Paste your Jimaku API key"
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Jimaku API key")
            .setMessage(
                "Jimaku is a second place to find Japanese subtitles. Sign in at jimaku.cc with your personal account, " +
                    "open your account page, create an API key and paste it here. Leave it empty to turn Jimaku off.",
            )
            .setView(field)
            .setPositiveButton("Save") { _, _ ->
                store.saveJimakuApiKey(field.text.toString())
                Toast.makeText(this, if (store.jimakuApiKey().isBlank()) "Jimaku off" else "Jimaku on", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearchDetails() {
        SearchReportDialog(this).show(lastSearchReport, lastStatusDetail)
    }

    private fun retryAfterConnectionProblem(reason: String?) {
        setSubtitleStatus(SubtitleState.PROBLEM, "Offline · retrying", "Couldn't reach the subtitle list (${reason ?: "no connection"}). Trying again in 15 s, or tap ↻.")
        lastAutoLoadPageKey = null
        ticker.postDelayed({ tryAutoLoadSubtitles() }, CONNECTION_RETRY_DELAY_MS)
    }

    private fun openSubtitlePicker() {
        val title = webView.title ?: ""
        val animeName = PageTitles.animeName(title)
        val episode = currentEpisode()
        lifecycleScope.launch {
            val romajiName = withContext(Dispatchers.IO) {
                runCatching { AniListClient.titlesFor(animeName).firstOrNull() }.getOrNull()
            }
            subtitlePicker.show(romajiName ?: animeName, episode)
        }
    }

    private fun loadMatch(match: SubtitleMatch) {
        lifecycleScope.launch {
            setSubtitleStatus(SubtitleState.SEARCHING, "Downloading", "Downloading ${match.fileName}…")
            val result = withContext(Dispatchers.IO) { runCatching { SubtitleParser.parse(match.fileName, match.download()) } }
            result.onFailure { setSubtitleStatus(SubtitleState.PROBLEM, "Download failed", "Download failed: ${it.message}") }
            result.onSuccess { cues ->
                val seriesKey = seriesKeyFor(match.folderKeyToRemember ?: match.seriesName)
                useSubtitleCues(cues, match.label, match.fileName, seriesKey)
                rememberFolderChoice(match)
                downloadNextEpisodeInBackground(match)
            }
        }
    }

    private fun rememberFolderChoice(match: SubtitleMatch) {
        val folderKey = match.folderKeyToRemember ?: return
        val animeName = currentAnimeName()
        if (animeName.isNotBlank()) {
            store.rememberSubtitleFolderForAnime(animeName, folderKey)
        }
    }

    private fun downloadNextEpisodeInBackground(match: SubtitleMatch) {
        val downloadNext = match.downloadNextEpisode ?: return
        lifecycleScope.launch(Dispatchers.IO) { runCatching { downloadNext() } }
    }

    private fun chooseSubtitleFileOnDevice() {
        openSubtitleDocument.launch(arrayOf("*/*"))
    }

    private fun loadSubtitleFromDevice(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = displayName(uri)
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    fileName to SubtitleParser.parse(fileName, bytes)
                }
            }
            result.onFailure { setSubtitleStatus(SubtitleState.PROBLEM, "Can't read file", "Could not read that file: ${it.message}") }
            result.onSuccess { (fileName, cues) -> useSubtitleCues(cues, fileName, uri.toString(), seriesKeyFor(currentAnimeName())) }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return uri.lastPathSegment ?: "subtitle.srt"
    }

    private fun seriesKeyFor(seriesName: String): String {
        val domain = Addresses.siteDomain(webView.url) ?: "unknown-site"
        return "$domain|${seriesName.lowercase()}"
    }

    private fun useSubtitleCues(cues: List<SubtitleCue>, label: String, key: String, seriesKey: String) {
        if (cues.isEmpty()) {
            setSubtitleStatus(SubtitleState.PROBLEM, "Empty file", "No readable subtitle lines in $label")
            return
        }
        subtitleSession.load(SubtitleTrack(label, cues), key, seriesKey, store.subtitleOffsetMs(seriesKey))
        val episodeText = currentEpisode()?.let { "Ep $it" } ?: "Ready"
        setSubtitleStatus(SubtitleState.READY, episodeText, "Subtitles: $label (${cues.size} lines)")
        showFriendlyAddress()
    }

    private fun setSubtitleStatus(state: SubtitleState, shortText: String, detail: String) {
        lastStatusDetail = detail
        subtitleControls.setStatus(state, shortText)
        subtitleControls.setOffsetText(if (subtitleSession.track != null) subtitleSession.shortOffsetText() else "—")
    }

    private fun syncToLineJustHeard() {
        if (subtitleSession.track == null) {
            Toast.makeText(this, "No subtitles loaded yet. Tap ↻ or Subtitles first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!playbackClock.hasVideoWithin(SYNC_VIDEO_WINDOW_MS)) {
            Toast.makeText(this, "The page hasn't sent the video time yet. Press play once, then Sync now.", Toast.LENGTH_SHORT).show()
            return
        }
        val synced = subtitleSession.syncNearestLineTo(playbackClock.positionMs())
        if (!synced) {
            Toast.makeText(this, "No subtitle line within 15 s. Use −/+ first.", Toast.LENGTH_SHORT).show()
            return
        }
        rememberOffset()
        Toast.makeText(this, "Synced: ${subtitleSession.offsetText()}", Toast.LENGTH_SHORT).show()
    }

    private fun rememberOffset() {
        subtitleControls.showOffsetChanged(subtitleSession.shortOffsetText())
        subtitleSession.seriesKey?.let { store.saveSubtitleOffsetMs(it, subtitleSession.offsetMs) }
    }

    private fun shiftSubtitles(deltaMs: Long) {
        subtitleSession.shiftBy(deltaMs)
        rememberOffset()
    }

    private fun updateSubtitleOverlay() {
        if (isPreviewingOverlayStyle) {
            subtitleOverlay.show(OVERLAY_PREVIEW_TEXT)
            return
        }
        val canShow = subtitleSession.overlayVisible && subtitleSession.track != null && playbackClock.hasVideo()
        if (!canShow) {
            subtitleOverlay.hide()
            return
        }
        subtitleOverlay.setLifted(playbackClock.isPaused())
        subtitleOverlay.show(subtitleSession.textAt(playbackClock.positionMs()))
    }

    private fun saveContinueWatchingIfDue() {
        if (!playbackClock.isPlaying()) {
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastContinueSaveMs < CONTINUE_SAVE_INTERVAL_MS) {
            return
        }
        lastContinueSaveMs = nowMs
        val url = webView.url ?: return
        val watching = ContinueWatching(
            url = url,
            title = webView.title ?: url,
            seconds = playbackClock.positionMs() / 1000.0,
            subtitleLabel = subtitleSession.track?.label ?: "",
        )
        store.saveContinueWatching(watching)
        recordInAnimeList(url, watching.seconds)
    }

    private fun recordInAnimeList(url: String, seconds: Double) {
        val pageTitle = webView.title ?: ""
        val animeName = PageTitles.cleanAnimeName(pageTitle)
        val domain = Addresses.siteDomain(url)
        if (animeName.isBlank() || domain == null) {
            return
        }
        val watched = WatchedOnSite(
            domain = domain,
            url = url,
            episode = currentEpisode(),
            seconds = seconds,
            watchedAtMs = System.currentTimeMillis(),
        )
        val entry = store.recordWatching(animeName, PageTitles.searchNames(pageTitle, namesShownOnPage), watched)
        if (!entry.isLookedUp) {
            lifecycleScope.launch { animeListLookup.lookUpUnnamedAnime() }
        }
    }

    override fun refreshHomePage() {
        if (webView.url?.startsWith(Addresses.HOME_URL) == true) {
            webView.reload()
        }
    }

    private fun showOverlayStyleDialog() {
        isPreviewingOverlayStyle = true
        val dialog = OverlayStyleDialog(this, store.overlayStyle()) { style ->
            store.saveOverlayStyle(style)
            subtitleOverlay.applyStyle(style)
        }
        dialog.show { isPreviewingOverlayStyle = false }
    }

    private fun refreshSubtitleCatalog() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Updating subtitle catalog…", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) { runCatching { kitsunekko.animeFolders(forceRefresh = true).size } }
            val message = result.fold({ "Subtitle catalog: $it anime" }, { "Update failed: ${it.message}" })
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCocoonArtwork() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ArtworkExporter(this@MainActivity).saveToDownloads() } }
            result.onFailure { Toast.makeText(this@MainActivity, "Could not save artwork: ${it.message}", Toast.LENGTH_LONG).show() }
            result.onSuccess { count ->
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Artwork saved")
                    .setMessage(
                        "$count images are in Download/${ArtworkExporter.DOWNLOAD_FOLDER}.\n\n" +
                            "In Cocoon: highlight Aynime, press Y > Edit > artwork, then pick:\n" +
                            "• Icon: aynime_icon_512.png\n" +
                            "• Hero: aynime_hero_1920x620.png\n" +
                            "• Logo: aynime_logo.png\n" +
                            "• Cover / grid: aynime_cover_600x900.png",
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun updateAdBlockList() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Downloading ad block list…", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) { runCatching { adBlocker.updateFromInternet() } }
            val message = result.fold({ "Ad block list: $it sites blocked" }, { "Update failed: ${it.message}" })
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (handleLeftStick(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun handleLeftStick(event: MotionEvent): Boolean {
        val isControllerMove = event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE
        if (!isControllerMove) {
            return false
        }
        val dpadWasUsed = handleDpadSentAsMotion(event)
        val stickWasUsed = playbackClock.hasVideo() && seekWithStick(event)
        return dpadWasUsed || stickWasUsed
    }

    private fun handleDpadSentAsMotion(event: MotionEvent): Boolean {
        val pressedKey = dpadKeyFromHat(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        if (pressedKey == heldDpadKey) {
            return heldDpadKeyWasHandled
        }
        val releasedKeyWasHandled = heldDpadKeyWasHandled
        heldDpadKey = pressedKey
        heldDpadKeyWasHandled = pressedKey != null && handleControllerButton(pressedKey)
        val isRelease = pressedKey == null
        return heldDpadKeyWasHandled || (isRelease && releasedKeyWasHandled)
    }

    private fun dpadKeyFromHat(hatX: Float, hatY: Float): Int? = when {
        hatX < -HAT_PRESSED -> KeyEvent.KEYCODE_DPAD_LEFT
        hatX > HAT_PRESSED -> KeyEvent.KEYCODE_DPAD_RIGHT
        hatY < -HAT_PRESSED -> KeyEvent.KEYCODE_DPAD_UP
        hatY > HAT_PRESSED -> KeyEvent.KEYCODE_DPAD_DOWN
        else -> null
    }

    private fun seekWithStick(event: MotionEvent): Boolean {
        val horizontal = event.getAxisValue(MotionEvent.AXIS_X)
        val newDirection = when {
            horizontal > STICK_DEAD_ZONE -> 1
            horizontal < -STICK_DEAD_ZONE -> -1
            else -> 0
        }
        val directionChanged = newDirection != stickDirection
        if (directionChanged) {
            stopStickSeeking()
            stickDirection = newDirection
            if (newDirection != 0) {
                ticker.post(stickSeekRepeater)
            }
        }
        return directionChanged || newDirection != 0
    }

    private fun stopStickSeeking() {
        stickDirection = 0
        ticker.removeCallbacks(stickSeekRepeater)
    }

    private fun seekVideoBy(deltaSeconds: Double) {
        val targetSeconds = (playbackClock.positionMs() / 1000.0 + deltaSeconds).coerceAtLeast(0.0)
        playbackClock.jumpTo(targetSeconds)
        updateSubtitleOverlay()
        videoBridge.seekTo(targetSeconds)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isFocusTap(event)) {
            isSwallowingTouch = true
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isSwallowingTouch = touchOnlyRevealsHiddenControls(event)
            subtitleControls.noteInteraction()
        }
        if (isSwallowingTouch) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun touchOnlyRevealsHiddenControls(event: MotionEvent): Boolean {
        val isFullscreen = fullscreenCallback != null
        if (!isFullscreen && topBar.isHidden && isInTopRevealStrip(event.rawY)) {
            topBar.revealBriefly()
            return true
        }
        return subtitleControls.isHiddenAndContains(event.rawX, event.rawY)
    }

    private fun isInTopRevealStrip(screenY: Float): Boolean {
        val mainColumn = findViewById<View>(R.id.main_column)
        val location = IntArray(2)
        mainColumn.getLocationOnScreen(location)
        val stripBottom = location[1] + mainColumn.paddingTop + TOP_REVEAL_STRIP_DP * resources.displayMetrics.density
        return screenY <= stripBottom
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isFirstPress = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        val serviceWatchesPresses = ControllerKeyRouting.runningService != null
        if (isFirstPress && !serviceWatchesPresses) {
            pauseIfCaptureButton(event.keyCode)
        }
        if (isFirstPress && !addressBar.hasFocus() && handleControllerButton(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleControllerButton(keyCode: Int): Boolean {
        val action = store.actionForButton(keyCode) ?: return false
        subtitleControls.noteInteraction()
        val hasSubtitles = subtitleSession.track != null
        if (!playbackClock.hasVideo() || (action.needsSubtitles && !hasSubtitles)) {
            return false
        }
        val currentCue = subtitleSession.currentCueIndex(playbackClock.positionMs())
        when (action) {
            ControllerAction.PREVIOUS_LINE -> jumpToCue(currentCue - 1)
            ControllerAction.NEXT_LINE -> jumpToCue(currentCue + 1)
            ControllerAction.REPLAY_LINE -> jumpToCue(currentCue)
            ControllerAction.TOGGLE_SUBTITLE -> toggleOverlayVisibility()
            ControllerAction.PLAY_PAUSE -> videoBridge.togglePlay()
            ControllerAction.SEEK_BACK -> seekVideoBy(-SEEK_STEP_SECONDS)
            ControllerAction.SEEK_FORWARD -> seekVideoBy(SEEK_STEP_SECONDS)
            ControllerAction.SUBTITLES_EARLIER -> shiftSubtitles(-OFFSET_STEP_MS)
            ControllerAction.SUBTITLES_LATER -> shiftSubtitles(OFFSET_STEP_MS)
            ControllerAction.SYNC_TO_LINE -> syncToLineJustHeard()
        }
        return true
    }

    private fun jumpToCue(cueIndex: Int) {
        val seconds = subtitleSession.videoSecondsForCue(cueIndex) ?: return
        playbackClock.jumpTo(seconds)
        updateSubtitleOverlay()
        videoBridge.seekTo(seconds)
    }

    private fun toggleOverlayVisibility() {
        subtitleSession.overlayVisible = !subtitleSession.overlayVisible
        val message = if (subtitleSession.overlayVisible) "Subtitle shown" else "Subtitle hidden"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TICK_MS = 100L
        const val OFFSET_STEP_MS = 100L
        const val TOP_REVEAL_STRIP_DP = 56
        const val CONTINUE_SAVE_INTERVAL_MS = 5_000L
        const val MOVED_SITE_WINDOW_MS = 60_000L
        const val EPISODE_CHECK_EVERY_TICKS = 30
        const val CONNECTION_RETRY_DELAY_MS = 15_000L
        const val SYNC_VIDEO_WINDOW_MS = 30_000L
        const val STICK_DEAD_ZONE = 0.5f
        const val HAT_PRESSED = 0.5f
        const val SEEK_STEP_SECONDS = 5.0
        const val RETURN_FOCUS_DELAY_MS = 800L
        const val CAPTURE_PAUSE_DELAY_MS = 100L
        const val FOCUS_TAP_WINDOW_MS = 1_000L
        const val STICK_REPEAT_MS = 350L
        const val OVERLAY_PREVIEW_TEXT = "魔法を集めるのが好きなんだ。"
    }
}

data class MenuEntry(val title: String, val iconResource: Int, val action: () -> Unit)
