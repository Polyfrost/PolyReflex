package org.polyfrost.polyreflex.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.polyfrost.polyreflex.LowLatency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.polydium.client.perf.PresentThread")
public class PresentThreadMixin {
    @Inject(method = "handOff", at = @At(value = "INVOKE", target = "Lorg/polyfrost/polydium/client/perf/PresentThread;drain()V", shift = At.Shift.AFTER))
    private static void polyreflex$handOff(final CallbackInfo ci) {
        LowLatency.frameHandedOff();
    }

    @Inject(method = "presentHandedOff", at = @At("HEAD"))
    private static void polyreflex$presentThread(final CallbackInfo ci) {
        LowLatency.onPresentThread();
    }

    @WrapOperation(
        method = "queuePresent",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"
        )
    )
    private static int polyreflex$present(final VkQueue queue, final VkPresentInfoKHR presentInfo, final Operation<Integer> original) {
        MemoryStack stack = MemoryStack.stackGet();
        boolean reflexFrame = LowLatency.beforePresent(queue, presentInfo, stack);
        int result = original.call(queue, presentInfo);
        if (reflexFrame) {
            LowLatency.afterPresent(stack);
        }
        return result;
    }
}
