package ru.aloyenz.t501.driver.device;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public record KeyCode(@SerializedName("key_code") int keyCode,
                      @SerializedName("comment") String comment) {
}
