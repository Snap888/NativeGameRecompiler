# ADR 0002: First static-recompilation proof of concept

**Status:** Accepted, subject to input availability

Use N64/MIPS and N64Recomp as the first static-recompilation experiment. It provides a bounded path from supported MIPS code to generated C/C++ that can be compiled for ARM64.

This is a proof of concept, not evidence that NGR can statically recompile arbitrary Windows, Linux, console or mobile games. A legally distributable test binary and required metadata are prerequisites.
