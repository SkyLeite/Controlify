//? if ftb_library {
package dev.isxander.controlify.compatibility.ftblibrary;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;

public interface FTBScreenExtension {
    default boolean isInteractive(Widget widget) {
        return false;
    }

    /**
     * @param atRoot true when no modal panel is open
     * @return true if the layout changed and focus should snap to the nearest widget
     */
    default boolean handleInput(BaseScreen gui, ControllerEntity controller, VirtualMouseHandler vmouse, boolean atRoot) {
        return false;
    }
}
//?}
