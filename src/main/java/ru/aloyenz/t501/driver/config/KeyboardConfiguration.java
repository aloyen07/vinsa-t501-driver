package ru.aloyenz.t501.driver.config;

import com.google.gson.annotations.SerializedName;

public class KeyboardConfiguration {

    @SerializedName("Ctrl+")
    public KeyBinding[] ctrlPlus = new KeyBinding[] {
            new KeyBinding("KEY_LEFTCTRL"),
            new KeyBinding("KEY_EQUAL")
    };

    @SerializedName("Ctrl-")
    public KeyBinding[] ctrlMinus = new KeyBinding[] {
            new KeyBinding("KEY_LEFTCTRL"),
            new KeyBinding("KEY_MINUS")
    };

    @SerializedName("[")
    public KeyBinding[] bracketOpen = new KeyBinding[] {
            new KeyBinding("KEY_LEFTBRACE")
    };

    @SerializedName("]")
    public KeyBinding[] bracketClose = new KeyBinding[] {
            new KeyBinding("KEY_RIGHTBRACE")
    };

    @SerializedName("Mouse Scroll Up")
    public KeyBinding[] mouseScrollUp = new KeyBinding[] {
            new KeyBinding("REL_WHEEL", 1)
    };

    @SerializedName("Mouse Scroll Down")
    public KeyBinding[] mouseScrollDown = new KeyBinding[] {
            new KeyBinding("REL_WHEEL", -1)
    };

    @SerializedName("Ctrl")
    public KeyBinding[] ctrl = new KeyBinding[] {
            new KeyBinding("KEY_LEFTCTRL")
    };

    @SerializedName("Alt")
    public KeyBinding[] alt = new KeyBinding[] {
            new KeyBinding("KEY_LEFTALT")
    };

    @SerializedName("Space")
    public KeyBinding[] space = new KeyBinding[] {
            new KeyBinding("KEY_SPACE")
    };

    @SerializedName("Tab")
    public KeyBinding[] tab = new KeyBinding[] {
            new KeyBinding("KEY_TAB")
    };

    @SerializedName("B")
    public KeyBinding[] b = new KeyBinding[] {
            new KeyBinding("KEY_B")
    };

    @SerializedName("E")
    public KeyBinding[] e = new KeyBinding[] {
            new KeyBinding("KEY_E")
    };
}
