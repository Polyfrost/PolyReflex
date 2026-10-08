package org.polyfrost.polyreflex;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.AMDAntiLag;
import org.lwjgl.vulkan.GOOGLEDisplayTiming;
import org.lwjgl.vulkan.KHRPresentWait;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.NVLowLatency2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VKCapabilitiesDevice;
import org.lwjgl.vulkan.VkAntiLagDataAMD;
import org.lwjgl.vulkan.VkAntiLagPresentationInfoAMD;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkLatencySleepInfoNV;
import org.lwjgl.vulkan.VkLatencySleepModeInfoNV;
import org.lwjgl.vulkan.VkLatencySubmissionPresentIdNV;
import org.lwjgl.vulkan.VkPastPresentationTimingGOOGLE;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceAntiLagFeaturesAMD;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDevicePresentIdFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDevicePresentWaitFeaturesKHR;
import org.lwjgl.vulkan.VkPresentIdKHR;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkPresentTimeGOOGLE;
import org.lwjgl.vulkan.VkPresentTimesInfoGOOGLE;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRefreshCycleDurationGOOGLE;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreTypeCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;
import org.lwjgl.vulkan.VkSetLatencyMarkerInfoNV;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkSwapchainLatencyCreateInfoNV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LowLatency {
    private static final Logger LOGGER = LoggerFactory.getLogger("PolyReflex");
    private static final boolean ENABLED = !Boolean.getBoolean("polyreflex.off");
    private static final boolean BOOST = Boolean.getBoolean("polyreflex.boost");
    private static final boolean DEBUG = Boolean.getBoolean("polyreflex.debug");
    private static final String PRESENT_ID_EXTENSION = "VK_KHR_present_id";
    private static final int VANILLA_UNLIMITED_FPS = 260;

    private static VkDevice device;
    private static long swapchain;
    private static VkQueue queue;
    private static long sleepSemaphore;
    private static long sleepCount;
    private static volatile long frameId;
    private static long presentedFrameId;
    private static int fpsLimit;
    private static boolean presentPending;
    private static final long[] INPUT_TIMES = new long[64];
    private static long onScreenFrameId;
    private static int ungatedFrames;
    private static boolean vsync;
    private static int queuedFrames = 2;
    private static int lastWantedQueuedFrames = 2;
    private static long windowMargin;
    private static long windowWork;
    private static long windowGateWait;
    private static long windowStart;
    private static boolean gateCostsFrames;
    private static int windowFrames;
    private static long refreshNs;
    private static long statLatency;
    private static long statMargin;
    private static int statFrames;
    private static long statSince;

    private LowLatency() {
    }

    public static void addDeviceExtensions(final VkPhysicalDevice physicalDevice, final VkDeviceCreateInfo info) {
        if (!ENABLED) {
            return;
        }
        MemoryStack stack = MemoryStack.stackGet();
        Set<String> supported = new HashSet<>();
        IntBuffer count = stack.callocInt(1);
        VK12.vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, count, null);
        try (VkExtensionProperties.Buffer properties = VkExtensionProperties.malloc(count.get(0))) {
            VK12.vkEnumerateDeviceExtensionProperties(physicalDevice, (ByteBuffer) null, count, properties);
            for (int i = 0; i < count.get(0); i++) {
                supported.add(properties.get(i).extensionNameString());
            }
        }

        VkPhysicalDevicePresentWaitFeaturesKHR presentWait = VkPhysicalDevicePresentWaitFeaturesKHR.calloc(stack).sType$Default();
        VkPhysicalDevicePresentIdFeaturesKHR presentId = VkPhysicalDevicePresentIdFeaturesKHR.calloc(stack).sType$Default().pNext(presentWait.address());
        VkPhysicalDeviceAntiLagFeaturesAMD antiLag = VkPhysicalDeviceAntiLagFeaturesAMD.calloc(stack).sType$Default().pNext(presentId.address());
        VK12.vkGetPhysicalDeviceFeatures2(physicalDevice, VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(antiLag.address()));

        List<String> added = new ArrayList<>();
        if (supported.contains(NVLowLatency2.VK_NV_LOW_LATENCY_2_EXTENSION_NAME)) {
            added.add(NVLowLatency2.VK_NV_LOW_LATENCY_2_EXTENSION_NAME);
        }
        if (supported.contains(AMDAntiLag.VK_AMD_ANTI_LAG_EXTENSION_NAME) && antiLag.antiLag()) {
            added.add(AMDAntiLag.VK_AMD_ANTI_LAG_EXTENSION_NAME);
            info.pNext(antiLag.pNext(info.pNext()).address());
        }
        boolean vendor = !added.isEmpty();
        if (supported.contains(PRESENT_ID_EXTENSION) && presentId.presentId()) {
            added.add(PRESENT_ID_EXTENSION);
            info.pNext(presentId.pNext(info.pNext()).address());
            if (!vendor && supported.contains(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME) && presentWait.presentWait()) {
                added.add(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME);
                info.pNext(presentWait.pNext(info.pNext()).address());
            }
        }
        if (!vendor && supported.contains(GOOGLEDisplayTiming.VK_GOOGLE_DISPLAY_TIMING_EXTENSION_NAME)) {
            added.add(GOOGLEDisplayTiming.VK_GOOGLE_DISPLAY_TIMING_EXTENSION_NAME);
        }
        if (added.isEmpty()) {
            return;
        }

        PointerBuffer existing = info.ppEnabledExtensionNames();
        PointerBuffer names = stack.mallocPointer((existing == null ? 0 : existing.remaining()) + added.size());
        if (existing != null) {
            names.put(existing);
        }
        for (String name : added) {
            names.put(stack.UTF8(name));
        }
        info.ppEnabledExtensionNames(names.flip());
    }

    private static boolean reflex() {
        return device != null && swapchain != 0L && sleepSemaphore != 0L && device.getCapabilities().VK_NV_low_latency2;
    }

    private static boolean antiLag() {
        return device != null && swapchain != 0L && device.getCapabilities().VK_AMD_anti_lag;
    }

    private static boolean gpuSync(final VkDevice vkDevice) {
        return ENABLED && !vkDevice.getCapabilities().VK_NV_low_latency2 && !vkDevice.getCapabilities().VK_AMD_anti_lag;
    }

    public static void beforeSwapchainCreate(final VkDevice vkDevice, final VkSwapchainCreateInfoKHR info, final MemoryStack stack) {
        if (vkDevice.getCapabilities().VK_NV_low_latency2) {
            info.pNext(VkSwapchainLatencyCreateInfoNV.calloc(stack).sType$Default().latencyModeEnable(true).pNext(info.pNext()).address());
        }
        vsync = info.presentMode() == KHRSurface.VK_PRESENT_MODE_FIFO_KHR || info.presentMode() == KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
        if (vsync && gpuSync(vkDevice)) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            if (KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(vkDevice.getPhysicalDevice(), info.surface(), capabilities) == VK12.VK_SUCCESS) {
                info.minImageCount(capabilities.minImageCount());
            }
        }
    }

    public static void afterSwapchainCreate(final VkDevice vkDevice, final long newSwapchain, final MemoryStack stack) {
        boolean first = device == null;
        device = vkDevice;
        swapchain = newSwapchain;
        if (vkDevice.getCapabilities().VK_NV_low_latency2) {
            if (sleepSemaphore == 0L) {
                VkSemaphoreTypeCreateInfo type = VkSemaphoreTypeCreateInfo.calloc(stack).sType$Default().semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE);
                LongBuffer out = stack.callocLong(1);
                int result = VK12.vkCreateSemaphore(vkDevice, VkSemaphoreCreateInfo.calloc(stack).sType$Default().pNext(type.address()), null, out);
                if (result != VK12.VK_SUCCESS) {
                    LOGGER.warn("Reflex disabled: vkCreateSemaphore failed ({})", result);
                    return;
                }
                sleepSemaphore = out.get(0);
            }
            applySleepMode(stack);
        }
        if (first) {
            LOGGER.info(
                "Latency mode: {}",
                reflex() ? "NVIDIA Reflex (VK_NV_low_latency2" + (BOOST ? ", boost)" : ")") : antiLag() ? "AMD Anti-Lag 2 (VK_AMD_anti_lag)" : gpuSync(vkDevice) ? "software fallback (" + (vkDevice.getCapabilities().VK_KHR_present_wait ? "VK_KHR_present_wait" : "vkQueueWaitIdle") + (vkDevice.getCapabilities().VK_GOOGLE_display_timing ? ", VK_GOOGLE_display_timing)" : ")") : "off"
            );
        }
    }

    public static void swapchainDestroyed() {
        swapchain = 0L;
        presentPending = false;
        onScreenFrameId = frameId;
        refreshNs = 0L;
    }

    private static void applySleepMode(final MemoryStack stack) {
        int result = NVLowLatency2.vkSetLatencySleepModeNV(
            device,
            swapchain,
            VkLatencySleepModeInfoNV.calloc(stack)
                .sType$Default()
                .lowLatencyMode(true)
                .lowLatencyBoost(BOOST)
                .minimumIntervalUs(fpsLimit > 0 ? 1_000_000 / fpsLimit : 0)
        );
        if (result != VK12.VK_SUCCESS) {
            LOGGER.warn("vkSetLatencySleepModeNV failed ({})", result);
        }
    }

    public static void frameStart(final int vanillaFpsLimit) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int limit = vanillaFpsLimit > 0 && vanillaFpsLimit < VANILLA_UNLIMITED_FPS ? vanillaFpsLimit : 0;
            boolean limitChanged = limit != fpsLimit;
            fpsLimit = limit;
            if (reflex()) {
                if (limitChanged) {
                    applySleepMode(stack);
                }
                long value = ++sleepCount;
                int result = NVLowLatency2.vkLatencySleepNV(
                    device, swapchain, VkLatencySleepInfoNV.calloc(stack).sType$Default().signalSemaphore(sleepSemaphore).value(value)
                );
                if (result == VK12.VK_SUCCESS) {
                    VK12.vkWaitSemaphores(
                        device,
                        VkSemaphoreWaitInfo.calloc(stack).sType$Default().semaphoreCount(1).pSemaphores(stack.longs(sleepSemaphore)).pValues(stack.longs(value)),
                        1_000_000_000L
                    );
                }
                frameId++;
                marker(stack, NVLowLatency2.VK_LATENCY_MARKER_SIMULATION_START_NV);
                marker(stack, NVLowLatency2.VK_LATENCY_MARKER_INPUT_SAMPLE_NV);
            } else if (antiLag()) {
                frameId++;
                antiLagUpdate(stack, AMDAntiLag.VK_ANTI_LAG_STAGE_INPUT_AMD);
            } else if (device != null && swapchain != 0L && gpuSync(device)) {
                VKCapabilitiesDevice capabilities = device.getCapabilities();
                if (presentPending) {
                    presentPending = false;
                    if (vsync) {
                        if (capabilities.VK_GOOGLE_display_timing) {
                            waitUntilOnScreen(stack, frameId - queuedFrames);
                        }
                    } else if (capabilities.VK_KHR_present_wait) {
                        KHRPresentWait.vkWaitForPresentKHR(device, swapchain, frameId, 100_000_000L);
                    } else {
                        VK12.vkQueueWaitIdle(queue);
                    }
                }
                if (DEBUG && capabilities.VK_GOOGLE_display_timing) {
                    readPresentTimings(stack);
                }
                frameId++;
                INPUT_TIMES[(int) (frameId & 63)] = System.nanoTime();
            }
        }
    }

    public static boolean limitsFps() {
        return (reflex() || antiLag()) && presentedFrameId == frameId;
    }

    public static void renderStart() {
        if (reflex()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                marker(stack, NVLowLatency2.VK_LATENCY_MARKER_SIMULATION_END_NV);
                marker(stack, NVLowLatency2.VK_LATENCY_MARKER_RENDERSUBMIT_START_NV);
            }
        }
    }

    public static void beforeSubmit(final VkSubmitInfo2.Buffer submits, final MemoryStack stack) {
        long id = frameId;
        if (id == 0L || !reflex()) {
            return;
        }
        for (int i = submits.position(); i < submits.limit(); i++) {
            VkSubmitInfo2 submit = submits.get(i);
            submit.pNext(VkLatencySubmissionPresentIdNV.calloc(stack).sType$Default().presentID(id).pNext(submit.pNext()).address());
        }
    }

    public static boolean beforePresent(final VkQueue presentQueue, final VkPresentInfoKHR presentInfo, final MemoryStack stack) {
        queue = presentQueue;
        if (frameId == presentedFrameId) {
            return false;
        }
        if (reflex()) {
            presentedFrameId = frameId;
            marker(stack, NVLowLatency2.VK_LATENCY_MARKER_RENDERSUBMIT_END_NV);
            marker(stack, NVLowLatency2.VK_LATENCY_MARKER_PRESENT_START_NV);
            if (device.getCapabilities().VK_KHR_present_id) {
                presentInfo.pNext(
                    VkPresentIdKHR.calloc(stack).sType$Default().swapchainCount(1).pPresentIds(stack.longs(frameId)).pNext(presentInfo.pNext()).address()
                );
            }
            return true;
        } else if (antiLag()) {
            presentedFrameId = frameId;
            antiLagUpdate(stack, AMDAntiLag.VK_ANTI_LAG_STAGE_PRESENT_AMD);
        } else if (gpuSync(device)) {
            presentedFrameId = frameId;
            presentPending = true;
            if (device.getCapabilities().VK_KHR_present_id) {
                presentInfo.pNext(
                    VkPresentIdKHR.calloc(stack).sType$Default().swapchainCount(1).pPresentIds(stack.longs(frameId)).pNext(presentInfo.pNext()).address()
                );
            }
            if (device.getCapabilities().VK_GOOGLE_display_timing) {
                presentInfo.pNext(
                    VkPresentTimesInfoGOOGLE.calloc(stack)
                        .sType$Default()
                        .swapchainCount(1)
                        .pTimes(VkPresentTimeGOOGLE.calloc(1, stack).presentID((int) frameId))
                        .pNext(presentInfo.pNext())
                        .address()
                );
            }
        }
        return false;
    }

    public static void afterPresent(final MemoryStack stack) {
        marker(stack, NVLowLatency2.VK_LATENCY_MARKER_PRESENT_END_NV);
    }

    private static void waitUntilOnScreen(final MemoryStack stack, final long frame) {
        if (ungatedFrames > 0) {
            ungatedFrames--;
            readPresentTimings(stack);
            return;
        }
        long start = System.nanoTime();
        while (readPresentTimings(stack) < frame) {
            if (System.nanoTime() - start > 50_000_000L) {
                ungatedFrames = 120;
                return;
            }
            LockSupport.parkNanos(100_000L);
        }
        windowGateWait += System.nanoTime() - start;
    }

    private static void updateQueuedFrames(final MemoryStack stack) {
        if (refreshNs == 0L) {
            VkRefreshCycleDurationGOOGLE refresh = VkRefreshCycleDurationGOOGLE.calloc(stack);
            GOOGLEDisplayTiming.vkGetRefreshCycleDurationGOOGLE(device, swapchain, refresh);
            refreshNs = Math.max(1L, refresh.refreshDuration());
        }
        long now = System.nanoTime();
        long framePeriod = (now - windowStart) / windowFrames;
        if (framePeriod > refreshNs * 3 / 2 && windowWork / windowFrames < refreshNs / 2) {
            int wanted = (int) Math.clamp(Math.ceilDiv(windowMargin / windowFrames, refreshNs) - 1, 0L, 4L);
            if (wanted == lastWantedQueuedFrames) {
                queuedFrames = wanted;
            }
            lastWantedQueuedFrames = wanted;
            gateCostsFrames = false;
        } else if (framePeriod > refreshNs * 11 / 10 && windowGateWait / windowFrames > refreshNs / 16) {
            if (gateCostsFrames && queuedFrames < 4) {
                queuedFrames++;
            }
            gateCostsFrames = !gateCostsFrames;
        } else {
            gateCostsFrames = false;
        }
        windowMargin = windowWork = windowGateWait = windowFrames = 0;
        windowStart = now;
    }

    private static long readPresentTimings(final MemoryStack stack) {
        try (MemoryStack frame = stack.push()) {
            IntBuffer count = frame.ints(8);
            VkPastPresentationTimingGOOGLE.Buffer timings = VkPastPresentationTimingGOOGLE.calloc(8, frame);
            if (GOOGLEDisplayTiming.vkGetPastPresentationTimingGOOGLE(device, swapchain, count, timings) < 0) {
                return onScreenFrameId;
            }
            for (int i = 0; i < count.get(0); i++) {
                VkPastPresentationTimingGOOGLE timing = timings.get(i);
                onScreenFrameId = Math.max(onScreenFrameId, frameId - (int) ((int) frameId - timing.presentID()));
                long latency = timing.actualPresentTime() - INPUT_TIMES[timing.presentID() & 63];
                if (latency > 0L) {
                    windowMargin += timing.presentMargin();
                    windowWork += latency - timing.presentMargin();
                    if (++windowFrames == 64) {
                        updateQueuedFrames(frame);
                    }
                }
                statLatency += latency;
                statMargin += timing.presentMargin();
                statFrames++;
            }
        }
        long now = System.nanoTime();
        if (DEBUG && now - statSince > 2_000_000_000L && statFrames > 0) {
            LOGGER.info(
                "{} fps, input to screen {} ms, submit to screen {} ms, {} queued frames",
                statFrames * 1_000_000_000L / (now - statSince), statLatency / statFrames / 1e6, statMargin / statFrames / 1e6, queuedFrames
            );
            statSince = now;
            statLatency = statMargin = statFrames = 0;
        }
        return onScreenFrameId;
    }

    private static void marker(final MemoryStack stack, final int marker) {
        NVLowLatency2.vkSetLatencyMarkerNV(device, swapchain, VkSetLatencyMarkerInfoNV.calloc(stack).sType$Default().presentID(frameId).marker(marker));
    }

    private static void antiLagUpdate(final MemoryStack stack, final int stage) {
        AMDAntiLag.vkAntiLagUpdateAMD(
            device,
            VkAntiLagDataAMD.calloc(stack)
                .sType$Default()
                .mode(AMDAntiLag.VK_ANTI_LAG_MODE_ON_AMD)
                .maxFPS(fpsLimit)
                .pPresentationInfo(VkAntiLagPresentationInfoAMD.calloc(stack).sType$Default().stage(stage).frameIndex(frameId))
        );
    }
}
