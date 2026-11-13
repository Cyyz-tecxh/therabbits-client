package com.therabbits.client.module.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends TextHudModule {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm");

    public ClockModule() { super("Clock", "Shows the real time", false, 300, 4); }

    @Override protected String text(boolean editing) { return LocalTime.now().format(F); }
}
