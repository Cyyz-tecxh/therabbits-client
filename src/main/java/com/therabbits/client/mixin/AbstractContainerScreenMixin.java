package com.therabbits.client.mixin;

import com.therabbits.client.module.utility.MouseTweaksModule;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected abstract Slot getHoveredSlot(double x, double y);
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType type);

    @Unique private final Set<Slot> therabbits$moved = new HashSet<>();

    /** Shift + drag: quick-move every stack the cursor passes over (once per drag). */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void therabbits$dragMove(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        MouseTweaksModule m = MouseTweaksModule.get();
        if (m == null || !m.enabled() || event.button() != 0 || !event.hasShiftDown()) return;
        Slot s = getHoveredSlot(event.x(), event.y());
        if (s != null && s.hasItem() && therabbits$moved.add(s)) slotClicked(s, s.index, 0, ClickType.QUICK_MOVE);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void therabbits$dragEnd(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        therabbits$moved.clear();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void therabbits$dragStart(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        therabbits$moved.clear();
    }
}
