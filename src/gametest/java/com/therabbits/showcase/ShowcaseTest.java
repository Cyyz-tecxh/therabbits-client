package com.therabbits.showcase;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import com.therabbits.client.module.combat.CrosshairModule;
import com.therabbits.client.module.hud.ClockModule;
import com.therabbits.client.module.hud.CoordinatesModule;
import com.therabbits.client.module.hud.DirectionModule;
import com.therabbits.client.module.hud.FpsModule;
import com.therabbits.client.module.hud.HudStyleModule;
import com.therabbits.client.module.visual.FullbrightModule;
import com.therabbits.client.module.visual.MenuBackgroundModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Feature showcase. Screenshot names start with a scene tag; the video script turns tags into captions.
 */
public class ShowcaseTest implements FabricClientGameTest {
    private int shot = 0;

    private void frames(ClientGameTestContext ctx, String scene, int count) {
        for (int i = 0; i < count; i++) {
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("%s_%04d", scene, shot++));
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void cursor(ClientGameTestContext ctx, double guiX, double guiY) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        cursor(ctx, guiX, guiY);
        ctx.waitTick();
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private void bigWindow(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        });
        ctx.waitTicks(5);
    }

    /** Spawns a frozen zombie 3 blocks in front of the player. */
    private static Mob zombieAhead(ServerPlayer p, boolean totem) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 pos = p.position().add(flat.scale(3.2));
        Mob z = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        z.setPos(pos.x, p.getY(), pos.z);
        z.setYRot(p.getYRot() + 180);
        z.setYHeadRot(p.getYRot() + 180);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        if (totem) z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        level.addFreshEntity(z);
        return z;
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        // Apply every mixin now so a broken hook fails loudly in CI
        org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
        });

        // ================= In-game features (world first: it loads faster in a small window) =================
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 300, 1));
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 240, 0));
                p.setXRot(0);
                p.getInventory().add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
                p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 16));
                p.getInventory().add(new ItemStack(Items.ENDER_PEARL, 12));
                p.getInventory().add(new ItemStack(Items.EXPERIENCE_BOTTLE, 32));
                p.getInventory().add(new ItemStack(Items.ARROW, 48));
                server.overworld().setDayTime(1000);
            });
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                ModuleManager.get(DirectionModule.class).setEnabled(true);
                ModuleManager.get(CoordinatesModule.class).setEnabled(true);
                ModuleManager.get(ClockModule.class).setEnabled(true);
                ModuleManager.get(com.therabbits.client.module.hud.SpeedModule.class).setEnabled(true);
                ModuleManager.get(com.therabbits.client.module.hud.BiomeModule.class).setEnabled(true);
                mc.setScreen(null);
            });
            bigWindow(ctx);
            sp.getClientWorld().waitForChunksRender();
            int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
            int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());

            // 1. HUD + keystrokes
            frames(ctx, "c01hud", 8);
            ctx.getInput().holdKey(o -> o.keyUp);
            frames(ctx, "c01hud", 12);
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.getInput().holdKey(o -> o.keyLeft);
            frames(ctx, "c01hud", 6);
            ctx.getInput().releaseKey(o -> o.keyLeft);
            ctx.getInput().holdKey(o -> o.keyJump);
            frames(ctx, "c01hud", 5);
            ctx.getInput().releaseKey(o -> o.keyJump);
            frames(ctx, "c01hud", 5);

            // 1b. HUD style: colors and size
            ctx.runOnClient(mc -> {
                HudStyleModule st = ModuleManager.get(HudStyleModule.class);
                st.textColor.set(8);   // Aqua
                st.textSize.set(1.15);
            });
            frames(ctx, "c10style", 14);
            ctx.runOnClient(mc -> {
                ModuleManager.get(HudStyleModule.class).textColor.set(12);  // Chroma
                ModuleManager.get(FpsModule.class).textColor.set(4);        // FPS: Red (index 3 + Global)
            });
            frames(ctx, "c10style", 20);
            ctx.runOnClient(mc -> {
                HudStyleModule st = ModuleManager.get(HudStyleModule.class);
                st.textColor.set(0); st.textSize.set(1.0);
                ModuleManager.get(FpsModule.class).textColor.set(0);
            });
            // style settings in the mod menu
            ctx.runOnClient(mc -> {
                ModMenuScreen menu = new ModMenuScreen(null);
                mc.setScreen(menu);
                menu.openSettings(ModuleManager.get(HudStyleModule.class));
            });
            cursor(ctx, w / 2.0 + 200, h / 2.0 + 130);
            frames(ctx, "c11stylemenu", 18);
            ctx.runOnClient(mc -> mc.setScreen(null));

            // 1c. Low health warning
            sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).setHealth(5f));
            frames(ctx, "c12lowhp", 22);
            sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).setHealth(20f));

            // 2. Crosshair styles
            for (int s = 0; s <= 6; s++) {
                final int style = s;
                ctx.runOnClient(mc -> ModuleManager.get(CrosshairModule.class).style.set(style));
                frames(ctx, "c02cross", 4);
            }
            ctx.runOnClient(mc -> ModuleManager.get(CrosshairModule.class).style.set(0));

            // 3. Zoom
            ctx.getInput().holdKey(o -> RabbitsClient.zoomKey);
            frames(ctx, "c03zoom", 16);
            ctx.getInput().releaseKey(o -> RabbitsClient.zoomKey);
            frames(ctx, "c03zoom", 6);

            // 4. Reach: hit a zombie standing 3 blocks away
            sp.getServer().runOnServer(server -> zombieAhead(server.getPlayerList().getPlayers().get(0), false));
            frames(ctx, "c04reach", 6);
            for (int i = 0; i < 3; i++) {
                ctx.getInput().pressKey(o -> o.keyAttack);
                frames(ctx, "c04reach", 8);
            }

            // 5. Totem pops: a zombie holding a totem takes lethal damage twice
            sp.getServer().runOnServer(server -> {
                server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard());
                Mob z = zombieAhead(server.getPlayerList().getPlayers().get(0), true);
                z.setCustomName(net.minecraft.network.chat.Component.literal("Rival"));
            });
            frames(ctx, "c05totem", 6);
            for (int i = 0; i < 2; i++) {
                sp.getServer().runOnServer(server -> server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> {
                    z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                    z.hurtServer(server.overworld(), server.overworld().damageSources().generic(), 1000f);
                }));
                frames(ctx, "c05totem", 14);
            }
            sp.getServer().runOnServer(server -> server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard()));

            // 6. Freelook
            ctx.getInput().holdKey(o -> RabbitsClient.freelookKey);
            ctx.waitTicks(2);
            for (int i = 0; i < 30; i++) {
                ctx.getInput().moveCursor(20, i < 15 ? -3 : 3);
                ctx.waitTick();
                sleep(35);
                ctx.takeScreenshot(String.format("c06free_%04d", shot++));
            }
            ctx.getInput().releaseKey(o -> RabbitsClient.freelookKey);
            frames(ctx, "c06free", 4);

            // 7. Fullbright at night
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(18000));
            frames(ctx, "c07bright", 12);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(true));
            frames(ctx, "c07bright", 14);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(false));
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(1000));

            // 8. Mod menu: click through each category
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "c08menu", 10);
            for (com.therabbits.client.module.Category c : com.therabbits.client.module.Category.values()) {
                ctx.runOnClient(mc -> ((ModMenuScreen) mc.screen).selectCategory(c));
                cursor(ctx, w / 2.0 - 60, h / 2.0 + 10);
                frames(ctx, "c08menu", 12);
            }

            // 8b. Presets page: show it, then load the Minimal preset and the PvP preset
            ctx.runOnClient(mc -> ((ModMenuScreen) mc.screen).showPresets());
            cursor(ctx, w / 2.0, h / 2.0 + 120);
            frames(ctx, "c13presets", 14);
            ctx.runOnClient(mc -> com.therabbits.client.Presets.apply(com.therabbits.client.Presets.BUILT_IN.get(1)));
            frames(ctx, "c13presets", 12);
            ctx.runOnClient(mc -> com.therabbits.client.Presets.apply(com.therabbits.client.Presets.BUILT_IN.get(0)));
            frames(ctx, "c13presets", 10);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "c14pvp", 14);
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));

            // 10. New features batch
            ctx.runOnClient(mc -> {
                mc.setScreen(null);
                ModuleManager.get(com.therabbits.client.module.hud.CustomTextModule.class).setEnabled(true);
                ModuleManager.get(com.therabbits.client.module.hud.InventoryHudModule.class).setEnabled(true);
                ModuleManager.get(com.therabbits.client.module.visual.HitboxesModule.class).setEnabled(true);
                var bo = ModuleManager.get(com.therabbits.client.module.visual.BlockOutlineModule.class);
                bo.setEnabled(true); bo.color.set(12); bo.width.set(5.0);
                var vm = ModuleManager.get(com.therabbits.client.module.visual.ViewModelModule.class);
                vm.setEnabled(true); vm.scale.set(0.7); vm.y.set(0.1);
                var ah = ModuleManager.get(com.therabbits.client.module.utility.AltHiderModule.class);
                ah.alias.set("TheRabbit"); ah.setEnabled(true);
            });
            // look down at the block in front so the outline shows, drop items for item physics
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 55f);
                for (int i = 0; i < 3; i++) {
                    net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(server.overworld(),
                            p.getX() + 1.2 + i * 0.6, p.getY() + 0.5, p.getZ() + 1.5, new ItemStack(i == 0 ? Items.DIAMOND : i == 1 ? Items.GOLDEN_APPLE : Items.ENDER_PEARL));
                    it.setNoPickUpDelay(); it.setPickUpDelay(32767);
                    server.overworld().addFreshEntity(it);
                }
            });
            sp.getServer().runOnServer(server -> zombieAhead(server.getPlayerList().getPlayers().get(0), false));
            frames(ctx, "c15new", 18);
            // waypoints: drop two and walk away
            ctx.runOnClient(mc -> ModuleManager.get(com.therabbits.client.module.hud.WaypointsModule.class).addHere());
            ctx.getInput().holdKey(o -> o.keyLeft);
            frames(ctx, "c16way", 10);
            ctx.getInput().releaseKey(o -> o.keyLeft);
            ctx.runOnClient(mc -> ModuleManager.get(com.therabbits.client.module.hud.WaypointsModule.class).addHere());
            ctx.getInput().holdKey(o -> o.keyDown);
            frames(ctx, "c16way", 12);
            ctx.getInput().releaseKey(o -> o.keyDown);
            // cooldowns: throw a pearl-style cooldown and swing
            sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).getCooldowns().addCooldown(new ItemStack(Items.ENDER_PEARL), 200));
            ctx.getInput().pressKey(o -> o.keyAttack);
            frames(ctx, "c17cool", 14);
            // tab list with Alt Hider
            ctx.getInput().holdKey(o -> o.keyPlayerList);
            frames(ctx, "c18alt", 10);
            ctx.getInput().releaseKey(o -> o.keyPlayerList);
            ctx.runOnClient(mc -> {
                ModuleManager.get(com.therabbits.client.module.visual.HitboxesModule.class).setEnabled(false);
                ModuleManager.get(com.therabbits.client.module.visual.ViewModelModule.class).setEnabled(false);
                ModuleManager.get(com.therabbits.client.module.hud.InventoryHudModule.class).setEnabled(false);
            });
            sp.getServer().runOnServer(server -> server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard()));
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));

            // 11. Capes: third person from behind, cycle through every cape
            ctx.runOnClient(mc -> {
                mc.setScreen(null);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                mc.options.hideGui = true;
            });
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 10f);
            });
            for (int c = 1; c < com.therabbits.client.cosmetics.Capes.IDS.length; c++) {
                final int cape = c;
                ctx.runOnClient(mc -> ModuleManager.get(com.therabbits.client.module.cosmetics.CapesModule.class).cape.set(cape));
                ctx.getInput().holdKey(o -> o.keyUp);
                frames(ctx, "c19cape", 10);
                ctx.getInput().releaseKey(o -> o.keyUp);
                frames(ctx, "c19cape", 8);
            }
            // 12. 3D cosmetics: front view, every hat, then claws
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                mc.options.fov().set(30);
            });
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.removeAllEffects();
                for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
                    p.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 12f);
            });
            frames(ctx, "skip", 0);
            for (int hat = 1; hat < com.therabbits.client.cosmetics.Wearables.HAT_IDS.length; hat++) {
                final int hh = hat;
                ctx.runOnClient(mc -> {
                    var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class);
                    wm.hat.set(hh); wm.arms.set(0);
                });
                for (int i = 0; i < 12; i++) {
                    final float yaw = i * 7.5f - 40f;
                    sp.getServer().runOnServer(server -> {
                        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, 12f);
                    });
                    frames(ctx, "c20hat" + hh, 1);
                }
            }
            ctx.runOnClient(mc -> {
                var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class);
                wm.hat.set(2); wm.arms.set(1);
            });
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), -25f, 12f);
            });
            frames(ctx, "c21claws", 6);
            for (int i = 0; i < 8; i++) {
                ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                frames(ctx, "c21claws", 2);
            }
            // 15. Right Shift hub, cosmetics locker, first-person claws
            ctx.runOnClient(mc -> { mc.options.hideGui = false; mc.setScreen(new com.therabbits.client.gui.HubScreen(null)); });
            frames(ctx, "c25hub", 3);
            ctx.runOnClient(mc -> mc.setScreen(new com.therabbits.client.gui.CosmeticsScreen(null)));
            frames(ctx, "c26locker", 3);
            ctx.runOnClient(mc -> { com.therabbits.client.cosmetics.CosmeticPreview.set("hat", "royal_crown"); com.therabbits.client.cosmetics.CosmeticPreview.set("aura", "snow_aura"); });
            frames(ctx, "c26tryon", 3);
            ctx.runOnClient(mc -> {
                mc.setScreen(null);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class).arms.set(1);
            });
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            });
            frames(ctx, "c27fpclaws", 3);
            for (int i = 0; i < 4; i++) {
                ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                frames(ctx, "c27fpclaws", 2);
            }
            ctx.runOnClient(mc -> mc.options.hideGui = true);
            // 16. Electric Aura (third person, front)
            ctx.runOnClient(mc -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class);
                wm.aura.set(1); wm.hat.set(4);
            });
            frames(ctx, "c28aura", 12);
            ctx.runOnClient(mc -> {
                var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class);
                wm.aura.set(2); wm.hat.set(0);
            });
            frames(ctx, "c29snow", 16);
            ctx.runOnClient(mc -> ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class).aura.set(0));
            // 17. Pack Customizer (mix tab + picker) and purple accent menus
            ctx.runOnClient(mc -> {
                mc.options.hideGui = false;
                ModuleManager.get(com.therabbits.client.module.utility.ClientSettingsModule.class).accent.set(0);
                try {   // a tiny test pack with a purple totem, picked in the mixer and applied
                    var dir = mc.getResourcePackDirectory().resolve("Test Purple Totem");
                    var tex = dir.resolve("assets/minecraft/textures/item/totem_of_undying.png");
                    java.nio.file.Files.createDirectories(tex.getParent());
                    java.nio.file.Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":75,\"description\":\"test\"}}");
                    var img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int x = 3; x < 13; x++) for (int y = 1; y < 15; y++) img.setRGB(x, y, 0xFF9B5CFF);
                    javax.imageio.ImageIO.write(img, "png", tex.toFile());
                    com.therabbits.client.pack.PackMixer.scan();
                    com.therabbits.client.pack.PackMixer.choose(com.therabbits.client.pack.PackMixer.CATEGORIES.get(0), "Test Purple Totem");
                    int files = com.therabbits.client.pack.PackMixer.build();
                    com.therabbits.client.RabbitsClient.LOG.info("SHOWCASE pack mixer copied {} files", files);
                    var repo = mc.getResourcePackRepository();
                    repo.reload();
                    var sel = new java.util.ArrayList<>(repo.getSelectedIds());
                    sel.add("file/" + com.therabbits.client.pack.PackMixer.MIX_NAME);
                    repo.setSelected(sel);
                    mc.options.updateResourcePacks(repo);
                } catch (Exception e) {
                    com.therabbits.client.RabbitsClient.LOG.warn("SHOWCASE pack mixer test failed", e);
                }
            });
            ctx.waitTicks(60);
            ctx.runOnClient(mc -> {
                mc.player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
                mc.setScreen(new com.therabbits.client.gui.PackCustomizerScreen(null));
            });
            frames(ctx, "c30packs", 3);
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "c31accent", 3);
            ctx.runOnClient(mc -> {
                ModuleManager.get(com.therabbits.client.module.utility.ClientSettingsModule.class).accent.set(0);
                mc.setScreen(null);
                mc.options.hideGui = true;
            });
            // 18. Emotes: wheel, then backflip / dance / floss in third person (front)
            ctx.runOnClient(mc -> { mc.options.hideGui = false; mc.setScreen(new com.therabbits.client.gui.EmoteWheelScreen()); });
            frames(ctx, "c32wheel", 2);
            ctx.runOnClient(mc -> { mc.setScreen(null); mc.options.hideGui = true;
                var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class); wm.aura.set(0); wm.hat.set(0); wm.arms.set(0); });
            for (String id : new String[]{"backflip", "dance", "floss", "dab", "spin", "sit"}) {
                ctx.runOnClient(mc -> com.therabbits.client.emote.EmoteManager.play(com.therabbits.client.emote.Emotes.byId(id)));
                for (int k = 0; k < 7; k++) { ctx.waitTicks(3); frames(ctx, "c33emote_" + id, 1); }
            }
            ctx.runOnClient(mc -> com.therabbits.client.emote.EmoteManager.stop());
            // 14. Account & Codes page
            ctx.runOnClient(mc -> {
                mc.options.hideGui = false;
                ModMenuScreen ms = new ModMenuScreen(null);
                mc.setScreen(ms);
                ms.openSettings(ModuleManager.get(com.therabbits.client.module.cosmetics.AccountModule.class));
            });
            frames(ctx, "c24account", 4);
            ctx.runOnClient(mc -> { mc.setScreen(null); mc.options.hideGui = true; });
            // 13. Neon Royal Shield skin: blocking in third person, then first person
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), -30f, 12f);
            });
            ctx.runOnClient(mc -> {
                var wm = ModuleManager.get(com.therabbits.client.module.cosmetics.WearablesModule.class);
                wm.shield.set(1); wm.arms.set(0); wm.hat.set(0);
            });
            frames(ctx, "c22shield", 8);
            ctx.getInput().holdKey(o -> o.keyUse);
            frames(ctx, "c22shield", 10);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            frames(ctx, "c23shieldfp", 6);
            ctx.getInput().holdKey(o -> o.keyUse);
            frames(ctx, "c23shieldfp", 8);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.runOnClient(mc -> {
                mc.options.fov().set(70);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                mc.setScreen(new ModMenuScreen(null));
            });

            // 9. HUD editor
            cursor(ctx, w / 2.0, h / 2.0);
            ctx.runOnClient(mc -> mc.setScreen(new HudEditorScreen(null)));
            frames(ctx, "c09editor", 18);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "c09editor", 4);
        }

        // ================= Start menu =================
        bigWindow(ctx);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        cursor(ctx, w - 5, h - 30);
        ctx.runOnClient(mc -> {
            ModuleManager.get(MenuBackgroundModule.class).style.set(0.0);
            mc.setScreen(new RabbitsTitleScreen());
        });
        frames(ctx, "a00title", 36);
        int panelX = (int) (w * 0.64) - 119 + 20;
        for (int i = 0; i < 40; i++) {
            cursor(ctx, panelX + 140, h * 0.36 + (h * 0.45) * i / 40.0);
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("a00title_%04d", shot++));
        }
        for (int s : new int[]{3, 10, 12, 14}) {
            final double style = s;
            ctx.runOnClient(mc -> ModuleManager.get(MenuBackgroundModule.class).style.set(style));
            frames(ctx, "a01bg", 12);
        }
        for (int i = 0; i < 4; i++) {
            ctx.runOnClient(mc -> com.therabbits.client.skin.SkinChanger.next());
            frames(ctx, "a02skin", 10);
        }
        ctx.runOnClient(mc -> {
            com.therabbits.client.skin.SkinChanger.reset();
            ModuleManager.get(MenuBackgroundModule.class).style.set(0.0);
        });
    }
}
