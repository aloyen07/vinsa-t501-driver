package ru.aloyenz.t501.driver.virtual;

public class VPen {

    static {
        System.loadLibrary("vpen");
    }

    public static native long initialize(String penName);

    public static native int writePosition(long descriptor,
                                           int x, int y,
                                           int pressure, boolean touch,
                                           int tiltX, int tiltY,
                                           boolean stylusPlusPressed, boolean stylusMinusPressed);

    //public static native int buttonEvent(long descriptor, int buttonCode, boolean pressed);

    public static native int penLeave(long descriptor);

    public static native void shutdown(long handle);
}
