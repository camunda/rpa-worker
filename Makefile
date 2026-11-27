SHELL := /bin/bash
.DEFAULT_GOAL := build

WORKER_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ROOT := $(abspath $(WORKER_DIR)/..)
BUILD_DIR := $(WORKER_DIR)/.local-build
INIT_SCRIPT := $(BUILD_DIR)/local-artifacts.init.gradle
PYTHON ?= python3.12
WORKER_VERSION ?= 1.3.3
RUNTIME_VERSION ?= 0.3.1
NATIVE_PLATFORM ?= darwin
NATIVE_ARCH ?= $(shell uname -m | sed -e 's/^arm64$$/aarch64/' -e 's/^x86_64$$/amd64/')
JAVA21_HOME ?= $(firstword $(wildcard $(HOME)/.sdkman/candidates/java/21*-tem) $(shell /usr/libexec/java_home -v 21 2>/dev/null))
JAVA25_HOME ?= $(firstword $(wildcard $(HOME)/.sdkman/candidates/java/25*-tem) $(shell /usr/libexec/java_home -v 25 2>/dev/null))

define GRADLE_INIT
allprojects {
    repositories {
        exclusiveContent {
            forRepository {
                maven { url = uri("$(BUILD_DIR)/maven") }
            }
            filter {
                includeModule("io.camunda.rpa", "rpa-worker-python-requirements")
                includeModule("io.camunda.rpa", "rpa-worker-static-runtime")
            }
        }
    }
    plugins.withId("maven-publish") {
        publishing.repositories.maven {
            name = "LocalBuild"
            url = uri("$(BUILD_DIR)/maven")
        }
    }
    afterEvaluate {
        if (rootProject.name == "rpa-worker" && project.name == "rpa-worker-application") {
            def wheels = fileTree("$(BUILD_DIR)/wheels") {
                include "camunda_utils-*.whl"
            }.files
            if (wheels.size() != 1) {
                throw new GradleException("Expected exactly one local camunda-utils wheel.")
            }
            def wheel = wheels.first()
            tasks.named("processFunctionalTestResources") {
                inputs.file(wheel)
                filesMatching("python/requirements-ftest.txt") {
                    filter { line ->
                        line.startsWith("camunda-utils ") ? "camunda-utils @ " + wheel.toURI().toString() : line
                    }
                }
            }
        }
        if (rootProject.name == "rpa-worker-static-runtime"
                && project.name == "rpa-worker-static-runtime"
                && project != rootProject) {
            publishing.publications.named("maven") {
                setArtifacts([tasks.named("jar").get()])
            }
        }
    }
}
endef
export GRADLE_INIT

.PHONY: build check wheel runtime jar test test-python test-frontend test-runtime test-worker test-e2e help

build: runtime
	cd "$(ROOT)/rpa-worker" && JAVA_HOME="$(JAVA25_HOME)" ./gradlew \
		-I "$(INIT_SCRIPT)" -Pversion="$(WORKER_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		:rpa-worker-application:nativeCompile :rpa-worker-application:zipNativeImage
	@echo "Executable: $(ROOT)/rpa-worker/rpa-worker-application/build/native/nativeCompile/"
	@echo "ZIP: $(ROOT)/rpa-worker/rpa-worker-application/build/distributions/"
	@echo "Local testing only: Python requirements reference $(BUILD_DIR)/wheels."

check:
	@test -f "$(ROOT)/rpa-python-libraries/camunda-utils/pyproject.toml" || \
		{ echo "Clone rpa-python-libraries alongside rpa-worker."; exit 1; }
	@test -x "$(ROOT)/rpa-worker-static-runtime/gradlew" || \
		{ echo "Clone rpa-worker-static-runtime alongside rpa-worker."; exit 1; }
	@command -v "$(PYTHON)" >/dev/null || { echo "Install Python 3.12 or set PYTHON."; exit 1; }
	@test -x "$(JAVA21_HOME)/bin/java" || { echo "Set JAVA21_HOME to a JDK 21 installation."; exit 1; }
	@test -x "$(JAVA25_HOME)/bin/java" || { echo "Set JAVA25_HOME to a JDK 25 installation."; exit 1; }
	@test "$(NATIVE_PLATFORM)" = darwin || { echo "This build currently supports macOS only."; exit 1; }
	@test "$(NATIVE_ARCH)" = aarch64 -o "$(NATIVE_ARCH)" = amd64 || \
		{ echo "Set NATIVE_ARCH to aarch64 or amd64."; exit 1; }
	@xcode-select -p >/dev/null || { echo "Install Xcode command-line tools: xcode-select --install"; exit 1; }
	@mkdir -p "$(BUILD_DIR)/wheels"
	@printf '%s\n' "$$GRADLE_INIT" > "$(INIT_SCRIPT)"

