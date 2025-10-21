package ru.aloyenz.t501.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

public class T501DevicesHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(T501DevicesHandler.class);

    private final HashMap<Byte, Pair<DeviceHandle, DeviceInformation>> deviceHandlers = new HashMap<>();

    protected T501DevicesHandler() {}

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
            boolean sizeFound = false;

            outer:
            for (Interface iface : configDescriptor.iface()) {
                for (InterfaceDescriptor ifaceDesc : iface.altsetting()) {
                    for (EndpointDescriptor epDesc : ifaceDesc.endpoint()) {
                        byte addr = epDesc.bEndpointAddress();
                        if ((epDesc.bEndpointAddress() & LibUsb.ENDPOINT_IN) != 0 &&
                                (epDesc.bmAttributes() & LibUsb.TRANSFER_TYPE_MASK) == LibUsb.TRANSFER_TYPE_INTERRUPT && !sizeFound) {
                            maxPacketSize = epDesc.wMaxPacketSize();
                            sizeFound = true;
                        }

                        if ((addr & LibUsb.ENDPOINT_DIR_MASK) == LibUsb.ENDPOINT_IN && !found) {
                            ifaceNumber = ifaceDesc.bInterfaceNumber();
                            entrypoint = addr;
                            LOGGER.info("Device {} has entrypoint {}...", descriptor.iSerialNumber(), addr);
                            found = true;
                        }

                        if (found && sizeFound) {
                            break outer;
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
            LibUsb.detachKernelDriver(handle, 0);

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
            ByteBuffer report = ByteBuffer.allocateDirect(8);
            report.put(new byte[] { 0x08, 0x04, 0x1d, 0x01, (byte)0xff, (byte)0xff, 0x06, 0x2e });
            report.rewind();

            result = LibUsb.controlTransfer(handle,
                    (byte) 0x21,  // bmRequestType
                    (byte) 0x09,  // bRequest (SET_REPORT)
                    (short) 0x0308, // wValue
                    ifaceNumber, // wIndex (interface)
                    report,
                    250);

            if (result < 0) {
                LOGGER.error("Failed to claim transfer: {}", LibUsb.strError(result));
            }

            // Store the handle and device information
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
            LibUsb.attachKernelDriver(handle.first(), 0);
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
