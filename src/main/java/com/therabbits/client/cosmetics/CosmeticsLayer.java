package com.therabbits.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.module.cosmetics.WearablesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Draws TheRabbits hats and arm pieces on players (yours, and other TheRabbits players'). */
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
        if (s.isInvisible || s.isSpectator) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(s.id);
        if (!(e instanceof Player p)) return;
        CosmeticModel hat = CosmeticModel.get(WearablesModule.hatFor(p));
        CosmeticModel arms = CosmeticModel.get(WearablesModule.armsFor(p));
        CosmeticModel aura = CosmeticModel.get(WearablesModule.auraFor(p));
        float now = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        if (aura != null && "aura".equals(aura.attach)) {
            if (aura.root.hasChild("f0") || aura.root.hasChild("ring0") || aura.root.hasChild("p0")) drawAura(pose, out, aura);
            else { animateAura(aura.root, now, p); draw(pose, out, light, aura, aura.root); }
        }
        CosmeticModel pet = CosmeticModel.get(WearablesModule.petFor(p));
        if (pet != null && "pet".equals(pet.attach)) { animatePet(pet.root, now); draw(pose, out, light, pet, pet.root); }
        PlayerModel model = getParentModel();
        if (hat != null && !hat.arms) {
            pose.pushPose();
            model.head.translateAndRotate(pose);
            draw(pose, out, light, hat, hat.root);
            pose.popPose();
        }
        if (arms != null && arms.arms) {
            pose.pushPose();
            model.rightArm.translateAndRotate(pose);
            animateClaws(arms.root, s.attackTime);
            animateClaws(arms.mirrored, s.attackTime);
            draw(pose, out, light, arms, arms.root);
            pose.popPose();
            pose.pushPose();
            model.leftArm.translateAndRotate(pose);
            draw(pose, out, light, arms, arms.mirrored);
            pose.popPose();
        }
    }

    /**
     * Claws: the blades flex slowly on their own while idle, and snap forward (then hook back) when you attack.
     * attackTime is the swing progress 0..1 (0 when not swinging). Models without blades are left alone.
     */
    public static void animateClaws(ModelPart root, float attackTime) {
        if (!root.hasChild("blade0")) return;
        float t = (System.currentTimeMillis() % 600000L) / 1000f;
        float atk = attackTime > 0 ? net.minecraft.util.Mth.sin(attackTime * (float) Math.PI) : 0f;
        float rad = (float) (Math.PI / 180);
        for (int i = 0; root.hasChild("blade" + i); i++) {
            ModelPart a = root.getChild("blade" + i);
            ModelPart b = a.getChild("blade" + i + "b");
            ModelPart c = b.getChild("blade" + i + "c");
            float ph = t * 2.2f + i * 0.9f;
            var ia = a.getInitialPose(); var ib = b.getInitialPose(); var ic = c.getInitialPose();
            a.xRot = ia.xRot() + (3.5f * net.minecraft.util.Mth.sin(ph) - 22f * atk) * rad;
            a.y = ia.y() + 0.9f * atk;
            b.xRot = ib.xRot() + (4.0f * net.minecraft.util.Mth.sin(ph + 0.8f) - 14f * atk) * rad;
            c.xRot = ic.xRot() + (6.0f * net.minecraft.util.Mth.sin(ph + 1.6f) + 10f * atk) * rad;
        }
    }

    // ------------------------------------------------------------------ helpers
    private static float rad(float d) { return d * (float) (Math.PI / 180); }
    private static float sin(float a) { return net.minecraft.util.Mth.sin(a); }
    private static float cos(float a) { return net.minecraft.util.Mth.cos(a); }

    /** Points a part's -z axis from a toward b (yaw + pitch), like the generator's look(). */
    private static void look(ModelPart part, float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-4f) l = 1;
        part.yRot = (float) Math.atan2(-dx, -dz);
        part.xRot = (float) Math.asin(Math.max(-1f, Math.min(1f, dy / l)));
        part.zRot = 0;
    }

    private static void place(ModelPart part, float[] at) { part.x = at[0]; part.y = at[1]; part.z = at[2]; }

    private static void scale(ModelPart part, float k) {
        part.xScale = part.yScale = part.zScale = Math.max(0f, k);
        part.visible = k > 0.02f;
    }

    // ------------------------------------------------------------------ auras (Clockwork, Petal Storm, Koi)
    private static final java.util.Map<Integer, float[]> CLOCK_PHASE = new java.util.HashMap<>();

    private static void animateAura(ModelPart root, float t, Player p) {
        if (root.hasChild("clock")) animateClockwork(root, t, p);
        else if (root.hasChild("pt0")) animatePetals(root, t);
        else if (root.hasChild("ka0")) animateKoi(root, t);
    }

    /** Gears and the clock orbit you; everything ticks faster while you sprint. */
    private static void animateClockwork(ModelPart root, float t, Player p) {
        float[] st = CLOCK_PHASE.computeIfAbsent(p.getId(), k -> new float[]{t, 0});
        float dt = Math.max(0f, Math.min(0.25f, t - st[0]));
        st[0] = t;
        st[1] += dt * (p.isSprinting() ? 2.4f : 1f);
        float ph = st[1];
        float a = ph * 0.45f, c = cos(a), sn = sin(a);
        for (int i = 0; ; i++) {
            String n = i < 4 ? "g" + i : i == 4 ? "clock" : "ch" + (i - 5);
            if (!root.hasChild(n)) { if (i < 5) continue; break; }
            ModelPart part = root.getChild(n);
            var ip = part.getInitialPose();
            part.x = ip.x() * c + ip.z() * sn;
            part.z = -ip.x() * sn + ip.z() * c;
            part.yRot = ip.yRot() + a;
            if (i < 4) part.zRot = ip.zRot() + ph * 1.6f * (i % 2 == 0 ? 1 : -1);
            if (i == 4) part.y = ip.y() + sin(ph * 1.3f) * 0.6f;
        }
    }

    /** Sakura petals drift down around you while slowly circling, fluttering as they fall. */
    private static void animatePetals(ModelPart root, float t) {
        for (int i = 0; root.hasChild("pt" + i); i++) {
            ModelPart part = root.getChild("pt" + i);
            var ip = part.getInitialPose();
            float h = (i * 0.6180339f) % 1f;
            float a = t * (0.35f + 0.3f * h), c = cos(a), sn = sin(a);
            part.x = ip.x() * c + ip.z() * sn;
            part.z = -ip.x() * sn + ip.z() * c;
            float y = ((ip.y() + 6f + t * (1.5f + 1.5f * h)) % 30f + 30f) % 30f - 6f;
            part.y = y;
            part.xRot = ip.xRot() + t * (1.1f + h);
            part.yRot = ip.yRot() + t * (0.7f + h * 0.8f);
            part.zRot = ip.zRot() + sin(t * 2f + i) * 0.6f;
            scale(part, Math.min(1f, Math.min((y + 6f) / 3f, (24f - y) / 3f)));
        }
    }

    private static float[] koiPath(float s, int k) {
        return new float[]{sin(s) * 13.5f, 7f + 3f * sin(s * 2f + 1f) - 4f * k + 2f, sin(2f * s) * 9.5f};
    }

    /** Two koi swim figure-eights around you, tails swishing; bubbles rise. */
    private static void animateKoi(ModelPart root, float t) {
        String[] fish = {"a", "b"};
        for (int k = 0; k < 2; k++) {
            String f = fish[k];
            int n = 0;
            while (root.hasChild("k" + f + n)) n++;
            if (n == 0) continue;
            float s0 = t * 0.55f + k * (float) Math.PI;
            float[] prev = koiPath(s0 + 0.1f, k);
            for (int i = 0; i < n; i++) {
                float[] pt = koiPath(s0 - i * 0.1f, k);
                ModelPart seg = root.getChild("k" + f + i);
                place(seg, pt);
                look(seg, pt, prev);
                seg.yRot += rad(9f * sin(t * 5f - i * 0.7f) * i / n);
                prev = pt;
            }
            ModelPart tailSeg = root.getChild("k" + f + (n - 1));
            for (int j = 0; j < 2; j++)
                if (tailSeg.hasChild("k" + f + "tail" + j)) tailSeg.getChild("k" + f + "tail" + j).yRot = rad(20f * sin(t * 5f - 7f));
        }
        for (int i = 0; root.hasChild("kbub" + i); i++) {
            ModelPart b = root.getChild("kbub" + i);
            float life = (t * 0.3f + i / 10f) % 1f, a = i * 2.3f, r = 10f + (i % 3) * 2f;
            b.x = cos(a) * r; b.y = 20f - life * 26f; b.z = sin(a) * r;
            scale(b, 0.6f + 0.6f * (1f - life));
        }
    }

    // ------------------------------------------------------------------ pets
    private static void animatePet(ModelPart root, float t) {
        if (root.hasChild("d_head")) animateDragonPet(root, t);
        else if (root.hasChild("bb")) animateBunny(root, t);
    }

    private static float[] petPath(float s, float i, float t) {
        float ii = Math.max(i, -1.6f), r = 4.2f + 0.08f * ii;
        return new float[]{17f + r * cos(s * 1.7f), -4f + ii * 0.75f + 1.5f * sin(t * 1.2f), 8f + r * sin(s * 1.7f)};
    }

    /** Dragon companion: hovers in a slow coil beside you, head up, whiskers and mane swaying, jaw opening now and then. */
    private static void animateDragonPet(ModelPart root, float t) {
        float th0 = t * 0.85f + 1f, d = 0.19f;
        float[] hp = petPath(th0 + d * 1.6f, -1f, t), prev = hp, first = null;
        for (int i = 0; root.hasChild("d_s" + i); i++) {
            float[] pt = petPath(th0 - i * d, i, t);
            ModelPart seg = root.getChild("d_s" + i);
            place(seg, pt);
            look(seg, pt, prev);
            seg.zRot = rad(12f * sin(t * 1.5f - i * 0.3f));
            if (first == null) first = pt;
            prev = pt;
        }
        ModelPart head = root.getChild("d_head");
        place(head, hp);
        if (first != null) look(head, first, hp);
        float tau = ((t + 3f) % 6.5f) / 6.5f;
        float jaw = tau < 0.5f ? Math.max(0f, sin(tau / 0.5f * (float) Math.PI)) : 0f;
        if (head.hasChild("d_jaw")) { ModelPart j = head.getChild("d_jaw"); j.xRot = j.getInitialPose().xRot() + rad(35f * jaw); }
        for (String side : new String[]{"l", "r"}) {
            float sx = side.equals("l") ? -1 : 1;
            if (!head.hasChild("d_wh" + side)) continue;
            ModelPart w1 = head.getChild("d_wh" + side);
            w1.xRot = w1.getInitialPose().xRot() + rad(12f * sin(t * 2.4f + sx));
            if (w1.hasChild("d_wh2" + side)) {
                ModelPart w2 = w1.getChild("d_wh2" + side);
                w2.xRot = w2.getInitialPose().xRot() + rad(18f * sin(t * 2.4f + 0.7f + sx));
                if (w2.hasChild("d_wh3" + side)) {
                    ModelPart w3 = w2.getChild("d_wh3" + side);
                    w3.xRot = w3.getInitialPose().xRot() + rad(22f * sin(t * 2.4f + 1.4f + sx));
                }
            }
        }
        for (int j = 0; head.hasChild("d_mane" + j); j++) {
            ModelPart m = head.getChild("d_mane" + j);
            m.xRot = m.getInitialPose().xRot() + rad(10f * sin(t * 3f + j));
        }
    }

    /** Spirit Bunny: hops beside you, ears bouncing, sparkles twinkling. */
    private static void animateBunny(ModelPart root, float t) {
        ModelPart bb = root.getChild("bb");
        var ip = bb.getInitialPose();
        float hop = Math.abs(sin(t * 3.2f));
        bb.y = ip.y() - hop * 2.6f;
        bb.x = ip.x() + sin(t * 0.6f) * 1.5f;
        bb.xRot = ip.xRot() + rad(10f * cos(t * 3.2f) * (sin(t * 3.2f) >= 0 ? 1 : -1));
        if (bb.hasChild("bh")) {
            ModelPart head = bb.getChild("bh");
            for (String e : new String[]{"be-1", "be1"})
                if (head.hasChild(e)) { ModelPart ear = head.getChild(e); ear.xRot = ear.getInitialPose().xRot() + rad(14f * hop); }
        }
        for (int i = 0; root.hasChild("bsp" + i); i++) {
            ModelPart sp = root.getChild("bsp" + i);
            var sp0 = sp.getInitialPose();
            sp.y = sp0.y() + sin(t * 1.7f + i) * 1.2f;
            scale(sp, 0.5f + 0.5f * Math.abs(sin(t * 2.5f + i * 1.3f)));
        }
    }

    /** Snow Aura: each flake drifts slowly around you, bobs, turns, and fades in and out on its own timer. */
    private static void animateSnow(ModelPart root, float t) {
        for (int i = 0; root.hasChild("f" + i); i++) {
            ModelPart f = root.getChild("f" + i);
            var ip = f.getInitialPose();
            float h = (i * 0.6180339f) % 1f;                    // per-flake variety
            float drift = t * (0.10f + h * 0.12f) * (i % 2 == 0 ? 1 : -1);
            float c = net.minecraft.util.Mth.cos(drift), sn = net.minecraft.util.Mth.sin(drift);
            f.x = ip.x() * c - ip.z() * sn;
            f.z = ip.x() * sn + ip.z() * c;
            f.y = ip.y() + net.minecraft.util.Mth.sin(t * (0.7f + h * 0.6f) + i * 1.3f) * (0.5f + h * 0.7f);
            f.yRot = ip.yRot() + t * (0.4f + h * 0.5f);
            f.zRot = t * (0.25f + h * 0.3f) * (i % 3 == 0 ? -1 : 1);
            // life: fade in, stay, fade out, short gap; plus a gentle flicker
            float life = (t * (0.11f + h * 0.08f) + h * 7.3f) % 1f;
            float a = life < 0.15f ? life / 0.15f : life < 0.75f ? 1f : life < 0.9f ? (0.9f - life) / 0.15f : 0f;
            a *= 0.85f + 0.15f * net.minecraft.util.Mth.sin(t * 9f + i * 2.1f);
            float size = (1.0f + h * 0.8f) * Math.max(0f, a);
            f.visible = size > 0.04f;
            f.xScale = f.yScale = f.zScale = size;
        }
    }

    /** Swirling rings (each spins its own way), crackling segments and rising sparks. */
    private static void drawAura(PoseStack pose, SubmitNodeCollector out, CosmeticModel m) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        ModelPart root = m.root;
        if (root.hasChild("f0")) {
            animateSnow(root, t);
            out.submitModelPart(root, pose, RenderTypes.eyes(m.currentTexture()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            return;
        }
        float[] speed = {2.4f, -1.8f, 3.0f};
        for (int k = 0; k < 3; k++) {
            if (!root.hasChild("ring" + k)) continue;
            ModelPart ring = root.getChild("ring" + k);
            var ip = ring.getInitialPose();
            ring.yRot = ip.yRot() + t * speed[k];
            ring.y = ip.y() + net.minecraft.util.Mth.sin(t * 2.1f + k * 1.7f) * 0.7f;
            int idx = 0;
            for (ModelPart seg : ring.getAllParts()) {
                if (seg == ring) continue;
                seg.visible = ((int) (t * 14) * 31 + idx * 17 + k * 7) % 6 != 0;   // crackle
                idx++;
            }
        }
        for (int i = 0; i < 16; i++) {
            if (!root.hasChild("p" + i)) continue;
            ModelPart p = root.getChild("p" + i);
            var ip = p.getInitialPose();
            float u = (t * 0.32f + i * 0.0625f) % 1f;
            p.y = ip.y() - u * 30f;
            p.x = ip.x() + net.minecraft.util.Mth.sin(t * 1.3f + i) * 0.8f;
            p.xScale = p.yScale = p.zScale = 1f - u * 0.7f;
        }
        out.submitModelPart(root, pose, RenderTypes.eyes(m.currentTexture()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }

    public static void draw(PoseStack pose, SubmitNodeCollector out, int light, CosmeticModel m, ModelPart part) {
        out.submitModelPart(part, pose, RenderTypes.entityCutoutNoCull(m.currentTexture()), light, OverlayTexture.NO_OVERLAY, null);
        Identifier g = m.currentGlow();
        if (g != null)
            out.submitModelPart(part, pose, RenderTypes.eyes(g), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }
}
