package org.polyfrost.polyreflex.mixin;

import org.polyfrost.polyreflex.LowLatency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "org.polyfrost.polydium.client.perf.FramePath")
public class FramePathMixin {
    @ModifyVariable(method = "beginFrame", at = @At("HEAD"), argsOnly = true)
    private static boolean polyreflex$deferAcquire(final boolean eligible) {
        return eligible && LowLatency.allowsDeferredAcquire();
    }
}
