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
    public static KeyMapping waypointKey;

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

        waypointKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.therabbits.waypoint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                com.therabbits.client.module.utility.AltHiderModule.active()
                        && message.getString().contains(Minecraft.getInstance().getUser().getName())
                        ? net.minecraft.network.chat.Component.literal(com.therabbits.client.module.utility.AltHiderModule.hide(message.getString()))
                        : message);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                ReachModule.onAttack(player, entity);
                com.therabbits.client.module.hud.ComboModule.onHit();
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(RabbitsClient::tick);
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
                @SuppressWarnings("unchecked")
                var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel>) (Object) avatar;
                helper.register(new com.therabbits.client.cosmetics.CosmeticsLayer(parent));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Config.save());

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudRenderer::render);
        LOG.info("TheRabbits Client loaded");
    }

    private static boolean accountStarted;

    private static void tick(Minecraft mc) {
        // Swap the vanilla title screen for ours.
        if (!accountStarted) {
            accountStarted = true;
            com.therabbits.client.account.Account.loadConfig();
            com.therabbits.client.account.Account.refreshNow();
        }
        if (mc.screen instanceof TitleScreen || mc.screen instanceof RabbitsTitleScreen) {
            boolean needKey = com.therabbits.client.account.Account.licenseRequired && !com.therabbits.client.account.Account.licensed;
            if (needKey) mc.setScreen(new com.therabbits.client.gui.LicenseScreen());
            else if (mc.screen instanceof TitleScreen) mc.setScreen(new RabbitsTitleScreen());
        }

        while (waypointKey.consumeClick()) {
            var wp = ModuleManager.get(com.therabbits.client.module.hud.WaypointsModule.class);
            if (wp.enabled()) wp.addHere();
        }
        while (menuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new com.therabbits.client.gui.HubScreen(null));
        }

        for (Module m : ModuleManager.all()) {
            if (m.enabled()) m.onTick();
        }
    }
}
