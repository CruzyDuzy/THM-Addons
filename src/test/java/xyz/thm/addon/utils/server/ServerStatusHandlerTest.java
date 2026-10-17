/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class ServerStatusHandlerTest {
    @Test
    void recognizesTheTagFactoryUsedByVanillaServerSystemMessages() throws Exception {
        var chat = new ClassNode();
        try (var input = getClass().getResourceAsStream("/net/minecraft/client/gui/components/ChatComponent.class")) {
            new ClassReader(input).accept(chat, ClassReader.SKIP_DEBUG);
        }
        var method = chat.methods.stream().filter(m -> m.name.equals("addServerSystemMessage")).findFirst().orElseThrow();
        int factories = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/client/multiplayer/chat/GuiMessageTag")
                && call.desc.equals("()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;")) {
                var tag = (GuiMessageTag) GuiMessageTag.class.getMethod(call.name).invoke(null);
                assertTrue(ServerStatusHandler.isSystemIndicator(tag), call.name);
                factories++;
            }
        }
        assertTrue(factories > 0, "Review system-message detection if vanilla stops supplying a tag.");
    }

    @Test
    void recognizesCurrentAndLegacySystemMessageTags() {
        assertTrue(ServerStatusHandler.isSystemIndicator(GuiMessageTag.systemSinglePlayer()));
        assertTrue(ServerStatusHandler.isSystemIndicator(GuiMessageTag.system()));
    }

    @Test
    void playerAndUnmarkedMessagesCannotArmCrackedLogin() {
        assertFalse(ServerStatusHandler.isSystemIndicator(null));
        assertFalse(ServerStatusHandler.isSystemIndicator(GuiMessageTag.chatNotSecure()));
        assertFalse(ServerStatusHandler.isSystemIndicator(GuiMessageTag.chatModified("/login <password>")));
        assertFalse(ServerStatusHandler.isSystemIndicator(GuiMessageTag.chatError()));
    }
}
