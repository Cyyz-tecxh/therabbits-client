package com.therabbits.client.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the start menu turn the 3D player slowly when nobody is dragging it. */
@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
    @Accessor("rotationY") float therabbits$getRotationY();
    @Accessor("rotationY") void therabbits$setRotationY(float value);
    @Accessor("rotationX") void therabbits$setRotationX(float value);
}
