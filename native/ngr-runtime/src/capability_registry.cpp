#include "ngr/capability_registry.hpp"
#include "ngr/backend_abi_v1.h"
static_assert(NGR_BACKEND_ABI_V1_MAJOR == 1u, "NGR backend ABI version must be explicit");
#include <sstream>
namespace ngr {
const std::vector<Capability>& capabilities() {
 static const std::vector<Capability> items{
  {"host.android.arm64","available","Android ARM64 host is the only configured target"},
  {"graphics.vulkan.enumeration","available","Physical-device enumeration through system Vulkan loader"},
  {"input.android.gamepad-discovery","available","Android gamepad and joystick discovery"},
  {"package.ngrpack.validation","available","Strict manifest, path, file-inventory and SHA-256 validation"},
  {"package.ngrpack.installation","available","Bounded NGRPACK staging install, update, inventory, removal and crash recovery"},
  {"package.ngrpack.signature-verification","planned","Signed release-package trust verification; unsigned payload execution is prohibited"},
  {"backend.plugin-abi","planned","C ABI v1 contract defined; loader, conformance tests and lifecycle integration remain"},
  {"backend.n64recomp","planned","Static recompilation proof of concept; not integrated"},
  {"backend.fex","planned","Dynamic Linux x86-64 translation; not integrated"},
  {"backend.box64","planned","Dynamic Linux x86-64 translation; not integrated"},
  {"graphics.turnip-provider","planned","Isolated Adreno Vulkan provider; not integrated"},
  {"graphics.dxvk","planned","D3D8-11 translation layer; not integrated"},
  {"graphics.vkd3d-proton","planned","D3D12 translation layer; not integrated"},
  {"audio.oboe","planned","Android low-latency audio backend; not integrated"}
 };
 return items;
}
std::string capability_report() {
 std::ostringstream out;
 for (const auto& item : capabilities())
  out << item.id << " [" << item.state << "] — " << item.detail << "\n";
 return out.str();
}
}
