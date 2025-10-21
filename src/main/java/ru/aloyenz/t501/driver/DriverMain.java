package ru.aloyenz.t501.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.*;
import ru.aloyenz.t501.driver.config.Configuration;

import java.io.File;
import java.io.IOException;

public class DriverMain {

    private static final Logger logger = LoggerFactory.getLogger(DriverMain.class);

    public static final String DRIVER_VERSION = "1.0.0";
    public static final String HID = "08f2";
    public static final String VID = "6811";

//    public static final String HID = "046d";
//    public static final String VID = "c52f";

    private static Thread hotplugThread;
    private static Thread readThread;

    private static final T501DevicesHandler HANDLER = new T501DevicesHandler();
    private static final DeviceReader READER = new DeviceReader(HANDLER);

    private static long virtualPenHandle = -1;

    public static long getVirtualPenHandle() {
        return virtualPenHandle;
    }

    public static void main(String[] args) {

        // Reading configuration
        String configPath = "t501_driver_config.json";

        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("--config")) {
                if (i + 1 < args.length) {
                    configPath = args[i + 1];
                } else {
                    logger.error("No configuration file path provided after --config");
                    return;
                }
            }
        }

        File configFile = new File(configPath);
        try {
            // Initializing configuration. We didn't need to check path traversal!
            Configuration.init(configFile);
        } catch (IOException e) {
            logger.error("Failed to read or create configuration file: {}", e.getMessage(), e);
            return;
        }

        if (!Configuration.getInstance().driverVersion.equals(DRIVER_VERSION)) {
            logger.warn("Driver version mismatch! Config version: {}, Actual version: {}",
                    Configuration.getInstance().driverVersion, DRIVER_VERSION);
            // Updating logic here for future versions...
            Configuration.getInstance().driverVersion = DRIVER_VERSION;
            try {
                Configuration.save(configFile);
            } catch (IOException e) {
                logger.error("Failed to save updated configuration file: {}", e.getMessage(), e);
                return;
            }
        }

        // Initializing virtual pen
        logger.info("Initializing virtual pen...");
        virtualPenHandle = VPen.initialize(Configuration.getInstance().penName);
        if (virtualPenHandle <= -1) {
            logger.error("Failed to initialize virtual pen. Error code: {}", virtualPenHandle);
            return;
        }

        int code = LibUsb.init(null);
        if (code != LibUsb.SUCCESS) {
            throw new RuntimeException("Unable to initialize libusb: " + LibUsb.strError(code));
        }

        // Initializing virtual pen


        findDevice();
        if (HANDLER.isEmpty()) {
            logger.warn("No T501 devices found.");
        } else {
            logger.info("Total T501 devices found: {}", HANDLER.size());
        }

        logger.info("Registering hotplug...");
        HotplugCallback callback = (Context context, Device device, int event,
                                    Object userData) -> {

            DeviceDescriptor descriptor = new DeviceDescriptor();
            int result = LibUsb.getDeviceDescriptor(device, descriptor);
            if (result != LibUsb.SUCCESS) {
                logger.error("Failed to get device descriptor: {}", LibUsb.strError(result));
                return 0;
            }

            String vid = String.format("%04x", descriptor.idProduct() & 0xffff);
            String hid = String.format("%04x", descriptor.idVendor() & 0xffff);

            if (vid.equals(VID) && hid.equals(HID)) {
                if (event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED) {
                    logger.info("T501 device connected via hotplug: VID={} PID={}", vid, hid);
                    HANDLER.onDeviceConnected(device, descriptor);
                } else if (event == LibUsb.HOTPLUG_EVENT_DEVICE_LEFT) {
                    logger.info("T501 device disconnected via hotplug: VID={} PID={}", vid, hid);
                    HANDLER.onDeviceDisconnected(descriptor);
                }
            }

            return 0; // Do not unregister!
        };

        LibUsb.hotplugRegisterCallback(null,
                LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                LibUsb.HOTPLUG_ENUMERATE,
                LibUsb.HOTPLUG_MATCH_ANY,
                LibUsb.HOTPLUG_MATCH_ANY,
                LibUsb.HOTPLUG_MATCH_ANY,
                callback,
                null,
                null);

        hotplugThread = new Thread(() -> {
            while (true) {
                int c = LibUsb.handleEventsTimeoutCompleted(null, 500000, null);
                if (c != LibUsb.SUCCESS) {
                    logger.error("Error handling USB events: {}", LibUsb.strError(c));
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Hotplug thread interrupted, exiting.");
                    break;
                }
            }
        });
        hotplugThread.start();

        readThread = new Thread(() -> {
            while (true) {
                READER.read();

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    logger.error("Read thread interrupted, exiting.");
                    break;
                }
            }
        });
        readThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(DriverMain::stop));
    }

    private static void findDevice() {
        logger.info("Finding T501 device...");

        DeviceList deviceList = new DeviceList();

        int code = LibUsb.getDeviceList(null, deviceList);

        try {
            if (code < 0) {
                throw new RuntimeException("Unable to get device list: " + LibUsb.strError(code));
            }

            for (Device device : deviceList) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                code = LibUsb.getDeviceDescriptor(device, descriptor);

                if (code != LibUsb.SUCCESS) {
                    throw new RuntimeException("Unable to read device descriptor: " + LibUsb.strError(code));
                }

                String vid = String.format("%04x", descriptor.idProduct() & 0xffff);
                String hid = String.format("%04x", descriptor.idVendor() & 0xffff);

                if (vid.equals(VID) && hid.equals(HID)) {
                    logger.info("T501 device found: VID={} PID={}", vid, hid);

                    HANDLER.onDeviceConnected(device, descriptor);
                }
            }
        } finally {
            LibUsb.freeDeviceList(deviceList, true);
        }
    }

    public static void stop() {
        logger.info("Stopping all devices...");

        // Stopping thread
        hotplugThread.interrupt();
        try {
            hotplugThread.join();
        } catch (InterruptedException ignored) {}

        readThread.interrupt();
        try {
            readThread.join();
        } catch (InterruptedException ignored) {}

        // Reattaching kernel drivers for alive devices
        HANDLER.reattachKernelDrivers();
    }
}
