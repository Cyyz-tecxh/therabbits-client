package com.therabbits.client.module.hud;

import com.therabbits.client.setting.TextSetting;

/** Your own text on the HUD. Use {fps}, {ping}, {name}, {time} to insert live values. */
public class CustomTextModule extends TextHudModule {
    private final TextSetting text = add(new TextSetting("Text", "TheRabbits on top", 64));

    public CustomTextModule() { super("Custom Text", "Your own text on the HUD ({fps} {ping} {name} {time})", false, 300, 112); }

    @Override
    protected String text(boolean editing) {
        String t = text.get();
        if (t.contains("{fps}")) t = t.replace("{fps}", String.valueOf(mc.getFps()));
        if (t.contains("{name}")) t = t.replace("{name}", mc.getUser().getName());
        if (t.contains("{time}")) t = t.replace("{time}", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        if (t.contains("{ping}")) {
            int ping = 0;
            if (mc.player != null && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
            t = t.replace("{ping}", String.valueOf(ping));
        }
        return t.isEmpty() ? " " : t;
    }
}
