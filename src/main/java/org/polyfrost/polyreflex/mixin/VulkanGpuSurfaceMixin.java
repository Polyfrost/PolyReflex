package org.polyfrost.polyreflex.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuSurface;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.polyfrost.polyreflex.LowLatency;

@Mixin(VulkanGpuSurface.class)
public class VulkanGpuSurfaceMixin {
    @WrapOperation(
        method = "configure",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"
        )
    )
    private int polyreflex$createSwapchain(
        final VkDevice device,
        final VkSwapchainCreateInfoKHR info,
        final VkAllocationCallbacks allocator,
        final LongBuffer swapchainPtr,
        final Operation<Integer> original
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LowLatency.beforeSwapchainCreate(device, info, stack);
            int result = original.call(device, info, allocator, swapchainPtr);
            if (result >= 0) {
                LowLatency.afterSwapchainCreate(device, swapchainPtr.get(0), stack);
            }
            return result;
        }
    }

    @Inject(method = "destroySwapchain", at = @At("HEAD"))
    private void polyreflex$destroySwapchain(final CallbackInfo ci) {
        LowLatency.swapchainDestroyed();
    }

    @WrapOperation(
        method = "present",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"
        )
    )
    private int polyreflex$present(final VkQueue queue, final VkPresentInfoKHR presentInfo, final Operation<Integer> original) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean reflexFrame = LowLatency.beforePresent(queue, presentInfo, stack);
            int result = original.call(queue, presentInfo);
            if (reflexFrame) {
                LowLatency.afterPresent(stack);
            }
            return result;
        }
    }
}
