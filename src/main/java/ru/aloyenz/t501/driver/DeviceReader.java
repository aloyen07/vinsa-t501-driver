package ru.aloyenz.t501.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class DeviceReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceReader.class);

    private final T501DevicesHandler devicesHandler;

    private final List<Long> disconnectedHandles = new ArrayList<>();

    public DeviceReader(T501DevicesHandler devicesHandler) {
        this.devicesHandler = devicesHandler;
    }

    public void read() {
        devicesHandler.deviceHandlers().forEach(device -> {
            DeviceHandle handle = device.first();
            DeviceInformation info = device.second();

            // Reading...
            ByteBuffer buffer = ByteBuffer.allocateDirect(info.packetSize());
            IntBuffer transferred = IntBuffer.allocate(1);
            int result = LibUsb.interruptTransfer(handle, info.entryPoint(), buffer, transferred, 1000);
            if (result == LibUsb.SUCCESS) {
                byte[] data = new byte[transferred.get(0)];
                buffer.get(data, 0, transferred.get(0));
                // Process data
                applyInput(buffer);
            } else {
                if (result != LibUsb.ERROR_TIMEOUT && result != LibUsb.ERROR_NO_DEVICE) {
                    LOGGER.error("Error interruptTransfer: {}, ID: {}", LibUsb.strError(result), result);
                }

                long pointer = handle.getPointer();
                if (result == LibUsb.ERROR_NO_DEVICE) {

                    if (!disconnectedHandles.contains(pointer)) {
                        LOGGER.info("Device {} has been disconnected", handle);
                        disconnectedHandles.add(pointer);
                    }
                } else {

                    disconnectedHandles.remove(pointer);
                }
            }
        });
    }

    private void applyInput(ByteBuffer buffer) {
        //05 C0 B3 0C F8 08 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        // Первый байт - 05, если событие - стилус. 02 если клавиатура.
        // Второй - тип события. C0 - ведение, C1 - нажатие.
        // Далее, идет два байта: Это Х координата. Она варьируется от 00 00 до FF 0F
        // Далее ещё два байта: Это Y. Варьируется точно так же.
        // Ещё два байта - сила нажатия (если у нас стилус прикоснулся к поверхности).

        byte[] data = new byte[buffer.limit()];
        buffer.get(data);

        if (data[0] == 0x05) {
            // Stylus input
            boolean touch = (data[1] & 0x01) != 0;
            int x = data[4] & 0x0F << 8 | data[3] & 0xFF;
            int y = data[6] & 0x0F << 8 | data[5] & 0xFF;

            int pressure = data[8] & 0xFF << 8 | data[7] & 0xFF;
            if (!touch) {
                pressure = 0;
            }

            // TODO: Apply tiltX and tiltY if available
            int tiltX = 0;
            int tiltY = 0;

            VPen.writePosition(DriverMain.getVirtualPenHandle(), x, y, pressure, touch, tiltX, tiltY);

        } else {
            // Keyboard input
        }
    }
}
