#pragma once

#include "ngr/backend_abi_v1.h"

#include <string>

namespace ngr {

/**
 * Validate the immutable v1 host table before handing it to a backend.
 * This checks the complete v1.0 contract; it does not load or sandbox plugins.
 */
bool validate_host_api_v1(const NgrHostApiV1& api, std::string* error = nullptr);

/**
 * Validate a backend-provided table after ngr_backend_get_api_v1 returns.
 * The caller must keep the supplying shared library loaded for the table lifetime.
 */
bool validate_backend_api_v1(const NgrBackendApiV1& api, std::string* error = nullptr);

} // namespace ngr
