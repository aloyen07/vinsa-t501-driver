package ru.aloyenz.t501.driver.config.special;

import com.google.gson.annotations.SerializedName;
import ru.aloyenz.t501.driver.config.KeyBinding;

public class SpecialAction {

    public static SpecialAction keyInput(String comment, KeyBinding[] keycodes) {
        SpecialAction action = new SpecialAction();

        action.comment = comment;
        action.type = SpecialActionType.KEY_INPUT;
        action.keycodes = keycodes;
        return action;
    }

    public static SpecialAction bashScript(String comment, String[][] scripts) {
        SpecialAction action = new SpecialAction();

        action.comment = comment;
        action.type = SpecialActionType.BASH_SCRIPT;
        action.scripts = scripts;

        return action;
    }

    public static SpecialAction noAction(String comment) {
        SpecialAction action = new SpecialAction();

        action.comment = comment;
        action.type = SpecialActionType.NO_ACTION;

        return action;
    }

    @SerializedName("_comment")
    public String comment;

    @SerializedName("type")
    public SpecialActionType type;

    @SerializedName("keycodes")
    public KeyBinding[] keycodes;

    @SerializedName("scripts")
    public String[][] scripts;

    private SpecialAction() {}
}
