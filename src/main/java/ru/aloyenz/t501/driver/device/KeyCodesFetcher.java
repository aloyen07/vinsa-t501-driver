package ru.aloyenz.t501.driver.device;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aloyenz.t501.driver.config.KeyBinding;
import ru.aloyenz.t501.driver.config.KeyboardConfiguration;
import ru.aloyenz.t501.driver.config.special.SpecialAction;
import ru.aloyenz.t501.driver.config.special.SpecialActionType;
import ru.aloyenz.t501.driver.config.special.SpecialButtonsConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KeyCodesFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyCodesFetcher.class);

    private static final List<String> KEYCODE_PREFIXES = List.of(
            "KEY_",
            "BTN_",
            "REL_"
    );


    /**
     * Pattern to match keycode definitions in the header file.
     * Example line: KEY_STOP		128	/* AC Stop *\/
     * Groups:
     * 1 - Keycode name (e.g., KEY_STOP)
     * 2 - Whitespace (or tabs)
     * 3 - Keycode value (e.g., 128). Only if it's a number.
     * If there is a comment, group 5 contains whitespace before comment, group 6 contains the comment, group 4 contains both.
     * Else, group 4 is empty.
     * Also, supports hex values (e.g., 0x80)
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("^([A-Z_]+)([\\t ]+)(0x[0-9a-fA-F]+|\\d+)(([\\t ]+)(.+)$|)");

    private static final HashMap<String, KeyCode> keycodes = new HashMap<>();

    public static HashMap<String, KeyCode> getKeycodes() {
        return keycodes;
    }

    public static void load(String keycodesCache, String userInputHeadersPath, boolean forceFetch) {
        if (!forceFetch) {
            try {
                loadKeycodesFromCache(keycodesCache);
                LOGGER.info("Keycodes fetched from cache.");
                return;
            } catch (IOException e) {
                LOGGER.warn("Could not load keycodes from cache: {}. Fetching from headers...", e.getMessage());
            }
        }

        fetch(userInputHeadersPath, keycodesCache);
    }

    public static void loadKeycodesFromCache(String keycodesCache) throws IOException {
        String json = Files.readString(Path.of(keycodesCache), StandardCharsets.UTF_8);
        Gson gson = new Gson();

        Map<String, KeyCode> loadedKeycodes = gson.fromJson(json,
                new TypeToken<Map<String, KeyCode>>() {
                }.getType());

        keycodes.putAll(loadedKeycodes);
    }

    // We need to load keycodes from linux library (from input-event-codes.h)
    public static void fetch(String userInputHeadersPath, String keycodesCache) {
        // At first, we need to try to find the library. Basically, it should be located in
        // /usr/include/linux/input-event-codes.h
        // or in /usr/src/linux-hwe-[linux-headers version]/include/uapi/linux/input-event-codes.h

        String keycodeString = null;

        if (userInputHeadersPath != null) {
            // Trying to load from user-provided path
            File userFile = new File(userInputHeadersPath);
            if (userFile.exists()) {
                try {
                    keycodeString = readFileToString(userFile);
                } catch (IOException e) {
                    LOGGER.error("Error reading input event codes file from user path: {}", e.getMessage());
                    throw new RuntimeException("Error reading input event codes file from user path: " + e.getMessage());
                }
            } else {
                LOGGER.error("Header file (input-event-codes.h) not found: {}", userInputHeadersPath);
                throw new IllegalArgumentException("Header file (input-event-codes.h as " + userInputHeadersPath + ") not found");
            }
        }

        // At first, try to load from /usr/include/linux/input-event-codes.h
        if (keycodeString == null || keycodeString.isEmpty()) {
            File file = new File("/usr/include/linux/input-event-codes.h");
            if (!file.exists()) {
                LOGGER.warn("Input event codes file does not exist in /usr/include/linux/input-event-codes.h");
            } else {
                try {
                    keycodeString = readFileToString(file);
                } catch (IOException e) {
                    LOGGER.error("Error reading input event codes file: {}", e.getMessage());
                }
            }
        }

        // If not found, try to find in /usr/src/linux-hwe-[linux-headers version]/include/uapi/linux/input-event-codes.h
        // Finding the linux-headers version
        if (keycodeString == null || keycodeString.isEmpty()) {

            ProcessBuilder pb = new ProcessBuilder("uname", "-r");
            try {
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String linuxVersion = reader.readLine();
                process.waitFor();

                // For example, output is "6.14.0-33-generic".
                // But file located at /usr/src/linux-hwe-6.14-headers-6.14.0-33/include/uapi/linux/input-event-codes.h

                String[] split = linuxVersion.split("-");

                String notSignedVersion = split[0] + "-" + split[1]; // "6.14.0-33"
                String trimmedVersion = split[0].substring(0, split[0].lastIndexOf('.')); // "6.14"

                String path = "/usr/src/linux-hwe-" + trimmedVersion + "-headers-" + notSignedVersion +
                        "/include/uapi/linux/input-event-codes.h";

                LOGGER.info("Input event codes file: {}. Parsed linux version: Full: {}, notSigned: {}, trimmed: {}",
                        path, linuxVersion, notSignedVersion, trimmedVersion);

                File file = new File(path);
                if (file.exists()) {
                    try {
                        keycodeString = readFileToString(file);
                    } catch (IOException e) {
                        LOGGER.error("Error reading input event codes file in sources: {}", e.getMessage());
                    }
                } else {
                    LOGGER.warn("Input event codes file does not exist in {}", path);
                }

            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error executing uname command or reading headers: {}", e.getMessage());

            }
        }

        // If still not found, load predefined keycodes from resources
        if (keycodeString == null || keycodeString.isEmpty()) {
            LOGGER.warn("Headers not found in system paths, loading predefined keycodes from resources.");

            saveKeycodeCacheFromResources(keycodesCache);
            try {
                loadKeycodesFromCache(keycodesCache);
            } catch (IOException e) {
                LOGGER.error("Could not load keycodes from predefined cache: {}", e.getMessage(), e);
                throw new RuntimeException("Could not load keycodes from predefined cache: " + e.getMessage());
            }

            return;
        }

        // Parsing header file
        parseKeycodesInHeader(keycodeString);

        // Saving to cache
        try {
            storeKeycodesToFile(new File(keycodesCache));
        } catch (IOException e) {
            LOGGER.warn("Could not store keycodes to cache file: {}", e.getMessage(), e);
        }
    }

    public static void saveKeycodeCacheFromResources(String keycodesCache) {
        try (InputStream inputStream = KeyCodesFetcher.class.getResourceAsStream("/keycodes.json")) {
            // Reading resource "keycodes.json"
            InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

            BufferedReader reader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            // Writing to file
            Files.writeString(Path.of(keycodesCache), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Could not store keycodes from cache file: {}", e.getMessage(), e);
            throw new RuntimeException("Could not store keycodes from cache file: " + e.getMessage());
        }
    }

    private static String readFileToString(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private static void parseKeycodesInHeader(String headerContent) {
        // For example,
        //#define KEY_STOP		128	/* AC Stop */
        //#define KEY_AGAIN		129

        String[] lines = headerContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#define")) {
                // Removing #define
                String definePart = line.substring(7).trim();
                // KEY_STOP		128	/* AC Stop */
                // Ensure that is a keycode or button

                boolean matched = false;
                for (String prefix : KEYCODE_PREFIXES) {
                    if (definePart.startsWith(prefix)) {
                        matched = true;
                        break;
                    }
                }

                if (!matched) continue;

                // Parsing keycode name
                Matcher matcher = KEY_PATTERN.matcher(definePart);
                if (matcher.find()) {
                    // Extracting keycode name and value
                    String keyName = matcher.group(1);
                    String keyValueStr = matcher.group(3);
                    String comment = getComment(matcher);

                    // Parsing value (could be hex or decimal)
                    int keyValue;
                    try {
                        if (keyValueStr.startsWith("0x") || keyValueStr.startsWith("0X")) {
                            keyValue = Integer.parseInt(keyValueStr.substring(2), 16);
                        } else {
                            keyValue = Integer.parseInt(keyValueStr);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Invalid keycode value for {}: {} at string {}", keyName, keyValueStr, line);
                        continue;
                    }

                    // Storing in the map
                    keycodes.put(keyName, new KeyCode(keyValue, comment));

                } else {
                    // No match. Possibly not a valid keycode definition or int code
                    continue;
                }
            }
        }

        LOGGER.info("Loaded {} keycodes from header file.", keycodes.size());
    }

    private static String getComment(Matcher matcher) {
        String fullComment = matcher.group(4);
        String comment = null;

        if (fullComment != null && !fullComment.isEmpty()) {
            comment = matcher.group(6);
            // Trimming /* and */
            if (comment.startsWith("/*")) {
                comment = comment.substring(2).trim();
            }
            if (comment.endsWith("*/")) {
                comment = comment.substring(0, comment.length() - 2).trim();
            }
        }
        return comment;
    }

    public static void storeKeycodesToFile(File outputFile) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        LinkedHashMap<String, KeyCode> sorted = keycodes.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().keyCode()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        String json = gson.toJson(sorted);
        Files.writeString(outputFile.toPath(), json, StandardCharsets.UTF_8);
    }

    public static int[] getNeededKeycodes(KeyboardConfiguration keyboardConfiguration, SpecialButtonsConfig buttonsConfig) {
        List<KeyBinding> keyNames = new ArrayList<>();

        keyNames.addAll(Arrays.stream(keyboardConfiguration.ctrlPlus).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.ctrlMinus).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.bracketOpen).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.bracketClose).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.mouseScrollUp).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.mouseScrollDown).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.ctrl).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.alt).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.space).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.tab).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.b).toList());
        keyNames.addAll(Arrays.stream(keyboardConfiguration.e).toList());

        // Adding scripted keycodes
        for (SpecialAction action : buttonsConfig.getAllActions()) {
            if (action.type == SpecialActionType.KEY_INPUT) {
                keyNames.addAll(Arrays.asList(action.keycodes));
            }
        }

        // Removing duplicates
        keyNames = keyNames.stream().distinct().collect(Collectors.toList());

        return keyNames.stream()
                .mapToInt(name -> {
                    KeyCode keyCode = keycodes.get(name.key);
                    if (keyCode == null) {
                        throw new IllegalArgumentException("Keycode not found: " + name);
                    }
                    return keyCode.keyCode();
                })
                .toArray();
    }
}
