package ru.aloyenz.t501.driver.virtual;

public class VKeyboard {

    static {
        System.loadLibrary("vkeyboard");
    }

    public static native long initialize(String keyboardName, int[] keycodes);

    public static native int keyboardKeyEvent(long descriptor, int[] keyCodes, boolean[] pressed);

    public static native void shutdown(long handle);
}
