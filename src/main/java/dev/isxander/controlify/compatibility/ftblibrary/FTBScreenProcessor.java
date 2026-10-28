//? if ftb_library {
package dev.isxander.controlify.compatibility.ftblibrary;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.isxander.controlify.api.vmousesnapping.ISnapBehaviour;
import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.utils.CUtil;
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * FTB Library widgets only react to the mouse position, so controller focus is emulated by
 * snapping the virtual mouse between interactive widgets and hiding the cursor while it rests on one.
 */
public class FTBScreenProcessor extends ScreenProcessor<ScreenWrapper> implements ISnapBehaviour {
    private static final List<FTBScreenExtension> extensions = new ArrayList<>();
    private static final @Nullable Field modalPanelsField = findModalPanelsField();

    private final BaseScreen gui;
    private @Nullable Panel lastRoot;

    public FTBScreenProcessor(ScreenWrapper screen) {
        super(screen);
        this.gui = screen.getGui();
    }

    public static void addExtension(FTBScreenExtension extension) {
        extensions.add(extension);
    }

    @Override
    public VirtualMouseBehaviour virtualMouseBehaviour() {
        return VirtualMouseBehaviour.ENABLED;
    }

    @Override
    protected void handleScreenVMouse(ControllerEntity controller, VirtualMouseHandler vmouse) {
        super.handleScreenVMouse(controller, vmouse);

        Panel root = interactionRoot();
        boolean resnap = root != lastRoot;
        lastRoot = root;

        for (FTBScreenExtension extension : extensions) {
            resnap |= extension.handleInput(gui, controller, vmouse, root == gui);
        }

        if (resnap && targetUnderCursor() == null) {
            snapToNearest(vmouse);
        }
    }

    @Override
    public void onVirtualMouseBack() {
        // escape pops the topmost modal panel (quest view, context menu) before closing the screen
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
    }

    @Override
    public boolean hideVirtualMouseCursor() {
        return targetUnderCursor() != null;
    }

    @Override
    protected void render(ControllerEntity controller, GuiGraphics graphics, float tickDelta, Optional<VirtualMouseHandler> vmouse) {
        if (vmouse.isEmpty()) return;

        Target target = targetUnderCursor();
        if (target == null) return;

        graphics.pose().pushPose();
        // below vanilla tooltips (z 400) so the highlight never covers them
        graphics.pose().translate(0, 0, 200);
        graphics.renderOutline(target.x - 1, target.y - 1, target.w + 2, target.h + 2, 0xFFFFFFFF);
        graphics.pose().popPose();
    }

    @Override
    public void controlify$collectSnapPoints(Consumer<SnapPoint> consumer) {
        for (Target target : collectTargets()) {
            consumer.accept(new SnapPoint(target.centerX(), target.centerY(), Math.max(2, Math.min(target.w, target.h) / 2)));
        }
    }

    private void snapToNearest(VirtualMouseHandler vmouse) {
        int mx = gui.getMouseX();
        int my = gui.getMouseY();

        collectTargets().stream()
                .min(Comparator.comparingLong(t -> t.distanceSquared(mx, my)))
                .ifPresent(target -> {
                    var window = minecraft.getWindow();
                    var scaleFactor = new Vector2d(
                            (double) window.getGuiScaledWidth() / window.getScreenWidth(),
                            (double) window.getGuiScaledHeight() / window.getScreenHeight());
                    vmouse.snapToPoint(new SnapPoint(target.centerX(), target.centerY(), 0), scaleFactor);
                });
    }

    private @Nullable Target targetUnderCursor() {
        int mx = gui.getMouseX();
        int my = gui.getMouseY();

        List<Target> targets = collectTargets();
        // later widgets are drawn on top
        for (int i = targets.size() - 1; i >= 0; i--) {
            if (targets.get(i).contains(mx, my)) return targets.get(i);
        }
        return null;
    }

    private List<Target> collectTargets() {
        List<Target> targets = new ArrayList<>();
        var window = minecraft.getWindow();
        collect(interactionRoot(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), targets);
        return targets;
    }

    private void collect(Panel panel, int clipX0, int clipY0, int clipX1, int clipY1, List<Target> out) {
        if (panel.getOnlyRenderWidgetsInside()) {
            clipX0 = Math.max(clipX0, panel.getX());
            clipY0 = Math.max(clipY0, panel.getY());
            clipX1 = Math.min(clipX1, panel.getX() + panel.width);
            clipY1 = Math.min(clipY1, panel.getY() + panel.height);
        }
        if (clipX0 >= clipX1 || clipY0 >= clipY1) return;

        panel.setOffset(true);
        try {
            for (Widget widget : panel.getWidgets()) {
                if (!widget.shouldDraw() || !widget.isEnabled()) continue;

                if (widget instanceof Panel child) {
                    collect(child, clipX0, clipY0, clipX1, clipY1, out);
                } else if (isInteractive(widget)) {
                    int x0 = Math.max(clipX0, widget.getX());
                    int y0 = Math.max(clipY0, widget.getY());
                    int x1 = Math.min(clipX1, widget.getX() + widget.width);
                    int y1 = Math.min(clipY1, widget.getY() + widget.height);
                    if (x0 < x1 && y0 < y1) {
                        out.add(new Target(x0, y0, x1 - x0, y1 - y0));
                    }
                }
            }
        } finally {
            panel.setOffset(false);
        }
    }

    private static boolean isInteractive(Widget widget) {
        if (widget instanceof Button || widget instanceof TextBox) return true;
        for (FTBScreenExtension extension : extensions) {
            if (extension.isInteractive(widget)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Panel interactionRoot() {
        if (modalPanelsField != null) {
            try {
                ModalPanel top = ((Deque<ModalPanel>) modalPanelsField.get(gui)).peekFirst();
                if (top != null) return top;
            } catch (IllegalAccessException ignored) {
            }
        }
        return gui;
    }

    private static @Nullable Field findModalPanelsField() {
        try {
            Field field = BaseScreen.class.getDeclaredField("modalPanels");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            CUtil.LOGGER.error("Failed to access FTB Library modal panels; controller focus will ignore popups", e);
            return null;
        }
    }

    private record Target(int x, int y, int w, int h) {
        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }

        long distanceSquared(int px, int py) {
            long dx = centerX() - px;
            long dy = centerY() - py;
            return dx * dx + dy * dy;
        }
    }
}
//?}
