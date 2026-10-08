plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3" /* [SC] DO NOT EDIT */

stonecutter parameters {
    replacements.string(current.parsed >= "26.3") {
        replace("com.mojang.blaze3d.vulkan", "com.mojang.renderpearl.backend.vulkan")
        replace("com/mojang/blaze3d/vulkan", "com/mojang/renderpearl/backend/vulkan")
    }
}
