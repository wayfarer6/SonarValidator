# Agent local test bundle

This directory holds the locally built Prober binary and an isolated writable data
directory. It does not reuse `/var/lib/sonar_validator_prober` or modify the
installed system configuration.

## Build and test

From the repository root:

```sh
./agent_test/build_agent.sh
```

The script configures the Prober with CMake, builds all targets, runs CTest, and
copies `sonar_validator_prober` here only after the build and tests succeed. If
the executable dynamically links the ANTLR4 runtime, its matching shared library
is copied to `agent_test/lib` so the runner can find it.
The ANTLR4 C++ runtime matching the checked-in 4.13.2 parser sources must be
installed; set `ANTLR4_RUNTIME_ROOT` if it is not under `/usr`. Build
parallelism can be adjusted with `BUILD_JOBS`.

## Run

```sh
./agent_test/run_agent_test.sh
```

The runner sets:

- `SONAR_DATA_DIR` to `agent_test/data` (SQLite DB and generated `settings.conf`)
- `SONAR_CONFIG_PATH` to `agent_test/default.conf`
- `SONAR_TEMPLATE_PATH` to the Prober's `Installer/default_template.sqlite`

The fixture points to `127.0.0.1:3000`; set `SERVER_IP` in `default.conf` or
override the environment before running if a test backend is available. Stop the
long-running Agent with Ctrl+C. Generated data files are ignored by Git.
