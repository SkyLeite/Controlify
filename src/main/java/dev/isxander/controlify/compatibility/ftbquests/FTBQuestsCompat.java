//? if ftb_quests {
package dev.isxander.controlify.compatibility.ftbquests;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.ExpandChaptersButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.compatibility.ftblibrary.FTBScreenExtension;
import dev.isxander.controlify.compatibility.ftblibrary.FTBScreenProcessor;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.utils.CUtil;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class FTBQuestsCompat implements FTBScreenExtension {
    private static final double PAN_SPEED = 16;

    private final @Nullable Method cycleChapter = findCycleChapter();
    private boolean wasPanning;

    public static void init() {
        FTBScreenProcessor.addExtension(new FTBQuestsCompat());
    }

    @Override
    public boolean isInteractive(Widget widget) {
        // hovering it slides out the chapter list, which then covers it
        return widget instanceof ExpandChaptersButton
                && widget.getParent().getWidgets().stream()
                        .noneMatch(w -> w instanceof ChapterPanel panel && panel.getX() + panel.width > 0);
    }

    @Override
    public boolean handleInput(BaseScreen gui, ControllerEntity controller, VirtualMouseHandler vmouse, boolean atRoot) {
        if (!(gui instanceof QuestScreen questScreen) || !atRoot) {
            wasPanning = false;
            return false;
        }

        boolean changed = false;

        if (ControlifyBindings.VMOUSE_PAGE_NEXT.on(controller).justPressed()) {
            changed |= cycleChapter(questScreen, 1);
        }
        if (ControlifyBindings.VMOUSE_PAGE_PREV.on(controller).justPressed()) {
            changed |= cycleChapter(questScreen, -1);
        }
        if (ControlifyBindings.VMOUSE_PAGE_DOWN.on(controller).justPressed()) {
            questScreen.addZoom(1);
            changed = true;
        }
        if (ControlifyBindings.VMOUSE_PAGE_UP.on(controller).justPressed()) {
            questScreen.addZoom(-1);
            changed = true;
        }

        QuestPanel questPanel = questScreen.questPanel;
        double dx = ControlifyBindings.GUI_SECONDARY_NAVI_RIGHT.on(controller).analogueNow()
                - ControlifyBindings.GUI_SECONDARY_NAVI_LEFT.on(controller).analogueNow();
        double dy = ControlifyBindings.GUI_SECONDARY_NAVI_DOWN.on(controller).analogueNow()
                - ControlifyBindings.GUI_SECONDARY_NAVI_UP.on(controller).analogueNow();
        boolean panning = (dx != 0 || dy != 0) && questPanel.isMouseOver();

        if (panning) {
            // the right stick also drives vmouse scrolling, which zooms the quest map
            vmouse.preventScrollingThisTick();
            questPanel.setScrollX(questPanel.getScrollX() + dx * Math.abs(dx) * PAN_SPEED);
            questPanel.setScrollY(questPanel.getScrollY() + dy * Math.abs(dy) * PAN_SPEED);
        } else if (wasPanning) {
            changed = true;
        }
        wasPanning = panning;

        return changed;
    }

    private boolean cycleChapter(QuestScreen questScreen, int offset) {
        if (cycleChapter == null) return false;
        try {
            cycleChapter.invoke(questScreen, offset);
            return true;
        } catch (ReflectiveOperationException e) {
            CUtil.LOGGER.error("Failed to cycle FTB Quests chapter", e);
            return false;
        }
    }

    private static @Nullable Method findCycleChapter() {
        try {
            Method method = QuestScreen.class.getDeclaredMethod("cycleChapter", int.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            CUtil.LOGGER.error("Failed to find FTB Quests chapter cycling; bumpers won't switch chapters", e);
            return null;
        }
    }
}
//?}
