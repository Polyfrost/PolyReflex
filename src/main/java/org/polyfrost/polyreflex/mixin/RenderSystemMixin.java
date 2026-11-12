package org.polyfrost.polyreflex.mixin;

//? if <26.1 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyreflex.LowLatency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Inject(method = "flipFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;pollEvents()V", ordinal = 1))
    private static void polyreflex$frameStart(final CallbackInfo ci) {
        LowLatency.frameStart(Minecraft.getInstance().getFramerateLimitTracker().getFramerateLimit());
    }
}
*///?}
