package org.polyfrost.polyreflex.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.polyfrost.polyreflex.LowLatency;

@Mixin(VulkanQueue.Submission.class)
public class VulkanQueueSubmissionMixin {
    @WrapOperation(
        method = "close",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkQueueSubmit2KHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo2$Buffer;J)I"
        )
    )
    private int polyreflex$submit(final VkQueue queue, final VkSubmitInfo2.Buffer submits, final long fence, final Operation<Integer> original) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LowLatency.beforeSubmit(submits, stack);
            return original.call(queue, submits, fence);
        }
    }
}
