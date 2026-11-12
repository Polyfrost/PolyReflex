package org.polyfrost.polyreflex;

import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;

import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;

final class VendorVk {
    static final String NV_LOW_LATENCY_2 = "VK_NV_low_latency2";
    static final String AMD_ANTI_LAG = "VK_AMD_anti_lag";

    static final int MARKER_SIMULATION_START = 0;
    static final int MARKER_SIMULATION_END = 1;
    static final int MARKER_RENDERSUBMIT_START = 2;
    static final int MARKER_RENDERSUBMIT_END = 3;
    static final int MARKER_PRESENT_START = 4;
    static final int MARKER_PRESENT_END = 5;
    static final int MARKER_INPUT_SAMPLE = 6;

    static final int ANTI_LAG_STAGE_INPUT = 0;
    static final int ANTI_LAG_STAGE_PRESENT = 1;

    static final int TYPE_LATENCY_SLEEP_MODE_INFO_NV = 1000505000;
    static final int TYPE_LATENCY_SLEEP_INFO_NV = 1000505001;
    static final int TYPE_SET_LATENCY_MARKER_INFO_NV = 1000505002;
    static final int TYPE_LATENCY_SUBMISSION_PRESENT_ID_NV = 1000505005;
    static final int TYPE_SWAPCHAIN_LATENCY_CREATE_INFO_NV = 1000505007;
    static final int TYPE_PHYSICAL_DEVICE_ANTI_LAG_FEATURES_AMD = 1000476000;
    static final int TYPE_ANTI_LAG_DATA_AMD = 1000476001;
    static final int TYPE_ANTI_LAG_PRESENTATION_INFO_AMD = 1000476002;
    private static final int ANTI_LAG_MODE_ON = 1;

    private static long setLatencySleepMode;
    private static long latencySleep;
    private static long setLatencyMarker;
    private static long antiLagUpdate;

    private VendorVk() {
    }

    static boolean loadLowLatency(final VkDevice device) {
        setLatencySleepMode = VK12.vkGetDeviceProcAddr(device, "vkSetLatencySleepModeNV");
        latencySleep = VK12.vkGetDeviceProcAddr(device, "vkLatencySleepNV");
        setLatencyMarker = VK12.vkGetDeviceProcAddr(device, "vkSetLatencyMarkerNV");
        return setLatencySleepMode != 0L && latencySleep != 0L && setLatencyMarker != 0L;
    }

    static boolean loadAntiLag(final VkDevice device) {
        antiLagUpdate = VK12.vkGetDeviceProcAddr(device, "vkAntiLagUpdateAMD");
        return antiLagUpdate != 0L;
    }

    static long struct(final MemoryStack stack, final int size, final int sType, final long pNext) {
        long address = stack.ncalloc(8, 1, size);
        memPutInt(address, sType);
        memPutAddress(address + 8, pNext);
        return address;
    }

    static long antiLagFeatures(final MemoryStack stack, final long pNext) {
        return struct(stack, 24, TYPE_PHYSICAL_DEVICE_ANTI_LAG_FEATURES_AMD, pNext);
    }

    static boolean antiLagSupported(final long features) {
        return memGetInt(features + 16) != 0;
    }

    static long chain(final long struct, final long pNext) {
        memPutAddress(struct + 8, pNext);
        return struct;
    }

    static long swapchainLatencyCreateInfo(final MemoryStack stack, final long pNext) {
        long info = struct(stack, 24, TYPE_SWAPCHAIN_LATENCY_CREATE_INFO_NV, pNext);
        memPutInt(info + 16, 1);
        return info;
    }

    static long submissionPresentId(final MemoryStack stack, final long presentId, final long pNext) {
        long info = struct(stack, 24, TYPE_LATENCY_SUBMISSION_PRESENT_ID_NV, pNext);
        memPutLong(info + 16, presentId);
        return info;
    }

    static int setLatencySleepMode(final VkDevice device, final long swapchain, final boolean boost, final int minimumIntervalUs, final MemoryStack stack) {
        long info = struct(stack, 32, TYPE_LATENCY_SLEEP_MODE_INFO_NV, 0L);
        memPutInt(info + 16, 1);
        memPutInt(info + 20, boost ? 1 : 0);
        memPutInt(info + 24, minimumIntervalUs);
        return JNI.callPJPI(device.address(), swapchain, info, setLatencySleepMode);
    }

    static int latencySleep(final VkDevice device, final long swapchain, final long semaphore, final long value, final MemoryStack stack) {
        long info = struct(stack, 32, TYPE_LATENCY_SLEEP_INFO_NV, 0L);
        memPutLong(info + 16, semaphore);
        memPutLong(info + 24, value);
        return JNI.callPJPI(device.address(), swapchain, info, latencySleep);
    }

    static void setLatencyMarker(final VkDevice device, final long swapchain, final long presentId, final int marker, final MemoryStack stack) {
        long info = struct(stack, 32, TYPE_SET_LATENCY_MARKER_INFO_NV, 0L);
        memPutLong(info + 16, presentId);
        memPutInt(info + 24, marker);
        JNI.callPJPV(device.address(), swapchain, info, setLatencyMarker);
    }

    static void antiLagUpdate(final VkDevice device, final int maxFps, final int stage, final long frameIndex, final MemoryStack stack) {
        long presentation = struct(stack, 32, TYPE_ANTI_LAG_PRESENTATION_INFO_AMD, 0L);
        memPutInt(presentation + 16, stage);
        memPutLong(presentation + 24, frameIndex);
        long data = struct(stack, 32, TYPE_ANTI_LAG_DATA_AMD, 0L);
        memPutInt(data + 16, ANTI_LAG_MODE_ON);
        memPutInt(data + 20, maxFps);
        memPutAddress(data + 24, presentation);
        JNI.callPPV(device.address(), data, antiLagUpdate);
    }
}
