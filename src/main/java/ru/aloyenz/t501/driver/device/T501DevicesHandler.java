package ru.aloyenz.t501.driver.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.*;
import ru.aloyenz.t501.driver.util.Pair;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Set;

public class T501DevicesHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(T501DevicesHandler.class);

    private final HashMap<Byte, Pair<DeviceHandle, DeviceInformation>> deviceHandlers = new HashMap<>();

    public T501DevicesHandler() {}

    public void onDeviceConnected(Device device, DeviceDescriptor descriptor) {
        if (deviceHandlers.containsKey(descriptor.iSerialNumber())) {
            LOGGER.info("Device {} already connected. Not reconnecting...", descriptor.iSerialNumber());
            return;
        }

        ConfigDescriptor configDescriptor = new ConfigDescriptor();
        try {
            int code = LibUsb.getConfigDescriptor(device, (byte) 0, configDescriptor);

            if (code != LibUsb.SUCCESS) {
                LOGGER.error("Failed to get config descriptor: {}", LibUsb.strError(code));
                return;
            }

            byte entrypoint = 0;
            byte ifaceNumber = 0;
            int maxPacketSize = 0;
            boolean found = false;

            for (Interface iface : configDescriptor.iface()) {
                for (InterfaceDescriptor ifaceDesc : iface.altsetting()) {
                    for (EndpointDescriptor epDesc : ifaceDesc.endpoint()) {
                        byte addr = epDesc.bEndpointAddress();

                        if ((addr & LibUsb.ENDPOINT_DIR_MASK) == LibUsb.ENDPOINT_IN && ifaceDesc.bInterfaceNumber() == 1) {
                            ifaceNumber = ifaceDesc.bInterfaceNumber();
                            maxPacketSize = epDesc.wMaxPacketSize();
                            entrypoint = addr;
                            LOGGER.info("Device {} has entrypoint {} and interface number {}...", descriptor.iSerialNumber(), addr, ifaceNumber);
                            found = true;
                        }
                    }
                }
            }

            if (!found) {
                LOGGER.error("Device {} has no entrypoint", descriptor.iSerialNumber());
                return;
            }

            // Handle device connection
            DeviceHandle handle = new DeviceHandle();

            int result = LibUsb.open(device, handle);
            if (result != LibUsb.SUCCESS) {
                LOGGER.error("Failed to open device: {}. This program must be run as sudo", LibUsb.strError(result));
                return;
            }
            LibUsb.detachKernelDriver(handle, ifaceNumber);

            // Reset the device to ensure it's in a known state
            result = LibUsb.resetDevice(handle);
            if (result != LibUsb.SUCCESS) {
                LOGGER.error("Failed to reset device: {}", LibUsb.strError(result));
                LibUsb.releaseInterface(handle, ifaceNumber);
                LibUsb.close(handle);
                return;
            }

            result = LibUsb.claimInterface(handle, ifaceNumber);
            if (result != LibUsb.SUCCESS) {
                LOGGER.error("Failed to claim interface: {}", LibUsb.strError(result));
                LibUsb.close(handle);
                return;
            }

            // Sending Full area packet
            byte[] report = new byte[] { (byte)0x08, (byte)0x03, (byte)0x00,
                    (byte)0xff, (byte)0xf0, (byte)0x00,
                    (byte)0xff, (byte)0xf0 };

            byte reportId = 0x08; // первый байт твоего отчёта
            byte reportType = 0x03; // Feature report (0x03)
            byte requestType = (byte) (LibUsb.REQUEST_TYPE_CLASS | LibUsb.RECIPIENT_INTERFACE | LibUsb.ENDPOINT_OUT);
            byte request = (byte) 0x09; // SET_REPORT
            short value = (short) ((reportType << 8) | reportId); // 0x0308
            short index = (short) ifaceNumber;

            ByteBuffer buffer = ByteBuffer.allocateDirect(report.length);
            buffer.put(report);
            buffer.rewind();

            IntBuffer transferred = IntBuffer.allocate(1);

            result = LibUsb.controlTransfer(handle, requestType, request, value, index, buffer, 5000);
            if (result < 0) {
                throw new RuntimeException("Error sending report via control transfer: " + LibUsb.strError(result));
            }

            LOGGER.info("Sent full mode report via control transfer, {} bytes. Transferred: {}", result, transferred.get(0));

            // Store the handle and device information
            LOGGER.info("Packet size for device {}: {}", descriptor.iSerialNumber(), maxPacketSize);
            deviceHandlers.put(descriptor.iSerialNumber(), new Pair<>(handle, new DeviceInformation(
                    ifaceNumber,
                    entrypoint,
                    maxPacketSize
            )));
            LOGGER.info("T501 device connected: VID={} PID={} SN={}",
                    String.format("%04x", descriptor.idVendor() & 0xffff),
                    String.format("%04x", descriptor.idProduct() & 0xffff),
                    descriptor.iSerialNumber());
        } finally {
            LibUsb.freeConfigDescriptor(configDescriptor);
        }
    }

    public void reattachKernelDrivers() {
        for (Pair<DeviceHandle, DeviceInformation> handle : deviceHandlers.values()) {
            LibUsb.attachKernelDriver(handle.first(), handle.second().interfaceNumber());
            LibUsb.close(handle.first());
        }

        deviceHandlers.clear();
    }

    public void onDeviceDisconnected(DeviceDescriptor descriptor) {
        LOGGER.info("T501 device disconnected: VID={} PID={} SN={}",
                String.format("%04x", descriptor.idVendor() & 0xffff),
                String.format("%04x", descriptor.idProduct() & 0xffff),
                descriptor.iSerialNumber());
        DeviceHandle handle = deviceHandlers.get(descriptor.iSerialNumber()).first();
        if (handle != null) {
            LibUsb.close(handle);
            deviceHandlers.remove(descriptor.iSerialNumber());
        }
    }

    public boolean isEmpty() {
        return deviceHandlers.isEmpty();
    }

    public int size() {
        return deviceHandlers.size();
    }

    public Set<Pair<DeviceHandle, DeviceInformation>> deviceHandlers() {
        return Set.copyOf(deviceHandlers.values());
    }
}
