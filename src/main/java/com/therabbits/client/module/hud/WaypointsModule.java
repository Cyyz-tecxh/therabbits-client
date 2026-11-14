package com.therabbits.client.module.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.ActionSetting;
import com.therabbits.client.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Press B to drop a waypoint where you stand. The HUD lists the nearest ones with distance
 * and an arrow pointing to them. Waypoints are saved per world/server and dimension.
 */
public class WaypointsModule extends HudModule {
    public record Waypoint(String name, int x, int y, int z) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("therabbits/waypoints.json");
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final NumberSetting shown = add(new NumberSetting("Show Nearest", 4, 1, 10, 1));
    private Map<String, List<Waypoint>> all;

    public WaypointsModule() {
        super("Waypoints", "Press B to mark a spot; arrows guide you back", true, 220, 4);
        add(new ActionSetting("Waypoints Here", "Delete All", () -> { list().clear(); save(); }));
    }

    private String key() {
        String world = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName() : "unknown";
        String dim = mc.level != null ? mc.level.dimension().toString() : "";
        return world + "|" + dim;
    }

    private List<Waypoint> list() {
        if (all == null) {
            try {
                all = Files.exists(FILE) ? GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, List<Waypoint>>>() {}.getType()) : null;
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Could not read waypoints", e);
            }
            if (all == null) all = new HashMap<>();
        }
        return all.computeIfAbsent(key(), k -> new ArrayList<>());
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(all));
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Could not save waypoints", e);
        }
    }

    /** Called by the B key. */
    public void addHere() {
        if (mc.player == null) return;
        List<Waypoint> l = list();
        l.add(new Waypoint("Waypoint " + (l.size() + 1), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
        save();
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§fWaypoint " + l.size() + " §7set at "
                + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ()), true);
    }

    private List<String[]> rows(boolean editing) {
        List<String[]> out = new ArrayList<>();
        if (mc.player == null) {
            if (editing) { out.add(new String[]{"↑", "Base", "120m"}); out.add(new String[]{"↗", "Portal", "48m"}); }
            return out;
        }
        double px = mc.player.getX(), pz = mc.player.getZ();
        List<Waypoint> sorted = new ArrayList<>(list());
        sorted.sort(Comparator.comparingDouble(w -> Math.hypot(w.x() + 0.5 - px, w.z() + 0.5 - pz)));
        for (Waypoint w : sorted.subList(0, Math.min(sorted.size(), shown.i()))) {
            double dx = w.x() + 0.5 - px, dz = w.z() + 0.5 - pz;
            double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(w.y() - mc.player.getY(), 2));
            float target = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
            float rel = Mth.wrapDegrees(target - mc.player.getYRot());
            int idx = Math.floorMod(Math.round(rel / 45f), 8);
            out.add(new String[]{ARROWS[idx], w.name(), Math.round(dist) + "m"});
        }
        if (out.isEmpty() && editing) out.add(new String[]{"↑", "Press B to add", ""});
        return out;
    }

    @Override public int width() { return 120; }
    @Override public int height() { return Math.max(12, rows(true).size() * 12); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        int y = 0;
        for (String[] r : rows(editing)) {
            if ((HudStyle.bg() >>> 24) > 0) Draw.rect(g, 0, y, width(), 11, HudStyle.bg());
            HudStyle.text(g, r[0], 3, y + 2, HudStyle.text(y / 60f));
            HudStyle.text(g, r[1], 14, y + 2, HudStyle.text(y / 60f));
            HudStyle.text(g, r[2], width() - 3 - Draw.font().width(r[2]), y + 2, HudStyle.dim());
            y += 12;
        }
    }
}
