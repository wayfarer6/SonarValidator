# ANTLR4 C++ runtime 4.13.2

Official runtime sources are vendored here so ordinary Prober builds need no installed ANTLR, Homebrew, Java, or network download. The `.cpp`/`.h` files in `src/` are copied without modification from the official source distribution; `CMakeLists.txt` is the project's static-library integration. Demos, upstream tests, downloaded test dependencies, IDE metadata and build products are excluded.

Origin, version and archive SHA-256 are recorded in `UPSTREAM.json`. Preserve `LICENSE.txt` and the copyright notices in source files. Installer and backend download bundles include this license.

To upgrade, verify the official release archive, replace only its `runtime/src`, `VERSION` and license, update provenance and the pinned generator hash, regenerate the project parsers with the matching tool, and run the complete parser/Prober regression suite. The generated parsers and runtime must use the same version.
