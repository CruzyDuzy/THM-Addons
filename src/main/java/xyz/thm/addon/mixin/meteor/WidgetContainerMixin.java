/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = WContainer.class, remap = false)
public abstract class WidgetContainerMixin {
    @ModifyArgs(method = "moveCells", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/widgets/WWidget;mouseMoved(DDDD)V"))
    private void thm$scrollMouse(Args args) {
        var window = mc.getWindow();
        double mouseX = mc.mouseHandler.getScaledXPos(window) * window.getGuiScale();
        double mouseY = mc.mouseHandler.getScaledYPos(window) * window.getGuiScale();
        // Scrolling must use the same coordinates as WidgetScreen input.
        args.setAll(mouseX, mouseY, mouseX, mouseY);
    }
}
