package com.therabbits.client.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import java.util.List;

/** All emotes: a pose for the body parts, and (optionally) a move for the whole body. */
public final class Emotes {
    private Emotes() {}

    private static final float PI = (float) Math.PI;

    public interface Pose { void apply(PlayerModel m, float t, float u); }          // t = seconds, u = 0..1 progress
    public interface Move { void apply(PoseStack p, float t, float u); }

    public record Emote(String id, String name, float seconds, boolean loops, Pose pose, Move move) {}

    private static float s(float v) { return Mth.sin(v); }
    private static float wave(float t, float speed) { return Mth.sin(t * speed); }
    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    private static void arms(PlayerModel m, float rx, float rz, float lx, float lz) {
        m.rightArm.xRot = rx; m.rightArm.zRot = rz; m.rightArm.yRot = 0;
        m.leftArm.xRot = lx; m.leftArm.zRot = lz; m.leftArm.yRot = 0;
    }

    public static final List<Emote> ALL = List.of(
            new Emote("backflip", "Backflip", 1.4f, false,
                    (m, t, u) -> {
                        float crouch = u < 0.18f ? u / 0.18f : u > 0.85f ? (1 - u) / 0.15f : 0;
                        float up = u > 0.18f && u < 0.85f ? 1 : 0;
                        arms(m, -2.6f * up + 0.4f * crouch, 0.2f, -2.6f * up + 0.4f * crouch, -0.2f);
                        m.rightLeg.xRot = -0.9f * up - 0.5f * crouch; m.leftLeg.xRot = -0.9f * up - 0.5f * crouch;
                        m.head.xRot = -0.3f * up;
                    },
                    (p, t, u) -> {
                        float f = Mth.clamp((u - 0.18f) / 0.67f, 0, 1);
                        float lift = s(f * PI) * 1.25f - (u < 0.18f ? 0.15f * u / 0.18f : 0);
                        p.translate(0, lift, 0);
                        p.translate(0, 0.9f, 0);
                        p.mulPose(Axis.XP.rotationDegrees(360f * ease(f)));
                        p.translate(0, -0.9f, 0);
                    }),
            new Emote("dance", "Dance", 6f, true,
                    (m, t, u) -> {
                        float b = wave(t, 7f);
                        arms(m, -1.2f + 0.9f * b, 0.5f + 0.3f * b, -1.2f - 0.9f * b, -0.5f + 0.3f * b);
                        m.body.yRot = 0.35f * wave(t, 3.5f);
                        m.head.yRot = 0.35f * wave(t, 3.5f); m.head.xRot = 0.18f * Math.abs(b);
                        m.rightLeg.xRot = 0.45f * Math.max(0, b); m.leftLeg.xRot = 0.45f * Math.max(0, -b);
                    },
                    (p, t, u) -> p.translate(0, 0.08f * Math.abs(wave(t, 7f)), 0)),
            new Emote("floss", "Floss", 5f, true,
                    (m, t, u) -> {
                        float b = wave(t, 9f);
                        arms(m, 0.25f * (b > 0 ? 1 : -1), 0.55f * b + 0.15f, 0.25f * (b > 0 ? 1 : -1), 0.55f * b - 0.15f);
                        m.body.zRot = -0.12f * b;
                        m.rightLeg.zRot = 0.1f * b; m.leftLeg.zRot = 0.1f * b;
                    },
                    (p, t, u) -> p.translate(0.06f * wave(t, 9f), 0, 0)),
            new Emote("wave", "Wave", 2.5f, false,
                    (m, t, u) -> { m.rightArm.xRot = -2.9f; m.rightArm.zRot = 0.35f + 0.45f * wave(t, 10f); m.head.zRot = 0.08f * wave(t, 5f); },
                    null),
            new Emote("dab", "Dab", 1.6f, false,
                    (m, t, u) -> {
                        float k = ease(Math.min(1, u * 4));
                        arms(m, -1.9f * k, 1.2f * k, -1.3f * k, 0.9f * k);
                        m.head.xRot = 0.7f * k; m.head.yRot = 0.6f * k;
                    },
                    null),
            new Emote("spin", "Spin", 2f, false,
                    (m, t, u) -> arms(m, 0, 1.45f, 0, -1.45f),
                    (p, t, u) -> p.mulPose(Axis.YP.rotationDegrees(720f * ease(u)))),
            new Emote("clap", "Clap", 2.5f, false,
                    (m, t, u) -> {
                        float c = 0.25f + 0.25f * (1 + wave(t, 16f));
                        m.rightArm.xRot = -1.35f; m.leftArm.xRot = -1.35f;
                        m.rightArm.yRot = -c; m.leftArm.yRot = c;
                    },
                    null),
            new Emote("salute", "Salute", 2f, false,
                    (m, t, u) -> { m.rightArm.xRot = -2.3f; m.rightArm.yRot = -0.6f; m.rightArm.zRot = 0.5f; },
                    null),
            new Emote("tpose", "T-Pose", 3f, false,
                    (m, t, u) -> { arms(m, 0, 1.57f, 0, -1.57f); m.rightLeg.xRot = 0; m.leftLeg.xRot = 0; },
                    null),
            new Emote("sit", "Sit", 8f, true,
                    (m, t, u) -> {
                        m.rightLeg.xRot = -1.45f; m.leftLeg.xRot = -1.45f; m.rightLeg.yRot = 0.15f; m.leftLeg.yRot = -0.15f;
                        arms(m, -0.6f, 0.1f, -0.6f, -0.1f);
                        m.head.xRot = 0.1f * wave(t, 1.2f);
                    },
                    (p, t, u) -> p.translate(0, -0.62f, 0)),
            new Emote("zombie", "Zombie", 4f, true,
                    (m, t, u) -> {
                        arms(m, -1.55f + 0.08f * wave(t, 4f), 0.05f, -1.55f - 0.08f * wave(t, 4f), -0.05f);
                        m.head.zRot = 0.25f; m.head.xRot = 0.2f;
                        m.rightLeg.xRot = 0.35f * wave(t, 4f); m.leftLeg.xRot = -0.35f * wave(t, 4f);
                    },
                    (p, t, u) -> p.mulPose(Axis.ZP.rotationDegrees(4f * wave(t, 2f)))),
            new Emote("celebrate", "Celebrate", 3f, false,
                    (m, t, u) -> {
                        float j = Math.abs(wave(t, 6f));
                        arms(m, -2.8f, 0.45f + 0.2f * j, -2.8f, -0.45f - 0.2f * j);
                        m.rightLeg.xRot = -0.3f * j; m.leftLeg.xRot = -0.3f * j;
                        m.head.xRot = -0.25f;
                    },
                    (p, t, u) -> p.translate(0, 0.45f * Math.abs(wave(t, 6f)), 0))
    );

    public static Emote byId(String id) {
        for (Emote e : ALL) if (e.id().equals(id)) return e;
        return null;
    }
}
