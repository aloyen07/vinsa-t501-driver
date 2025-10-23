package ru.aloyenz.t501.driver.config.special;

import com.google.gson.annotations.SerializedName;
import ru.aloyenz.t501.driver.config.KeyBinding;
import ru.aloyenz.t501.driver.exception.InvalidSpecialConfigException;

import java.util.Arrays;
import java.util.List;

public class SpecialButtonsConfig {

    @SerializedName("button_mute")
    public SpecialAction buttonMute = SpecialAction.keyInput(
            "Button #1. Mute/Unmute device audio.",
            new KeyBinding[] { new KeyBinding("KEY_MUTE") }
    );

    @SerializedName("button_volume_down")
    public SpecialAction buttonVolumeDown = SpecialAction.keyInput(
            "Button #2. Volume Down.",
            new KeyBinding[] { new KeyBinding("KEY_VOLUMEDOWN") }
    );

    @SerializedName("button_volume_up")
    public SpecialAction buttonVolumeUp = SpecialAction.keyInput(
            "Button #3. Volume Up.",
            new KeyBinding[] { new KeyBinding("KEY_VOLUMEUP") }
    );

    @SerializedName("button_media_player")
    public SpecialAction buttonMediaPlayer = SpecialAction.keyInput(
            "Button #4. Open Media Player.",
            new KeyBinding[] { new KeyBinding("KEY_MEDIA") }
    );

    @SerializedName("button_start_stop_player")
    public SpecialAction buttonStartStopPlayer = SpecialAction.keyInput(
            "Button #5. Start/Stop Media Player.",
            new KeyBinding[] { new KeyBinding("KEY_PLAYPAUSE") }
    );

    @SerializedName("button_previous_track")
    public SpecialAction buttonPreviousTrack = SpecialAction.keyInput(
            "Button #6. Previous Track.",
            new KeyBinding[] { new KeyBinding("KEY_PREVIOUSSONG") }
    );

    @SerializedName("button_next_track")
    public SpecialAction buttonNextTrack = SpecialAction.keyInput(
            "Button #7. Next Track.",
            new KeyBinding[] { new KeyBinding("KEY_NEXTSONG") }
    );

    @SerializedName("button_home")
    public SpecialAction buttonHome = SpecialAction.keyInput(
            "Button #8. Home Button.",
            new KeyBinding[] { new KeyBinding("KEY_HOME") }
    );

    @SerializedName("button_calculator")
    public SpecialAction buttonCalculator = SpecialAction.keyInput(
            "Button #9. Open Calculator.",
            new KeyBinding[] { new KeyBinding("KEY_CALC") }
    );

    @SerializedName("button_screensoot")
    public SpecialAction buttonScreenshot = SpecialAction.keyInput(
            "Button #10. Take Screenshot.",
            new KeyBinding[] { new KeyBinding("KEY_SYSRQ") }
    );

    @SerializedName("example_bash_script_button")
    public SpecialAction exampleBashScriptButton = SpecialAction.bashScript(
            "Example Button. This button is not mapped to any physical button. It runs a bash script.",
            new String[][] {
                    { "echo", "Hello, World! I'm stdout of process" },
                    { "notify-send", "T501 driver", "Foxes is power!!!" }
            }
    );

    @SerializedName("example_no_action_button")
    public SpecialAction exampleNoActionButton = SpecialAction.noAction(
            "Example Button. This button is not mapped to any physical button and performs no action."
    );

    public List<SpecialAction> getAllActions() {
        return List.of(
                buttonMute,
                buttonVolumeDown,
                buttonVolumeUp,
                buttonMediaPlayer,
                buttonStartStopPlayer,
                buttonPreviousTrack,
                buttonNextTrack,
                buttonHome,
                buttonCalculator,
                buttonScreenshot
        );
    }

    public void validate() throws InvalidSpecialConfigException {
        int id = 1;
        for (SpecialAction specialAction : getAllActions()) {
            if (specialAction == null) {
                throw new InvalidSpecialConfigException("Special Action #" + id + " is null");
            }

            if (specialAction.type == null) {
                throw new InvalidSpecialConfigException("Special Action #" + id + " has null type");
            }

            if (specialAction.type == SpecialActionType.KEY_INPUT) {
                if (specialAction.keycodes == null) {
                    throw new InvalidSpecialConfigException("Special Action #" + id + " is of type KEY_INPUT but has null keycodes");
                }
            } else if (specialAction.type == SpecialActionType.BASH_SCRIPT) {
                if (specialAction.scripts == null) {
                    throw new InvalidSpecialConfigException("Special Action #" + id + " is of type BASH_SCRIPT but has null scripts");
                }
            }

            id++;
        }
    }
}
