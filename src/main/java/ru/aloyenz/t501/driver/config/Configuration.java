package ru.aloyenz.t501.driver.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import ru.aloyenz.t501.driver.config.special.SpecialButtonsConfig;
import ru.aloyenz.t501.driver.exception.InvalidSpecialConfigException;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Configuration {

    private static Configuration instance;

    public static void init(File file) throws IOException, InvalidSpecialConfigException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (file.exists()) {

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                instance = gson.fromJson(reader, Configuration.class);
                instance.specialButtonsConfiguration.validate();
            }
        } else {
            instance = new Configuration();
        }

        // write back to ensure any new fields are added
        save(file);
    }

    public static void save(File file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(gson.toJson(instance));
            writer.flush();
        }
    }

    public static Configuration getInstance() {
        return instance;
    }

    @SerializedName("pen_name")
    public String penName = "T501 Virtual Pen (Aloyenz's Driver)";

    @SerializedName("keyboard_name")
    public String keyboardName = "T501 Virtual Keyboard (Aloyenz's Driver)";

    @SerializedName("mouse_name")
    public String mouseName = "T501 Virtual Mouse (Aloyenz's Driver)";

    @SerializedName("driver_version")
    public String driverVersion = "1.0.0";

    @SerializedName("hover_after_raw_pressure")
    public int hoverAfterRawPressure = 1400;

    @SerializedName("full_pressure_value")
    public int fullPressureValue = 677;

    @SerializedName("keyboard_keycodes")
    public KeyboardConfiguration keyboardConfiguration = new KeyboardConfiguration();

    @SerializedName("special_buttons")
    public SpecialButtonsConfig specialButtonsConfiguration = new SpecialButtonsConfig();

    @SerializedName("max_threads_for_scripts")
    public int maxThreadsForScripts = Runtime.getRuntime().availableProcessors();

    @SerializedName("replication_thread_delay_ms")
    public int replicationThreadDelayMs = 50;

    @SerializedName("read_thread_delay_ms")
    public int readThreadDelayMs = 0;
}
