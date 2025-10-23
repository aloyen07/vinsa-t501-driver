package ru.aloyenz.t501.driver.config;

import com.google.gson.annotations.SerializedName;

public class KeyBinding {

    public KeyBinding() {}

    public KeyBinding(String key) {
        this.key = key;
    }

    public KeyBinding(String key, int value) {
        this.key = key;
        this.value = value;
    }

    @SerializedName("key")
    public String key;

    /** Nullable */
    @SerializedName("value")
    public Integer value;
}