wheel: check
	"$(PYTHON)" -m venv "$(BUILD_DIR)/tools"
	"$(BUILD_DIR)/tools/bin/python" -m pip wheel --no-deps \
		--wheel-dir "$(BUILD_DIR)/wheels" "$(ROOT)/rpa-python-libraries/camunda-utils"

runtime: wheel
	@set -euo pipefail; \
	wheels=("$(BUILD_DIR)"/wheels/camunda_utils-*.whl); \
	test "$${#wheels[@]}" -eq 1 && test -f "$${wheels[0]}" || \
		{ echo "Expected exactly one camunda-utils wheel in $(BUILD_DIR)/wheels."; exit 1; }; \
	wheel_uri=$$("$(BUILD_DIR)/tools/bin/python" -c \
		'import pathlib, sys; print(pathlib.Path(sys.argv[1]).as_uri())' "$${wheels[0]}"); \
	cd "$(ROOT)/rpa-worker-static-runtime"; \
	JAVA_HOME="$(JAVA21_HOME)" ./gradlew -I "$(INIT_SCRIPT)" \
		-Pversion="$(RUNTIME_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		"-PcamundaUtilsVersion=@ $$wheel_uri" \
		:rpa-worker-static-runtime:clean; \
	JAVA_HOME="$(JAVA21_HOME)" ./gradlew -I "$(INIT_SCRIPT)" \
		-Pversion="$(RUNTIME_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		"-PcamundaUtilsVersion=@ $$wheel_uri" \
		:rpa-worker-static-runtime:buildRuntime; \
	./rpa-worker-static-runtime/build/dist/runtime/robot \
		--outputdir "$(BUILD_DIR)/test-results/runtime" \
		--dryrun rpa-worker-static-runtime/test.robot; \
	JAVA_HOME="$(JAVA21_HOME)" ./gradlew -I "$(INIT_SCRIPT)" \
		-Pversion="$(RUNTIME_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		"-PcamundaUtilsVersion=@ $$wheel_uri" \
		:rpa-worker-python-requirements:publishAllPublicationsToLocalBuildRepository \
		:rpa-worker-static-runtime:publishAllPublicationsToLocalBuildRepository \
		-x :rpa-worker-static-runtime:testRuntime

jar: runtime
	cd "$(ROOT)/rpa-worker" && JAVA_HOME="$(JAVA25_HOME)" ./gradlew \
		-I "$(INIT_SCRIPT)" -Pversion="$(WORKER_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		:rpa-worker-application:bootJar

test:
	$(MAKE) -C "$(WORKER_DIR)" --keep-going test-python test-frontend test-runtime test-worker test-e2e

$(BUILD_DIR)/test-python/bin/python:
	"$(PYTHON)" -m venv "$(BUILD_DIR)/test-python"

$(BUILD_DIR)/test-python/.dependencies: $(ROOT)/rpa-python-libraries/requirements.txt $(BUILD_DIR)/test-python/bin/python
	"$(BUILD_DIR)/test-python/bin/python" -m pip install \
		-r "$(ROOT)/rpa-python-libraries/requirements.txt"
	@touch "$@"

test-python: $(BUILD_DIR)/test-python/.dependencies
	cd "$(ROOT)/rpa-python-libraries" && "$(BUILD_DIR)/test-python/bin/python" -m pytest \
		--junitxml="$(BUILD_DIR)/test-results/python.xml"

test-frontend:
	@test -f "$(ROOT)/rpa-frontend/package.json" || \
		{ echo "Clone rpa-frontend alongside rpa-worker."; exit 1; }
	cd "$(ROOT)/rpa-frontend" && npm run all

test-runtime: runtime
	@set -euo pipefail; \
	wheels=("$(BUILD_DIR)"/wheels/camunda_utils-*.whl); \
	test "$${#wheels[@]}" -eq 1 && test -f "$${wheels[0]}" || \
		{ echo "Expected exactly one camunda-utils wheel in $(BUILD_DIR)/wheels."; exit 1; }; \
	wheel_uri=$$("$(BUILD_DIR)/tools/bin/python" -c \
		'import pathlib, sys; print(pathlib.Path(sys.argv[1]).as_uri())' "$${wheels[0]}"); \
	cd "$(ROOT)/rpa-worker-static-runtime" && JAVA_HOME="$(JAVA21_HOME)" ./gradlew \
		-I "$(INIT_SCRIPT)" -Pversion="$(RUNTIME_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		"-PcamundaUtilsVersion=@ $$wheel_uri" \
		build --console=plain

test-worker: runtime
	cd "$(ROOT)/rpa-worker" && JAVA_HOME="$(JAVA25_HOME)" ./gradlew \
		-I "$(INIT_SCRIPT)" -Pversion="$(WORKER_VERSION)" \
		-PnativePlatform="$(NATIVE_PLATFORM)" -PnativeArch="$(NATIVE_ARCH)" \
		build functionalTest -x :rpa-worker-e2e-tests:test --console=plain

test-e2e:
	@test -x "$(JAVA25_HOME)/bin/java" || { echo "Set JAVA25_HOME to a JDK 25 installation."; exit 1; }
	@test -n "$$CAMUNDA_RPA_API_TOKEN" || \
		{ echo "E2E prerequisite missing: export the CAMUNDA_RPA_API_TOKEN used to provision the container."; exit 1; }
	@test -n "$$CAMUNDA_RPA_E2E_PATHTOWORKER" && test -x "$$CAMUNDA_RPA_E2E_PATHTOWORKER" || \
		{ echo "E2E prerequisite missing: set CAMUNDA_RPA_E2E_PATHTOWORKER to an executable native worker."; exit 1; }
	@curl --silent --show-error --fail --max-time 10 --noproxy '*' \
		http://rpa-worker.local:36228/actuator/health >/dev/null || \
		{ echo "E2E prerequisite missing: provision the Camunda/Kubernetes E2E environment, including rpa-worker.local:36228."; exit 1; }
	cd "$(ROOT)/rpa-worker" && JAVA_HOME="$(JAVA25_HOME)" ./gradlew \
		:rpa-worker-e2e-tests:test --console=plain

help:
	@echo "make        Build local Python dependencies, bundled runtime, native worker, and ZIP."
	@echo "make jar    Build local dependencies and a runnable worker JAR with bundled runtime."
	@echo "make check  Check prerequisites and generate the local Gradle helper."
	@echo "make test   Run all four RPA repositories' checks, continuing independent suites on failure."
	@echo "            Rebuilds the local runtime; worker build includes unit and functional tests."
	@echo "            Frontend runs lint, headless Chrome tests, and build; Python runs pytest."
	@echo "Individual goals: test-python, test-frontend, test-runtime, test-worker, test-e2e."
	@echo "Infrastructure E2E stays in make test and fails explicitly when prerequisites are missing."
	@echo "E2E requires CAMUNDA_RPA_E2E_PATHTOWORKER and a pre-provisioned Camunda/Kubernetes environment."
	@echo "Requires frontend npm dependencies and Chrome; Python test dependencies install in a local venv."
	@echo "Python and runtime reports: $(BUILD_DIR)/test-results; worker reports: */build/reports/tests."
	@echo "Overrides: JAVA21_HOME, JAVA25_HOME, PYTHON, NATIVE_ARCH, WORKER_VERSION, RUNTIME_VERSION."
	@echo "Artifacts are for local testing only. No remote publication or tracked version edits."
