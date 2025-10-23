package ru.aloyenz.t501.driver.virtual;

// You can now control your maus by your mouse!
public class VMouse {

    static {
        System.loadLibrary("vmouse");
    }

    public static native long initialize(String mouseName);

    public static native int mouseEvent(long descriptor,
                                        int[] codes, boolean[] isRel, int[] values);

    public static native void shutdown(long handle);
}
