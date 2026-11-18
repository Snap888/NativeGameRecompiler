#ifndef CONFIG_HPP_INCLUDED
#define CONFIG_HPP_INCLUDED
#include <vector>
#include <fstream>
#include <string>
#include <iostream>
#include <vector>
#include <unordered_map>
#include <cstdlib>

#include "vulkan_include.hpp"

namespace vkBasalt
{
    class Config
    {
    public:
        Config();
        Config(const Config& other);

        // Path of the config file actually opened, for the present hook's mtime watch.
        std::string configFilePath;

        template<typename T>
        T getOption(const std::string& option, const T& defaultValue = {})
        {
            T result = defaultValue;
            parseOption(option, result);
            return result;
        }

        std::unordered_map<std::string, std::string> getOptions()
        {
            return options;
        }

        void printOptions() {
            for (auto it = options.begin(); it != options.end(); it++) {
                Logger::info(it->first + " = " + it->second);
            }
        }

        void reload();

    private:
        std::unordered_map<std::string, std::string> options;

        void readConfigLine(std::string line);
        void readConfigFile(std::ifstream& stream);
        void readConfigFromEnv(std::string configLine);

        void parseOption(const std::string& option, int32_t& result);
        void parseOption(const std::string& option, float& result);
        void parseOption(const std::string& option, bool& result);
        void parseOption(const std::string& option, std::string& result);
        void parseOption(const std::string& option, std::vector<std::string>& result);

    };
} // namespace vkBasalt

#endif // CONFIG_HPP_INCLUDED
