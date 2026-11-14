package com.therabbits.client.module.utility;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** General client options: menu animations, F3 behaviour, GUI scale. */
public class ClientSettingsModule extends Module {
    private static ClientSettingsModule instance;

    public final ModeSetting accent = add(new ModeSetting("Menu Colour", 0, false, com.therabbits.client.gui.Theme.ACCENT_NAMES));
    public final BoolSetting animations = add(new BoolSetting("Menu Animations", true));
    public final BoolSetting hideHudInF3 = add(new BoolSetting("Hide HUD When F3 Is Open", true));
    public final ModeSetting f3Style = add(new ModeSetting("F3 Screen", 0, false, "Vanilla", "Compact"));
    public final ModeSetting guiScale = add(new ModeSetting("GUI Scale", 0, false, "Auto", "1", "2", "3", "4", "5", "6"));
    private int appliedScale = -1;

    public ClientSettingsModule() {
        super("Client Settings", "Colours, F3, GUI scale", Category.UTILITY, true);
        instance = this;
    }

    public static ClientSettingsModule get() { return instance; }

    public static boolean animations() { return instance == null || !instance.enabled() || instance.animations.on(); }

    public static boolean compactF3() { return instance != null && instance.enabled() && instance.f3Style.get() == 1; }

    @Override
    public void onTick() {
        // GUI scale: apply when you change it here; changing it in vanilla Options updates this too
        int vanilla = mc.options.guiScale().get();
        if (appliedScale == -1) { appliedScale = vanilla; if (vanilla <= 6) guiScale.set(vanilla); return; }
        int want = guiScale.get();
        if (vanilla != appliedScale && vanilla <= 6) { appliedScale = vanilla; guiScale.set(vanilla); return; }
        if (want != vanilla) { mc.options.guiScale().set(want); appliedScale = want; mc.options.save(); }
    }

    /** Small black & white F3: FPS, position, facing, biome, light and target block. */
    public void drawCompactF3(GuiGraphics g) {
        var p = mc.player;
        if (p == null || mc.level == null) return;
        BlockPos pos = p.blockPosition();
        String facing = p.getDirection().getName();
        String biome = mc.level.getBiome(pos).getRegisteredName().replace("minecraft:", "").replace('_', ' ');
        String[] lines = {
                "TheRabbits  " + mc.getFps() + " fps",
                String.format("XYZ  %.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ()),
                "Block  " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "   Chunk " + (pos.getX() >> 4) + " " + (pos.getZ() >> 4),
                String.format("Facing  %s (%.1f / %.1f)", facing, net.minecraft.util.Mth.wrapDegrees(p.getYRot()), p.getXRot()),
                "Biome  " + biome,
                "Light  " + mc.level.getMaxLocalRawBrightness(pos) + "   Day " + (mc.level.getDayTime() / 24000L),
                mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                        ? "Looking at  " + b.getBlockPos().getX() + " " + b.getBlockPos().getY() + " " + b.getBlockPos().getZ() : "Looking at  -",
        };
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        g.fill(4, 4, 4 + w + 10, 4 + lines.length * 11 + 6, 0xA0000000);
        for (int i = 0; i < lines.length; i++) Draw.text(g, lines[i], 9, 8 + i * 11, i == 0 ? 0xFFFFFFFF : 0xFFD8D8D8);
    }
}
