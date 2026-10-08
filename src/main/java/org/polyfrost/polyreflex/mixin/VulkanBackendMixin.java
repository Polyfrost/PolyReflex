package org.polyfrost.polyreflex.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.polyfrost.polyreflex.LowLatency;

@Mixin(VulkanBackend.class)
public class VulkanBackendMixin {
    @WrapOperation(
        //? if >=26.3 {
        method = "createDevice(Lcom/mojang/renderpearl/backend/vulkan/init/FeatureSet;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;)Lorg/lwjgl/vulkan/VkDevice;",
        //?} else {
        /*method = "createDevice(Ljava/util/Collection;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;",
        *///?}
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK12;vkCreateDevice(Lorg/lwjgl/vulkan/VkPhysicalDevice;Lorg/lwjgl/vulkan/VkDeviceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Lorg/lwjgl/PointerBuffer;)I"
        )
    )
    private static int polyreflex$createDevice(
        final VkPhysicalDevice physicalDevice,
        final VkDeviceCreateInfo info,
        final VkAllocationCallbacks allocator,
        final PointerBuffer devicePtr,
        final Operation<Integer> original
    ) {
        LowLatency.addDeviceExtensions(physicalDevice, info);
        return original.call(physicalDevice, info, allocator, devicePtr);
    }
}
