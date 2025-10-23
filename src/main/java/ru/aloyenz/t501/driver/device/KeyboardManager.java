package ru.aloyenz.t501.driver.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aloyenz.t501.driver.config.Configuration;
import ru.aloyenz.t501.driver.config.KeyBinding;
import ru.aloyenz.t501.driver.util.Pair;
import ru.aloyenz.t501.driver.util.Quaternion;
import ru.aloyenz.t501.driver.util.Triple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private boolean initialized = false;

    private short newState = 0;

    /**
     * Integer - Key code
     * First Boolean - Key pressed (true) / released (false)
     * Second Boolean - is mouse event (true) / keyboard event (false)
     * Third Boolean - is REL_ event (true) / not REL_ event (false)
     * Integer - value (nullable)
     */
    public List<Pair<Integer, Quaternion<Boolean, Boolean, Boolean, Integer>>> getKeyEvents(short state) {

        short oldState;
        if (!initialized) {
            newState = state;
            initialized = true;
            return new ArrayList<>();
        }

        oldState = newState;
        newState = state;

        // Determine changed bits
        short changedBits = (short) (oldState ^ newState);

        /*
         * In this bit mask...
         *
         * Bit 0:   Ctrl -
         * Bit 1:   [
         * Bit 2:   Mouse scroll up
         * Bit 3:   Mouse scroll down
         * Bit 4:   Ctrl
         * Bit 5:   Alt
         * Bit 6:   Space
         * Bit 7:   Tab
         * Bit 8:   Unused
         * Bit 9:   Unused
         * Bit 10:  ]
         * Bit 11:  B
         * Bit 12:  Unused
         * Bit 13:  Unused
         * Bit 14:  E
         * Bit 15:  Ctrl +
         *
         * Laboratoricaly determined mapping based on debugging. 1 = pressed.
         */

//        System.out.print(String.format("%16s", Integer.toBinaryString(changedBits & 0xFFFF)).replace(' ', '0')
//                + " " + String.format("%16s", Integer.toBinaryString(state & 0xFFFF)).replace(' ', '0') + "\r");

        List<Pair<Integer, Quaternion<Boolean, Boolean, Boolean, Integer>>> keyEvents = new ArrayList<>();

        for (int bit = 0; bit < 16; bit++) {
            if ((changedBits & (1 << bit)) != 0) {

                // Bit changed
                boolean isActive = (newState & (1 << bit)) != 0;
                for (Quaternion<Integer, Boolean, Boolean, Integer> i : getEventsByBitNumber(15 - bit)) {
                    keyEvents.add(new Pair<>(i.first(),
                            new Quaternion<>(
                                    !isActive,
                                    i.second(), i.third(),
                                    i.fourth())));
                }
            }
        }

        return keyEvents;
    }

    private List<Quaternion<Integer, Boolean, Boolean, Integer>> getEventsByBitNumber(int bitNumber) {
        //System.out.print("Bit number: " + bitNumber + "\r");

        return switch (bitNumber) {
            case 0 -> // Ctrl -
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.ctrlMinus);
            case 1 -> // [
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.bracketOpen);
            case 2 -> // Mouse scroll up
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.mouseScrollUp);
            case 3 -> // Mouse scroll down
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.mouseScrollDown);
            case 4 -> // Ctrl
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.ctrl);
            case 5 -> // Alt
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.alt);
            case 6 -> // Space
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.space);
            case 7 -> // Tab
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.tab);
            case 10 -> // ]
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.bracketClose);
            case 11 -> // B
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.b);
            case 14 -> // E
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.e);
            case 15 -> // Ctrl +
                    getKeyCodesFromNames(Configuration.getInstance().keyboardConfiguration.ctrlPlus);
            default -> new ArrayList<>();
        };
    }

    /**
     * Convert key code names to key codes
     * @return Integer - Key code, Boolean 1 - is mouse event, Boolean 2 - is REL_ event, Integer - value (nullable)
     */
    private List<Quaternion<Integer, Boolean, Boolean, Integer>> getKeyCodesFromNames(KeyBinding[] names) {
        List<Quaternion<Integer, Boolean, Boolean, Integer>> keyCodes = new ArrayList<>();

        for (KeyBinding name : names) {
            KeyCode code = KeyCodesFetcher.getKeycodes().get(name.key);

            if (code == null) {
                logger.error("Failed to find key code for key code {}", name);
                continue;
            }

            keyCodes.add(new Quaternion<>(
                    KeyCodesFetcher.getKeycodes().get(name.key).keyCode(),
                    name.key.startsWith("BTN_") || name.key.startsWith("REL_"),
                    name.key.startsWith("REL_"),
                    name.value
            ));
        }

        return keyCodes;
    }

}
