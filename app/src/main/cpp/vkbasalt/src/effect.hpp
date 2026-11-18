#ifndef EFFECT_HPP_INCLUDED
#define EFFECT_HPP_INCLUDED
#include <vector>
#include <fstream>
#include <string>
#include <iostream>
#include <vector>
#include <unordered_map>

#include "vulkan_include.hpp"

namespace vkBasalt
{
    class Config; // for live updateUniformsFromConfig

    class Effect
    {
    public:
        void virtual applyEffect(uint32_t imageIndex, VkCommandBuffer commandBuffer) = 0;
        void virtual updateEffect(){};
        void virtual useDepthImage(VkImageView depthImageView){};
        // live per-uniform override values, no-op for non-ReShade effects
        void virtual updateUniformsFromConfig(Config& config){};

        // per-effect live enable gate; false records applyIdentity instead of applyEffect
        bool enabled = true;

        // re-read "<effectName>_enabled"; returns true iff the flag changed
        bool virtual updateEnabledFromConfig(Config& config)
        {
            return false;
        }

        // passthrough copy for a disabled effect, same PRESENT_SRC_KHR layout as applyEffect
        void virtual applyIdentity(uint32_t imageIndex, VkCommandBuffer commandBuffer){};

        virtual ~Effect(){};

    private:
    };
} // namespace vkBasalt

#endif // EFFECT_HPP_INCLUDED
