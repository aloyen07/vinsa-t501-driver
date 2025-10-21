package ru.aloyenz.t501.driver;

import java.sql.Time;

public class VirtualPenInitializer {

    public static void main(String[] args) throws InterruptedException {
        long handle = VPen.initialize("Virtual T501 Pen");

        Thread.sleep(100);

        VPen.writePosition(handle, 0, 0, 500, true, 0, 0);
        System.out.println("Virtual T501 Pen initialized. Handle: " + handle);

        Thread.sleep(1000000);

        VPen.shutdown(handle);
    }
}
