package ru.aloyenz.t501.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import ru.aloyenz.t501.driver.config.Configuration;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class DeviceReader {

    private static final int MAX_8BIT = (int) (Math.pow(2, 8) - 1);

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
                int length = transferred.get(0);
                byte[] data = new byte[length];
                buffer.get(data, 0, length);
                buffer.rewind();

                // Process data
                applyInput(buffer, length);
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

    private void applyInput(ByteBuffer buffer, int length) {

        byte[] data = new byte[length];
        buffer.get(data);

        StringBuilder hexString = new StringBuilder();
        for (byte b : data) {
            hexString.append(String.format("%02X ", b));
        }
        System.out.print(hexString.toString() + "\r");

        byte xLow = data[2];
        byte xHigh = data[1];
        int x = u16From2Bytes(xHigh, xLow);

        byte yLow = data[4];
        byte yHigh = data[3];
        int y = u16From2Bytes(yHigh, yLow);

        int rawPressure = u16From2Bytes(data[5], data[6]);

        int min = Configuration.getInstance().fullPressureValue;      // full press
        int max = Configuration.getInstance().hoverAfterRawPressure;  // hover

        // Нормализуем давление к диапазону 0-1024 (как в C коде)
        int normalizedPressure = (int) ((max - rawPressure) * 1024.0 / (max - min));
        int pressure = Math.max(0, Math.min(1024, normalizedPressure));

        // 13 ~ 16 tilt values
        int tiltX = data[13] & 0xFF;
        int tiltY = data[14] & 0xFF;

        // Правильная нормализация наклона:
        // 1. Центрируем значения вокруг 0 (0-255 -> -127 до +128)
        tiltX = tiltX - 128;
        tiltY = tiltY - 128;

        // 2. Нормализуем к диапазону -90 до +90 градусов
        tiltX = (int) (tiltX * 90.0 / 128.0);
        tiltY = (int) (tiltY * 90.0 / 128.0);

        // Ограничиваем диапазон
        tiltX = Math.max(-90, Math.min(90, tiltX));
        tiltY = Math.max(-90, Math.min(90, tiltY));

        // Определяем состояние стилуса
        boolean isHovering = rawPressure < max; // Стилус в зоне планшета
        boolean isTouching = pressure > 0;      // Стилус касается поверхности

        // 9 byte = stylus buttons
        // 11 12 bytes = buttons on tablet

        if (data[0] == 0x06 && isHovering) {
            VPen.writePosition(DriverMain.getVirtualPenHandle(), x, y, pressure, isTouching, tiltX, tiltY);
        }
    }

    private int u16From2Bytes(byte high, byte low) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }
}
