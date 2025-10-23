package ru.aloyenz.t501.driver.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import ru.aloyenz.t501.driver.DriverMain;
import ru.aloyenz.t501.driver.config.KeyBinding;
import ru.aloyenz.t501.driver.util.Pair;
import ru.aloyenz.t501.driver.util.Quaternion;
import ru.aloyenz.t501.driver.util.Triple;
import ru.aloyenz.t501.driver.virtual.VKeyboard;
import ru.aloyenz.t501.driver.virtual.VMouse;
import ru.aloyenz.t501.driver.virtual.VPen;
import ru.aloyenz.t501.driver.bash.ProcessManager;
import ru.aloyenz.t501.driver.config.Configuration;
import ru.aloyenz.t501.driver.config.special.SpecialAction;
import ru.aloyenz.t501.driver.config.special.SpecialActionType;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DeviceReader {

    private static final int MAX_8BIT = (int) (Math.pow(2, 8) - 1);

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceReader.class);

    private final T501DevicesHandler devicesHandler;

    private final List<Long> disconnectedHandles = new ArrayList<>();
    private final KeyboardManager keyboardManager = new KeyboardManager();

    private boolean isTouchedSpecial = false;
    private boolean wasHovering = false;
    private int[] pressedKeys = new int[0];

    // Key code - REL_ event - value (nullable)
    private final ArrayList<Triple<Integer, Boolean, Integer>> mouseButtons = new ArrayList<>();
    private final ArrayList<Triple<Integer, Boolean, Integer>> mouseButtonsToRelease = new ArrayList<>();

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

        if (data[0] == 0x06) {
            // HID Report for stylus. Process it.
            processHID(data);
        }
    }

    private void processHID(byte[] data) {
        // For debug: showing raw HID report in hex
//        StringBuilder hexString = new StringBuilder();
//        for (byte b : data) {
//            hexString.append(String.format("%02X ", b));
//        }

        byte xLow = data[2];
        byte xHigh = data[1];
        int x = u16From2Bytes(xHigh, xLow);

        byte yLow = data[4];
        byte yHigh = data[3];
        int y = u16From2Bytes(yHigh, yLow);

        boolean onSpecialButton = (y & 0b1111000000000000) != 0;

        int rawPressure = u16From2Bytes(data[5], data[6]);

        int min = Configuration.getInstance().fullPressureValue;      // full press
        int max = Configuration.getInstance().hoverAfterRawPressure;  // hover

        // Normalizing pressure to 0 ~ 1024
        int normalizedPressure = (int) ((max - rawPressure) * 1024.0 / (max - min));
        int pressure = Math.max(0, Math.min(1024, normalizedPressure));

        // 13 ~ 16 tilt values
        int tiltX = data[13] & 0xFF;
        int tiltY = data[14] & 0xFF;

        // Centering tilt values around 0
        tiltX = tiltX - 128;
        tiltY = tiltY - 128;

        // Normalizing tilt to degrees
        tiltX = (int) (tiltX * 90.0 / 128.0);
        tiltY = (int) (tiltY * 90.0 / 128.0);

        // Diapazon tilt: -90 to 90
        tiltX = Math.max(-90, Math.min(90, tiltX));
        tiltY = Math.max(-90, Math.min(90, tiltY));

        boolean isHovering = rawPressure > 0;
        boolean isTouching = pressure > 0;

        // 9 byte = stylus buttons
        // 0x02 = not pressed
        // 0x04 = pressed +
        // 0x06 = pressed -
        byte buttonState = data[9];
        boolean buttonPlusPressed = buttonState == 0x04;
        boolean buttonMinusPressed = buttonState == 0x06;

        // For debug: showing raw HID report in hex with some parsed values
        //System.out.print(hexString + " " + buttonPlusPressed + " " + buttonMinusPressed + "\r");

        // 11 12 bytes = buttons on tablet
        short tabletButtons = (short) ((data[11] & 0xFF) << 8 | (data[12] & 0xFF));

        List<Pair<Integer, Quaternion<Boolean, Boolean, Boolean, Integer>>> keyEvents = keyboardManager.getKeyEvents(tabletButtons);

        // Process stylus
        if (isHovering) {
            // Sending position data if not on special button
            if (!onSpecialButton) {
                VPen.writePosition(DriverMain.getVirtualPenHandle(),
                        x, y,
                        pressure, isTouching,
                        tiltX, tiltY,
                        buttonPlusPressed, buttonMinusPressed);
            } else if (isTouching) {
                // We need to process special button if stylus touched
                processSpecialButton(x);
            } else {
                if (isTouchedSpecial) {
                    // Resetting all mouse keys
                    mouseButtonsToRelease.addAll(mouseButtons);
                    mouseButtons.clear();
                }

                isTouchedSpecial = false;

                if (pressedKeys.length > 0) {
                    VKeyboard.keyboardKeyEvent(
                            DriverMain.getVirtualKeyboardHandle(),
                            pressedKeys,
                            new boolean[pressedKeys.length] // All false array
                    );

                    pressedKeys = new int[0]; // Resetting pressed keys
                }

            }

            // Sending keyboard events
            if (!keyEvents.isEmpty()) {
                int[] keyCodes = new int[keyEvents.size()];
                boolean[] pressedStates = new boolean[keyEvents.size()];

                int index = 0;
                for (var entry : keyEvents) {
                    if (!entry.second().second()) { // Not mouse event

                        keyCodes[index] = entry.first();
                        pressedStates[index] = entry.second().first();

                    } else {
                        Triple<Integer, Boolean, Integer> o = new Triple<>(
                                entry.first(),
                                entry.second().third(),
                                entry.second().fourth());

                        if (entry.second().first()) { // Pressed
                            mouseButtons.add(o);
                        } else { // Released
                            mouseButtonsToRelease.add(o);
                            mouseButtons.remove(o);
                        }
                    }

                    index++;
                }

                // Removing 0 keycodes (mouse or unknown events)
                List<Integer> filteredKeyCodes = new ArrayList<>();
                List<Boolean> filteredPressedStates = new ArrayList<>();

                for (int i = 0; i < keyCodes.length; i++) {
                    if (keyCodes[i] != 0) {
                        filteredKeyCodes.add(keyCodes[i]);
                        filteredPressedStates.add(pressedStates[i]);
                    }
                }

                keyCodes = filteredKeyCodes.stream().mapToInt(Integer::intValue).toArray();
                pressedStates = new boolean[filteredPressedStates.size()];

                VKeyboard.keyboardKeyEvent(DriverMain.getVirtualKeyboardHandle(), keyCodes, pressedStates);
            }
        } else if (wasHovering) {
            // Stylus has just left hover state
            VPen.penLeave(DriverMain.getVirtualPenHandle());
        }

        // Updating previous state
        wasHovering = isHovering;
    }

    public Thread getReplicationThread() {

        return new Thread(() -> {
            while (true) {
                // Sending mouse button events
                int[] codes = new int[mouseButtons.size()];
                boolean[] rels = new boolean[mouseButtons.size()];
                int[] values = new int[mouseButtons.size()];

                int index = 0;
                for (var button : mouseButtons) {
                    codes[index] = button.first();
                    rels[index] = button.second();

                    if (button.third() != null) {
                        values[index] = button.third();
                    } else {
                        values[index] = 1; // Default value for button press
                    }

                    index++;
                }

                VMouse.mouseEvent(DriverMain.getVirtualMouseHandle(), codes, rels, values);

                // Sending mouse button release events
                codes = new int[mouseButtonsToRelease.size()];
                rels = new boolean[mouseButtonsToRelease.size()];
                values = new int[mouseButtonsToRelease.size()];

                index = 0;
                for (var button : mouseButtonsToRelease) {
                    codes[index] = button.first();
                    rels[index] = button.second();
                    values[index] = 0;

                    index++;
                }
                mouseButtonsToRelease.clear();

                VMouse.mouseEvent(DriverMain.getVirtualMouseHandle(), codes, rels, values);

                try {
                    Thread.sleep(Configuration.getInstance().replicationThreadDelayMs);
                } catch (InterruptedException e) {
                    LOGGER.info("Replication thread interrupted: {}", e.getMessage());
                    return;
                }
            }
        });
    }

    private void processSpecialButton(int x) {
        if (!isTouchedSpecial) {
            isTouchedSpecial = true;
            processSpecialButtonImpl(x);
        }
    }

    private void processSpecialButtonImpl(int x) {
        // Okay, we need to map X coordinate and process some script from config

        // We have 10 special buttons mapped evenly across the X axis.
        // We need to extract button number from X coordinate (0 ~ 4096 - X range)
        int buttonNumber = (x * 10) / 4096;

        LOGGER.debug("Special button #{} touched at X={}", buttonNumber, x);

        // Process from configuration
        SpecialAction action = Configuration.getInstance().specialButtonsConfiguration.getAllActions().get(buttonNumber);
        if (action.type == null) {
            LOGGER.warn("Special button #{} has null action type, ignoring", buttonNumber);
            return;
        }

        if (action.type == SpecialActionType.KEY_INPUT) {
            // Key Input
            if (action.keycodes != null && action.keycodes.length > 0) {
                int[] keyCodes = new int[action.keycodes.length];
                HashMap<String, KeyCode> keycodeCache = KeyCodesFetcher.getKeycodes();

                for (int i = 0; i < action.keycodes.length; i++) {
                    KeyBinding keyBinding = action.keycodes[i];
                    String keycodeStr = keyBinding.key;

                    if (keycodeStr.startsWith("BTN_") || keycodeStr.startsWith("REL_")) {
                        // Adding to mouse
                        Triple<Integer, Boolean, Integer> o = new Triple<>(
                                keycodeCache.get(keycodeStr).keyCode(),
                                keycodeStr.startsWith("REL_"),
                                keyBinding.value
                        );
                        mouseButtons.add(o);
                        continue; // Skip adding to keyboard keycodes
                    }


                    KeyCode keycode = keycodeCache.get(keycodeStr);

                    if (keycode != null) {
                        keyCodes[i] = keycode.keyCode();
                    } else {
                        LOGGER.warn("Special button key input has unknown keycode name: {}", keycodeStr);
                    }
                }

                // Removing 0 keycodes (unknown keycodes)
                List<Integer> filteredKeyCodes = new ArrayList<>();
                for (int keyCode : keyCodes) {
                    if (keyCode != 0) {
                        filteredKeyCodes.add(keyCode);
                    }
                }

                keyCodes = filteredKeyCodes.stream().mapToInt(Integer::intValue).toArray();

                boolean[] pressedStates = new boolean[keyCodes.length];
                Arrays.fill(pressedStates, true);

                // Store pressed keys for release later
                this.pressedKeys = keyCodes;

                VKeyboard.keyboardKeyEvent(
                        DriverMain.getVirtualKeyboardHandle(),
                        keyCodes,
                        pressedStates
                );
            }
        } else if (action.type == SpecialActionType.BASH_SCRIPT) {
            ProcessManager.runScripts(action.scripts);
        }
    }

    private int u16From2Bytes(byte high, byte low) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }
}
