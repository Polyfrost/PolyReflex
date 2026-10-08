package org.polyfrost.polyreflex.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.polyfrost.polyreflex.LowLatency;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public abstract FramerateLimitTracker getFramerateLimitTracker();

    @Inject(
        method = "run",
        //? if >=26.3 {
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;pollEvents(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V")
        //?} else {
        /*at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;pollEvents()V")
        *///?}
    )
    private void polyreflex$frameStart(final CallbackInfo ci) {
        LowLatency.frameStart(this.getFramerateLimitTracker().getFramerateLimit());
    }

    @Inject(
        method = "renderFrame",
        //? if >=26.3 {
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V")
        //?} else {
        /*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V")
        *///?}
    )
    private void polyreflex$renderStart(final CallbackInfo ci) {
        LowLatency.renderStart();
    }

    /** The limit is forwarded to the driver in frameStart; sleeping here as well would only add latency. */
    @WrapWithCondition(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"))
    private boolean polyreflex$skipVanillaLimiter(final int framerateLimit) {
        return !LowLatency.limitsFps();
    }
}
