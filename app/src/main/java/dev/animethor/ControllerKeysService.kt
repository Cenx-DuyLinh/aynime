package dev.animethor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.graphics.Path
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

object ControllerKeyRouting {
    var handlerWhileAynimeIsOpen: ((Int) -> Boolean)? = null
    var stickHandlerWhileAynimeIsOpen: ((MotionEvent) -> Boolean)? = null
    var playTranslateUsedWhileAynimeIsOpen: ((String) -> Unit)? = null
    var pressWatcherWhileAynimeIsOpen: ((Int) -> Unit)? = null
    var runningService: ControllerKeysService? = null
    private var isCatchingStick = false

    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val ourService = ComponentName(context, ControllerKeysService::class.java).flattenToString()
        return enabledServices.split(':').any { it.equals(ourService, ignoreCase = true) }
    }

    fun startCatchingStick() {
        isCatchingStick = true
        runningService?.catchStick(true)
    }

    fun stopCatchingStick() {
        isCatchingStick = false
        runningService?.catchStick(false)
    }

    fun shouldCatchStick(): Boolean = isCatchingStick

    fun tapScreen(displayId: Int, x: Float, y: Float, onResult: (Boolean) -> Unit): Boolean {
        val service = runningService ?: return false
        return service.tap(displayId, x, y, onResult)
    }
}

class ControllerKeysService : AccessibilityService() {
    private val keysTakenOnPress = mutableSetOf<Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ControllerKeyRouting.runningService = this
        catchStick(ControllerKeyRouting.shouldCatchStick())
    }

    override fun onDestroy() {
        if (ControllerKeyRouting.runningService == this) {
            ControllerKeyRouting.runningService = null
        }
        super.onDestroy()
    }

    fun catchStick(shouldCatch: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        val settings = serviceInfo ?: return
        settings.motionEventSources = if (shouldCatch) InputDevice.SOURCE_JOYSTICK else 0
        serviceInfo = settings
    }

    fun tap(displayId: Int, x: Float, y: Float, onResult: (Boolean) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val touchPoint = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(touchPoint, 0, TAP_DURATION_MS))
            .setDisplayId(displayId)
            .build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onResult(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onResult(false)
        }
        return dispatchGesture(gesture, callback, null)
    }

    override fun onMotionEvent(event: MotionEvent) {
        ControllerKeyRouting.stickHandlerWhileAynimeIsOpen?.invoke(event)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isFirstPress = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (isFirstPress) {
            ControllerKeyRouting.pressWatcherWhileAynimeIsOpen?.invoke(event.keyCode)
        }
        val handler = ControllerKeyRouting.handlerWhileAynimeIsOpen ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> takePress(event, handler)
            KeyEvent.ACTION_UP -> keysTakenOnPress.remove(event.keyCode)
            else -> false
        }
    }

    private fun takePress(event: KeyEvent, handler: (Int) -> Boolean): Boolean {
        if (event.repeatCount > 0) {
            return event.keyCode in keysTakenOnPress
        }
        val handled = handler(event.keyCode)
        if (handled) {
            keysTakenOnPress.add(event.keyCode)
        }
        return handled
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val isPlayTranslate = packageName.startsWith(PLAYTRANSLATE_PACKAGE)
        val isWindowOrClick = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        if (isPlayTranslate && isWindowOrClick) {
            ControllerKeyRouting.playTranslateUsedWhileAynimeIsOpen?.invoke(packageName)
        }
    }

    private companion object {
        const val PLAYTRANSLATE_PACKAGE = "com.playtranslate"
        const val TAP_DURATION_MS = 40L
    }

    override fun onInterrupt() = Unit
}
