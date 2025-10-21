package ru.aloyenz.t501.driver;

public class VPen {

    static {
        System.loadLibrary("vpen");
    }

    public static native long initialize(String penName);

    public static native int writePosition(long descriptor, int x, int y, int pressure, boolean touch, int tiltX, int tiltY);

    public static native void shutdown(long handle);
}
