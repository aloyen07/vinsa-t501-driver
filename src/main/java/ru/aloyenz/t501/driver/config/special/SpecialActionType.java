package ru.aloyenz.t501.driver.config.special;

import com.google.gson.annotations.SerializedName;

public enum SpecialActionType {

    @SerializedName("KEY_INPUT")
    KEY_INPUT,
    @SerializedName("BASH_SCRIPT")
    BASH_SCRIPT,
    @SerializedName("NO_ACTION")
    NO_ACTION;
}
