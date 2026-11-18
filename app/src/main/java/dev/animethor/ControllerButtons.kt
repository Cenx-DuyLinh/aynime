package dev.animethor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

enum class ControllerAction(val title: String, val defaultKeyCode: Int, val needsSubtitles: Boolean) {
    PREVIOUS_LINE("Previous line", KeyEvent.KEYCODE_BUTTON_L1, needsSubtitles = true),
    NEXT_LINE("Next line", KeyEvent.KEYCODE_BUTTON_R1, needsSubtitles = true),
    REPLAY_LINE("Replay current line", KeyEvent.KEYCODE_BUTTON_L2, needsSubtitles = true),
    TOGGLE_SUBTITLE("Hide / show subtitle box", KeyEvent.KEYCODE_BUTTON_R2, needsSubtitles = true),
    PLAY_PAUSE("Play / pause", NO_BUTTON_VALUE, needsSubtitles = false),
    SEEK_BACK("Back 5 s", NO_BUTTON_VALUE, needsSubtitles = false),
    SEEK_FORWARD("Forward 5 s", NO_BUTTON_VALUE, needsSubtitles = false),
    SUBTITLES_EARLIER("Subtitles 0.1 s earlier", NO_BUTTON_VALUE, needsSubtitles = true),
    SUBTITLES_LATER("Subtitles 0.1 s later", NO_BUTTON_VALUE, needsSubtitles = true),
    SYNC_TO_LINE("Sync to the line just heard", NO_BUTTON_VALUE, needsSubtitles = true);

    companion object {
        const val NO_BUTTON = NO_BUTTON_VALUE

        fun buttonName(keyCode: Int): String {
            if (keyCode == NO_BUTTON) {
                return "not set"
            }
            return KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_BUTTON_")
                .removePrefix("KEYCODE_")
                .replace('_', ' ')
        }
    }
}

private const val NO_BUTTON_VALUE = 0

class ControllerButtonsDialog(private val context: Context, private val store: AppStore) {

    fun show() {
        val actions = ControllerAction.entries
        val actionLabels = actions.map { "${it.title}:  ${ControllerAction.buttonName(store.controllerButton(it))}" }
        val labels = actionLabels + focusFixLabel() + returnFocusLabel() + captureButtonLabel()
        val focusFixPosition = actions.size
        val returnFocusPosition = actions.size + 1
        MaterialAlertDialogBuilder(context)
            .setTitle("Controller buttons")
            .setItems(labels.toTypedArray()) { _, position ->
                when {
                    position < actions.size -> askForButton(actions[position])
                    position == focusFixPosition -> explainFocusFix()
                    position == returnFocusPosition -> toggleReturnFocus()
                    else -> askForCaptureButton()
                }
            }
            .setPositiveButton("Done", null)
            .setNeutralButton("Defaults") { _, _ -> restoreDefaults() }
            .show()
    }

    private fun focusFixLabel(): String {
        val state = if (ControllerKeyRouting.isServiceEnabled(context)) "on" else "off"
        return "Work while PlayTranslate has focus:  $state"
    }

    private fun returnFocusLabel(): String {
        val state = if (store.returnFocusAfterOtherApps()) "on" else "off"
        return "Return the focus to Aynime after PlayTranslate:  $state"
    }

    private fun toggleReturnFocus() {
        val turningOn = !store.returnFocusAfterOtherApps()
        store.saveReturnFocusAfterOtherApps(turningOn)
        val message = if (turningOn) {
            "On: while a video is open, a moment after you use PlayTranslate Aynime makes one invisible tap on its own " +
                "screen (the tap does nothing in the page), so the stick, D-pad and all buttons go to Aynime again. " +
                "Taps on the bottom screen still work.\n\n" +
                "Needs \"Work while PlayTranslate has focus\" to be on. Turn this off if you need the controller in PlayTranslate."
        } else {
            "Off: the focus stays with whichever app you used last."
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Return the focus to Aynime")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> show() }
            .show()
    }

    private fun captureButtonLabel(): String {
        val keyCode = store.captureButton()
        val state = if (keyCode == ControllerAction.NO_BUTTON) "off" else ControllerAction.buttonName(keyCode)
        return "Pause the video when PlayTranslate captures:  $state"
    }

    private fun askForCaptureButton() {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("PlayTranslate capture button")
            .setMessage(
                "Press the button you use for PlayTranslate's capture (usually X). When you press it while a video plays, " +
                    "Aynime pauses the video. The button still goes to PlayTranslate as before.\n\nBack cancels.",
            )
            .setNegativeButton("Cancel") { _, _ -> show() }
            .setNeutralButton("Turn off") { _, _ ->
                store.saveCaptureButton(ControllerAction.NO_BUTTON)
                show()
            }
            .create()
        dialog.setOnKeyListener { _, keyCode, event ->
            val isUsableButton = isAssignable(keyCode)
            if (isUsableButton && event.action == KeyEvent.ACTION_UP) {
                store.saveCaptureButton(keyCode)
                dialog.dismiss()
                show()
            }
            isUsableButton
        }
        dialog.show()
    }

    private fun explainFocusFix() {
        MaterialAlertDialogBuilder(context)
            .setTitle("Buttons while PlayTranslate has focus")
            .setMessage(
                "When PlayTranslate takes the screen, it also takes the controller focus, so your buttons go to it.\n\n" +
                    "Turn on \"Aynime controller buttons\" in Accessibility. Then, while Aynime is open, only the buttons " +
                    "assigned here go to Aynime; every other button (like X) still goes to PlayTranslate. " +
                    "It reads nothing on the screen.\n\n" +
                    "If the switch is greyed out: Settings > Apps > Aynime > ⋮ > Allow restricted settings, then try again.",
            )
            .setPositiveButton("Open Accessibility") { _, _ ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .setNegativeButton("Back") { _, _ -> show() }
            .show()
    }

    private fun askForButton(action: ControllerAction) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(action.title)
            .setMessage("Press the controller button or a D-pad direction to use.\n\nTip: X is left free for PlayTranslate. Back cancels.")
            .setNegativeButton("Cancel") { _, _ -> show() }
            .setNeutralButton("Clear") { _, _ ->
                store.setControllerButton(action, ControllerAction.NO_BUTTON)
                show()
            }
            .create()
        dialog.setOnKeyListener { _, keyCode, event ->
            val isUsableButton = isAssignable(keyCode)
            if (isUsableButton && event.action == KeyEvent.ACTION_UP) {
                store.setControllerButton(action, keyCode)
                dialog.dismiss()
                show()
            }
            isUsableButton
        }
        dialog.show()
    }

    private fun isAssignable(keyCode: Int): Boolean = KeyEvent.isGamepadButton(keyCode) || keyCode in DIRECTION_BUTTONS

    private companion object {
        val DIRECTION_BUTTONS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }

    private fun restoreDefaults() {
        ControllerAction.entries.forEach { store.setControllerButton(it, it.defaultKeyCode) }
        show()
    }
}
