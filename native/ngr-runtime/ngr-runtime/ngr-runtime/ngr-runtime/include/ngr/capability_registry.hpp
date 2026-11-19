#pragma once
#include <string>
#include <vector>
namespace ngr {
struct Capability { std::string id; std::string state; std::string detail; };
const std::vector<Capability>& capabilities();
std::string capability_report();
}
