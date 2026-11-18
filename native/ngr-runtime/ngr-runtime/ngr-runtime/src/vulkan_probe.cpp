#include "ngr/runtime_info.hpp"
#include <vulkan/vulkan.h>
#include <sstream>
#include <string>
#include <vector>
namespace ngr {
std::string vulkan_probe() {
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "Native Game Recompiler";
    app.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    app.pEngineName = "NGR Runtime";
    app.engineVersion = VK_MAKE_VERSION(0, 1, 0);
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo create{};
    create.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&create, nullptr, &instance);
    if (result != VK_SUCCESS)
        return "Vulkan instance creation failed: VkResult " + std::to_string(result);
    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) {
        vkDestroyInstance(instance, nullptr);
        return "No Vulkan physical devices found (VkResult " + std::to_string(result) + ")";
    }
    std::vector<VkPhysicalDevice> devices(count);
    result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    std::ostringstream out;
    if (result != VK_SUCCESS) out << "Device enumeration failed: VkResult " << result;
    else for (uint32_t i = 0; i < count; ++i) {
        VkPhysicalDeviceProperties p{};
        vkGetPhysicalDeviceProperties(devices[i], &p);
        out << (i + 1) << ". " << p.deviceName << "\n"
            << "   Vendor ID: 0x" << std::hex << p.vendorID
            << "  Device ID: 0x" << p.deviceID << std::dec << "\n"
            << "   Vulkan API: " << VK_VERSION_MAJOR(p.apiVersion) << "."
            << VK_VERSION_MINOR(p.apiVersion) << "." << VK_VERSION_PATCH(p.apiVersion) << "\n";
    }
    vkDestroyInstance(instance, nullptr);
    return out.str();
}
}
