package com.therabbits.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import com.therabbits.client.hud.HudRenderer;
import com.therabbits.client.module.Module;
import com.therabbits.client.module.hud.ReachModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitsClient implements ClientModInitializer {
    public static final String MOD_ID = "therabbits";
    public static final Logger LOG = LoggerFactory.getLogger("TheRabbits");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    public static KeyMapping menuKey;
    public static KeyMapping zoomKey;
    public static KeyMapping freelookKey;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.therabbits.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.therabbits.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.therabbits.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) ReachModule.onAttack(player, entity);
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(RabbitsClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Config.save());

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudRenderer::render);
        LOG.info("TheRabbits Client loaded");
    }

    private static void tick(Minecraft mc) {
        // Swap the vanilla title screen for ours.
        if (mc.screen instanceof TitleScreen) {
            mc.setScreen(new RabbitsTitleScreen());
        }

        while (menuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ModMenuScreen(null));
        }

        for (Module m : ModuleManager.all()) {
            if (m.enabled()) m.onTick();
        }
    }
}
