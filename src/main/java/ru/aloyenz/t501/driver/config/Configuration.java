package ru.aloyenz.t501.driver.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Configuration {

    private static Configuration instance;

    public static void init(File file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (file.exists()) {

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                instance = gson.fromJson(reader, Configuration.class);
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

    @SerializedName("driver_version")
    public String driverVersion = "1.0.0";

}
