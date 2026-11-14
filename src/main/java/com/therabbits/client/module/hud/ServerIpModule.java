package com.therabbits.client.module.hud;

public class ServerIpModule extends TextHudModule {
    public ServerIpModule() { super("Server IP", "Current server", false, 300, 22); }

    @Override
    protected String text(boolean editing) {
        var server = mc.getCurrentServer();
        return server == null ? "Singleplayer" : server.ip;
    }
}
