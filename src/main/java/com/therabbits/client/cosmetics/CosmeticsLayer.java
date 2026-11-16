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

    /** Where cosmetic geometry goes: the world's node collector, or a GUI buffer for the preview player. */
    @FunctionalInterface
    public interface Sink { void part(ModelPart part, PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type, int light); }

    public static Sink of(SubmitNodeCollector out) {
        return (pt, ps, rt, l) -> out.submitModelPart(pt, ps, rt, l, OverlayTexture.NO_OVERLAY, null);
    }

    public static Sink of(net.minecraft.client.renderer.MultiBufferSource buffers) {
        return (pt, ps, rt, l) -> pt.render(ps, buffers.getBuffer(rt), l, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
        if (s.isInvisible || s.isSpectator) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(s.id);
        if (!(e instanceof Player p)) return;
        drawAll(pose, of(out), light, getParentModel(), p, s.attackTime,
                WearablesModule.hatFor(p), WearablesModule.armsFor(p), WearablesModule.auraFor(p), WearablesModule.petFor(p));
    }

    /**
     * Draws every cosmetic slot in the player model's space. {@code p} may be null (the title-screen preview,
     * where there is no player entity); then the looks that follow the player just use their defaults.
     */
    public static void drawAll(PoseStack pose, Sink out, int light, PlayerModel model, Player p, float attackTime,
                               String hatId, String armsId, String auraId, String petId) {
        CosmeticModel hat = CosmeticModel.get(hatId);
        CosmeticModel arms = CosmeticModel.get(armsId);
        CosmeticModel aura = CosmeticModel.get(auraId);
        float now = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        if (aura != null && "aura".equals(aura.attach)) {
            float k = WearablesModule.auraSize();
            ModelPart root = aura.root;
            boolean chain = root.hasChild("d_head") || root.hasChild("ka0");   // dragons and koi: grow as a whole so bodies stay connected
            pose.pushPose();
            if (chain || root.hasChild("ring0")) {
                pose.translate(0, 12f / 16f, 0);
                if (chain) pose.scale(k, k, k); else pose.scale(k, 1f, k);
                pose.translate(0, -12f / 16f, 0);
            }
            if (root.hasChild("cc0")) {   // Candy Cane aura: canes pop up at random spots
                animateCandy(root, now, k);
                draw(pose, out, light, aura, root);
            } else if (root.hasChild("g0")) {   // Glyph Aura: flickering glyphs, flat white/grey (no glow)
                animateGlyphs(root, now, cardFacing(p), k);
                draw(pose, out, FULL_BRIGHT, aura, root);
            } else if (root.hasChild("f0") || root.hasChild("ring0") || root.hasChild("p0")) {
                if (root.hasChild("f0")) { drawAuraSpread(pose, out, aura, k, cardFacing(p)); }
                else drawAura(pose, out, aura);
            } else {
                animateAura(aura, now, p);
                if (!chain) spread(root, k);
                draw(pose, out, light, aura, root);
            }
            pose.popPose();
        }
        CosmeticModel pet = CosmeticModel.get(petId);
        if (pet != null && "pet".equals(pet.attach)) {
            animatePet(pet.root, now);
            if (pet.root.hasChild("d_head")) {
                // dragon pets are pocket-sized: shrink the coil around its centre and float it by your shoulder
                pose.pushPose();
                pose.translate(13f / 16f, -6f / 16f, -1f / 16f);
                pose.scale(0.4f, 0.4f, 0.4f);
                pose.translate(-17f / 16f, -6f / 16f, -8f / 16f);
                draw(pose, out, light, pet, pet.root);
                pose.popPose();
            } else draw(pose, out, light, pet, pet.root);
        }
        if (hat != null && !hat.arms) {
            pose.pushPose();
            model.head.translateAndRotate(pose);
            draw(pose, out, light, hat, hat.root);
            pose.popPose();
        }
        if (arms != null && arms.arms) {
            pose.pushPose();
            model.rightArm.translateAndRotate(pose);
            animateClaws(arms.root, attackTime);
            animateClaws(arms.mirrored, attackTime);
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

    /** Push loose parts (cards, petals, gears, sparkles, light trails) further out from you; their size stays the same. */
    private static void spread(ModelPart root, float k) {
        if (k == 1f) return;
        for (int i = 0; ; i++) {
            boolean any = false;
            for (String pre : new String[]{"c", "s", "pt", "g", "ch", "kbub"}) {
                if (root.hasChild(pre + i)) { ModelPart q = root.getChild(pre + i); q.x *= k; q.z *= k; any = true; }
            }
            if (!any && i > 40) break;
        }
        if (root.hasChild("clock")) { ModelPart c = root.getChild("clock"); c.x *= k; c.z *= k; }
        for (int r = 0; root.hasChild("t" + r); r++) {
            ModelPart ring = root.getChild("t" + r);
            for (int j = 0; ring.hasChild("t" + r + "_" + j); j++) {
                ModelPart q = ring.getChild("t" + r + "_" + j);
                q.x *= k; q.z *= k; q.xScale *= k;
            }
        }
    }

    private static void drawAuraSpread(PoseStack pose, Sink out, CosmeticModel m, float k, float facing) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        animateSnow(m.root, t, facing);
        float spread = 1f + (k - 1f) * 0.5f;   // snow hugs the body; Aura Size only nudges it outward
        for (int i = 0; m.root.hasChild("f" + i); i++) { ModelPart f = m.root.getChild("f" + i); f.x *= spread; f.z *= spread; }
        draw(pose, out, FULL_BRIGHT, m, m.root);   // solid crystals (readable on white skins), faint glow on the core
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

    private static void animateAura(CosmeticModel m, float t, Player p) {
        ModelPart root = m.root;
        if (root.hasChild("d_head")) animateDragon(root, t, dragonStyle(m.id));
        else if (root.hasChild("c0") && root.hasChild("t0")) animateCards(root, t, cardFacing(p));
        else if (root.hasChild("clock")) animateClockwork(root, t, p);
        else if (root.hasChild("pt0")) animatePetals(root, t);
        else if (root.hasChild("ka0")) animateKoi(root, t);
    }

    /** Gears and the clock orbit you; everything ticks faster while you sprint. */
    private static void animateClockwork(ModelPart root, float t, Player p) {
        float[] st = CLOCK_PHASE.computeIfAbsent(p == null ? -1 : p.getId(), k -> new float[]{t, 0});
        float dt = Math.max(0f, Math.min(0.25f, t - st[0]));
        st[0] = t;
        st[1] += dt * (p != null && p.isSprinting() ? 2.4f : 1f);
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
        return new float[]{sin(s) * 13.5f, 7f + 3f * sin(s * 2f + 1f) - 4.5f * k + 2f, sin(2f * s) * 9.5f};
    }

    /** Two koi swim figure-eights around you, tails swishing; bubbles rise. */
    private static void animateKoi(ModelPart root, float t) {
        String[] fish = {"a", "b"};
        for (int k = 0; k < fish.length; k++) {
            String f = fish[k];
            int n = 0;
            while (root.hasChild("k" + f + n)) n++;
            if (n == 0) continue;
            float s0 = t * 0.55f + k * (float) Math.PI;
            float[] prev = koiPath(s0 + 0.085f, k);
            for (int i = 0; i < n; i++) {
                float[] pt = koiPath(s0 - i * 0.085f, k);
                ModelPart seg = root.getChild("k" + f + i);
                place(seg, pt);
                look(seg, pt, prev);
                seg.yRot += rad(10f * sin(t * 5f - i * 0.6f) * i / n);
                prev = pt;
            }
            ModelPart tailSeg = root.getChild("k" + f + (n - 1));
            for (int j = 0; j < 3; j++)
                if (tailSeg.hasChild("k" + f + "tail" + j)) tailSeg.getChild("k" + f + "tail" + j).yRot = rad(22f * sin(t * 5f - 8f));
        }
        for (int i = 0; root.hasChild("kbub" + i); i++) {
            ModelPart b = root.getChild("kbub" + i);
            float life = (t * 0.3f + i / 12f) % 1f, a = i * 2.3f, r = 10f + (i % 3) * 2f;
            b.x = cos(a) * r; b.y = 20f - life * 26f; b.z = sin(a) * r;
            scale(b, 0.6f + 0.6f * (1f - life));
        }
    }

    // ------------------------------------------------------------------ pets
    private static void animatePet(ModelPart root, float t) {
        if (root.hasChild("d_head")) animateDragonPet(root, t);
        else if (root.hasChild("bb")) animateBunny(root, t);
    }

    static final int ETERNAL = 0, TILTED = 1, HELIX = 2, FAST = 3, LOOP = 4, PET = 5;

    private static int dragonStyle(String id) {
        return switch (id) {
            case "dragon_shadow" -> TILTED; case "dragon_frost" -> HELIX; case "dragon_infernal" -> FAST;
            case "dragon_celestial" -> LOOP; default -> ETERNAL;
        };
    }

    /** Flight path of each dragon (model pixels, around the body). i = body segment index (-1 = head). */
    private static float[] dragonPath(int style, float s, float i, float t) {
        switch (style) {
            case PET -> {
                float ii = Math.max(i, -1.6f), r = 4.2f + 0.08f * ii;
                return new float[]{17f + r * cos(s * 1.7f), -4f + ii * 0.75f + 1.5f * sin(t * 1.2f), 8f + r * sin(s * 1.7f)};
            }
            case TILTED -> { return new float[]{12f * cos(s), 5f + 6.6f * sin(s), 12f * sin(s) * 0.85f}; }
            case HELIX -> { return new float[]{10.5f * cos(s), -7f + Math.max(i, -1.5f) + 3f * sin(t * 0.7f), 10.5f * sin(s)}; }
            case FAST -> { return new float[]{11f * cos(s), 8f + 1.8f * sin(3f * s + t), 11f * sin(s)}; }
            case LOOP -> { float r = 11.5f + 3f * sin(2f * s); return new float[]{r * cos(s), 2f + 5f * sin(s), r * sin(s)}; }
            default -> {
                float r = 11.5f + 0.8f * sin(2f * s + 0.6f * t), y = 5f + 3f * sin(1.4f * s - t);
                float w = Math.max(0f, sin(2f * (float) Math.PI * (t - 0.5f) / 9f));
                float rise = w * w * 12f * (float) Math.exp(-Math.max(i, 0f) / 5f);
                return new float[]{r * cos(s), y - rise, r * sin(s)};
            }
        }
    }

    private static void animateDragonPet(ModelPart root, float t) { animateDragon(root, t, PET); }

    /** Dragons: the body follows the flight path, head leads, whiskers and mane sway, jaw opens and fires an energy orb. */
    private static void animateDragon(ModelPart root, float t, int style) {
        float th0 = style == FAST ? t * 1.6f + 1f : t * 0.85f + 1f, d = 0.19f;
        int n = 0;
        while (root.hasChild("d_s" + n)) n++;
        float[] hp = dragonPath(style, th0 + d * 1.6f, -1f, t), prev = hp, first = null;
        for (int i = 0; i < n; i++) {
            float[] pt = dragonPath(style, th0 - i * d, i, t);
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
        if (root.hasChild("d_orb") && first != null) {
            ModelPart orb = root.getChild("d_orb");
            float u = (tau - 0.18f) / 0.32f;
            if (u > 0 && u < 1) {
                float fx = hp[0] - first[0], fy = hp[1] - first[1], fz = hp[2] - first[2];
                float l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz); if (l < 1e-4f) l = 1;
                float k = 7f + u * 12f;
                orb.x = hp[0] + fx / l * k; orb.y = hp[1] + fy / l * k + 0.8f; orb.z = hp[2] + fz / l * k;
                orb.yRot = t * 4f;
                scale(orb, (0.6f + 1.4f * u) * (1f - u * u * u * u));
            } else scale(orb, 0f);
        }
        for (int k = 0; root.hasChild("d_p" + k) && n > 2; k++) {
            float life = (t * 0.4f + k / 20f) % 1f;
            int j = 1 + (k * 7) % (n - 2);
            float[] q = dragonPath(style, th0 - j * d - life * 0.35f, j, t);
            float r = (float) Math.sqrt(q[0] * q[0] + q[2] * q[2]); if (r < 1e-4f) r = 1;
            ModelPart sp = root.getChild("d_p" + k);
            sp.x = q[0] + q[0] / r * life * 2f + sin(k * 3f) * 0.8f; sp.y = q[1] - life * 3.5f; sp.z = q[2] + q[2] / r * life * 2f;
            float tw = sin(t * 12f + k);
            scale(sp, 1.3f * (1f - life) * (0.6f + 0.4f * tw * tw));
        }
        for (int k = 0; root.hasChild("d_k" + k) && n > 4; k++) {
            float life = (t * 0.25f + k / 10f) % 1f;
            int j = 3 + (k * 3) % (n - 4);
            float[] q = dragonPath(style, th0 - j * d - life * 0.3f, j, t);
            ModelPart sm = root.getChild("d_k" + k);
            sm.x = q[0]; sm.y = q[1] - life * 1.5f; sm.z = q[2];
            sm.xRot = t * 0.3f; sm.yRot = -th0 + rad(k * 40f); sm.zRot = rad(k * 30f);
            scale(sm, (0.8f + 1.2f * life) * (1f - life * life));
        }
    }

    // ------------------------------------------------------------------ Card Master
    private static final float[] CARD_SIZES = {0.62f, 0.52f, 0.58f, 0.5f, 0.56f, 0.66f, 0.5f, 0.58f, 0.68f, 0.54f, 0.6f, 0.5f};
    private static final float[][] TRAILS = {{10f, 14.5f, 16f, 1.6f, 1.0f}, {4f, 13f, -12f, -1.2f, 0.8f}, {16f, 12f, 8f, 2.1f, 0.7f}};

    private static float smooth(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    /** Yaw (degrees, model space) that turns a card's face toward whoever is looking at this player. */
    private static float cardFacing(Player p) {
        if (p == null) return 0f;   // preview: cards face the viewer
        Minecraft mc = Minecraft.getInstance();
        float theta;
        if (mc.player == null || p == mc.player) {
            float yr = p.getYRot();
            theta = mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT ? yr + 180f : yr;
        } else {
            double cx = mc.player.getX() - p.getX(), cz = mc.player.getZ() - p.getZ();
            theta = (float) Math.toDegrees(Math.atan2(cx, -cz));
        }
        return theta + 180f - p.yBodyRot;
    }

    /** Cards orbit in a scattered band, fly out and back, flip now and then, snap into a ring every 12 s; light trails + sparkles. */
    private static void animateCards(ModelPart root, float t, float facing) {
        int n = 0;
        while (root.hasChild("c" + n)) n++;
        if (n == 0) return;
        float ph = t % 12f;
        float form = smooth((ph - 7.5f) / 0.8f) * (1f - smooth((ph - 10.5f) / 0.8f));
        float[][] pos = new float[n][];
        float tau2 = (float) (Math.PI * 2);
        for (int k = 0; k < n; k++) {
            float h = (k * 0.6180339f) % 1f;
            float a = t * (0.55f + 0.12f * h) + (float) k / n * tau2;
            float r = 15.5f + sin(t * 0.8f + k);
            float y = 9f + 6f * sin(a + 0.6f) + 14f * ((k * 0.37f) % 1f - 0.5f) + sin(t * 1.4f + k * 1.7f);
            float fo = (t * 0.14f + h * 3.7f) % 1f;
            if (fo > 0.85f) r += 9f * sin((float) Math.PI * smooth((fo - 0.85f) / 0.15f));
            float fl = (t * 0.2f + h * 5.1f) % 1f;
            float flip = 180f * smooth((fl - 0.80f) / 0.06f) - 180f * smooth((fl - 0.93f) / 0.06f);
            float af = t * 0.4f + (float) k / n * tau2;
            float ox = cos(a) * r, oz = sin(a) * r, fx = cos(af) * 16f, fz = sin(af) * 16f;
            ModelPart c = root.getChild("c" + k);
            c.x = ox + (fx - ox) * form; c.y = y + (7f - y) * form; c.z = oz + (fz - oz) * form;
            c.xRot = rad(14f * sin(t * 1.1f + k * 0.7f));
            c.yRot = rad(facing + 22f * sin(t * 1.3f + k) + flip);
            c.zRot = rad(24f * sin(t * 0.7f + k * 2.1f));
            scale(c, k < CARD_SIZES.length ? CARD_SIZES[k] : 0.55f);
            pos[k] = new float[]{c.x, c.y, c.z};
        }
        for (int k = 0; root.hasChild("s" + k); k++) {
            float life = (t * 0.5f + k / 18f) % 1f;
            float[] c = k % 3 != 0 ? pos[(k * 5) % n] : new float[]{sin(k) * 12f, 2f + cos(k * 2f) * 4f, cos(k) * 12f};
            ModelPart sp = root.getChild("s" + k);
            sp.x = c[0] + sin(k * 2.3f) * 3.5f; sp.y = c[1] - 2.5f + cos(k) * 2.5f - life; sp.z = c[2] + cos(k * 1.7f) * 3.5f;
            sp.yRot = rad(facing);
            float w = sin((float) Math.PI * life);
            scale(sp, w * w * (0.6f + 0.4f * (k % 2)));
        }
        for (int r = 0; r < TRAILS.length && root.hasChild("t" + r); r++) {
            float[] tr = TRAILS[r];
            ModelPart ring = root.getChild("t" + r);
            ring.x = 0; ring.y = tr[0]; ring.z = 0; ring.xRot = rad(tr[2]); ring.zRot = rad(tr[2] * 0.4f);
            int nt = 0;
            while (ring.hasChild("t" + r + "_" + nt)) nt++;
            float head = t * tr[3] + r * 2.1f;
            for (int j = 0; j < nt; j++) {
                float a = head - Math.signum(tr[3]) * j * 0.085f * tr[4];
                ModelPart q = ring.getChild("t" + r + "_" + j);
                q.x = cos(a) * tr[1]; q.y = 0; q.z = sin(a) * tr[1];
                q.yRot = -a - (float) (Math.PI / 2);
                float f = Math.max(0.001f, 1f - (float) j / nt);
                q.xScale = 1.3f; q.yScale = f; q.zScale = f;
            }
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
    private static double hash01(double a, double b) {
        double v = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    private static float smoothf(double x) { x = Math.max(0, Math.min(1, x)); return (float) (x * x * (3 - 2 * x)); }

    /** NULL//PROTOCOL glyph state: {visible, x, y, z}. Same maths as tools/cosmetics/null_protocol.py. */
    private static final float[] GLYPH = new float[4];
    private static float[] glyphAt(int i, double t, double density, double spread, int n) {
        GLYPH[0] = 0;
        if (hash01(i, 99) > density) return GLYPH;
        double rate = 0.6 + hash01(i, 1) * 0.8;
        double life = t * rate + hash01(i, 2) * 10;
        double slot = Math.floor(life), frac = life - slot;
        boolean vis = frac < 0.85 && hash01(i, slot + 3) > 0.15 && hash01(i, Math.floor(t * 12) + 5) > 0.06;
        double a0 = hash01(i, slot) * 6.2832; int dir = i % 2 == 1 ? 1 : -1;
        double a = a0 + t * (0.18 + hash01(i, 4) * 0.14) * dir;
        boolean near = hash01(i + 5, slot) < 0.22;
        double rad = near ? 3.0 + hash01(slot + 2, i) * 1.2 : (4.6 + hash01(slot + 2, i) * 4.0) * spread;
        double y = -10 + hash01(slot, i + 7) * 35 + Math.sin(t * 0.8 + i) * 0.8 - frac * 1.4;
        double x = Math.sin(a) * rad, z = Math.cos(a) * rad;
        double ph = t % 9.0;
        float k = smoothf((ph - 5.6) / 0.6) * (1 - smoothf((ph - 7.6) / 0.6));
        if (k > 0 && i % 3 == 0) {
            double ra = (double) i / n * 6.2832 * 3 + t * 0.6;
            x += (Math.sin(ra) * 6.2 * spread - x) * k; z += (Math.cos(ra) * 6.2 * spread - z) * k; y += (9 - y) * k;
            vis = vis || k > 0.5;
        }
        GLYPH[0] = vis ? 1 : 0; GLYPH[1] = (float) x; GLYPH[2] = (float) y; GLYPH[3] = (float) z;
        return GLYPH;
    }

    /**
     * NULL//PROTOCOL (glyph_aura): monochrome glyphs orbit and drift, flicker, sometimes gather into a ring at the waist,
     * and break into pixel fragments when they vanish; tiny white sparks flash around the body. Always faces the viewer.
     */
    private static void animateGlyphs(ModelPart root, float t, float facing, float k) {
        double spread = 1f + (k - 1f) * 0.5f, density = WearablesModule.auraDensity();
        float face = (float) Math.toRadians(facing);
        int n = 0;
        while (root.hasChild("g" + n)) n++;
        for (int i = 0; i < n; i++) {
            ModelPart g = root.getChild("g" + i);
            float[] st = glyphAt(i, t, density, spread, n);
            g.visible = st[0] > 0;
            if (!g.visible) continue;
            g.x = st[1]; g.y = st[2]; g.z = st[3]; g.yRot = face;
        }
        for (int j = 0; root.hasChild("q" + j); j++) {   // fragments of the glyph that just vanished
            ModelPart q = root.getChild("q" + j);
            int gi = j * 3 % Math.max(1, n);
            q.visible = false;
            if (hash01(gi, 99) > density) continue;
            double rate = 0.6 + hash01(gi, 1) * 0.8;
            double life = t * rate + hash01(gi, 2) * 10;
            double slot = Math.floor(life), frac = life - slot;
            if (frac < 0.85 || frac >= 0.97 || hash01(gi, slot + 3) <= 0.15) continue;
            float[] st = glyphAt(gi, (slot + 0.84 - hash01(gi, 2) * 10) / rate, 1.0, spread, n);
            double e = (frac - 0.85) / 0.12, ang = j * 2.4;
            q.visible = true;
            q.x = (float) (st[1] + Math.cos(ang) * e * 1.6);
            q.y = (float) (st[2] - e * 1.2 + Math.sin(ang) * e);
            q.z = (float) (st[3] + Math.sin(ang) * e * 1.6);
            q.yRot = face;
        }
        double slot = Math.floor(t * 30);
        for (int i = 0; root.hasChild("k" + i); i++) {   // single-frame sparks anywhere around the body
            ModelPart sp = root.getChild("k" + i);
            sp.visible = hash01(i + 40, slot) < 0.05;
            if (!sp.visible) continue;
            double a = hash01(slot, i + 50) * 6.2832, rad = (4.5 + hash01(slot + 3, i) * 3) * spread;
            sp.x = (float) (Math.sin(a) * rad); sp.y = (float) (-6 + hash01(slot + 7, i) * 28); sp.z = (float) (Math.cos(a) * rad);
            sp.yRot = face;
        }
    }

    private static void animateSnow(ModelPart root, float t, float facing) {
        float face = facing * (float) (Math.PI / 180);
        for (int i = 0; root.hasChild("f" + i); i++) {
            ModelPart f = root.getChild("f" + i);
            var ip = f.getInitialPose();
            float h = (i * 0.6180339f) % 1f;                    // per-flake variety
            float drift = t * (0.10f + h * 0.12f) * (i % 2 == 0 ? 1 : -1);
            float c = net.minecraft.util.Mth.cos(drift), sn = net.minecraft.util.Mth.sin(drift);
            f.x = ip.x() * c - ip.z() * sn;
            f.z = ip.x() * sn + ip.z() * c;
            // falls from above your head to your feet, then starts again at the top
            float span = 36f, top = -11f;
            float fall = ((ip.y() - top) + t * (2.2f + h * 1.6f)) % span;
            f.y = top + fall;
            f.x += net.minecraft.util.Mth.sin(t * (0.9f + h * 0.5f) + i * 1.3f) * 0.6f;   // slight side-to-side sway
            f.yRot = (float) Math.atan2(f.x, f.z) + net.minecraft.util.Mth.sin(t * (0.5f + h * 0.4f) + i * 1.7f) * 0.3f;   // each crystal faces outward from you, gently wobbling
            f.zRot = t * (0.25f + h * 0.3f) * (i % 3 == 0 ? -1 : 1);
            float u = fall / span;   // fade in at the top, fade out at the feet
            float a = u < 0.1f ? u / 0.1f : u > 0.88f ? (1f - u) / 0.12f : 1f;
            float size = (0.9f + h * 0.3f) * Math.max(0f, a);
            f.visible = size > 0.04f;
            f.xScale = f.yScale = f.zScale = size;
        }
    }

    /** Swirling rings (each spins its own way), crackling segments and rising sparks. */
    private static void drawAura(PoseStack pose, Sink out, CosmeticModel m) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        ModelPart root = m.root;
        if (root.hasChild("f0")) {
            animateSnow(root, t, 0f);
            out.part(root, pose, RenderTypes.eyes(m.currentTexture()), FULL_BRIGHT);
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
        out.part(root, pose, RenderTypes.eyes(m.currentTexture()), FULL_BRIGHT);
    }

    public static void draw(PoseStack pose, SubmitNodeCollector out, int light, CosmeticModel m, ModelPart part) {
        draw(pose, of(out), light, m, part);
    }

    public static void draw(PoseStack pose, Sink out, int light, CosmeticModel m, ModelPart part) {
        out.part(part, pose, RenderTypes.entityCutoutNoCull(m.currentTexture()), light);
        Identifier g = m.currentGlow();
        if (g != null)
            out.part(part, pose, RenderTypes.eyes(g), FULL_BRIGHT);
    }
}
