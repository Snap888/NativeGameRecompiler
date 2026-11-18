# ADR 0001: Runtime boundaries

**Status:** Accepted

NGR separates Android host, native runtime core, execution backends, compatibility adapters and graphics providers. This prevents one emulator/recompiler implementation from becoming the architecture of the whole product.

The first milestone uses Kotlin Activity plus JNI and C++20. Evaluate GameActivity when the runtime owns a native render loop and requires deeper lifecycle/input integration.
