# Toolchain for the static arms of the experiments — compile, parse and lint, with no network.
#
# WHY THIS EXISTS. Two of the experiments read AgentTrap: 91 independently-authored malicious
# agent skills containing live attack code — workflows that delete files, harvest credentials and
# post to remote hosts. Those arms need **no network at all**, so running them under
# `--network=none` makes exfiltration impossible rather than merely unattempted.
#
# It also makes the work reproducible. Running test9 on a host otherwise needs ten tools installed
# by hand.
#
# WHAT THIS DOES NOT COVER, stated so nobody assumes more isolation than exists:
#   * the MLX local-model arms — Apple Silicon GPU is not available to a Linux container on macOS;
#   * the `agy` frontier arms — they need egress to a vendor API and carry host credentials;
#   * Claude Code subagents — they run in-process and cannot be wrapped from here.
# Those keep the isolation described in SAFETY.md, which is weaker. See that file.

FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git python3 python3-pip python3-venv nodejs npm \
    && rm -rf /var/lib/apt/lists/*

# Kotlin — compiler plus the two linters test9 and test10 exercise.
ARG KOTLIN_VERSION=2.4.10
RUN curl -sSLo /tmp/kotlin.zip \
      "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
    && unzip -q /tmp/kotlin.zip -d /opt && rm /tmp/kotlin.zip
ENV PATH="/opt/kotlinc/bin:${PATH}"

ARG DETEKT_VERSION=1.23.8
RUN curl -sSLo /usr/local/bin/detekt.jar \
      "https://github.com/detekt/detekt/releases/download/v${DETEKT_VERSION}/detekt-cli-${DETEKT_VERSION}-all.jar" \
    && printf '#!/bin/sh\nexec java -jar /usr/local/bin/detekt.jar "$@"\n' > /usr/local/bin/detekt \
    && chmod +x /usr/local/bin/detekt

ARG KTLINT_VERSION=1.8.0
RUN curl -sSLo /usr/local/bin/ktlint \
      "https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint" \
    && chmod +x /usr/local/bin/ktlint

# Java — Checkstyle, with the Google config test9 uses.
ARG CHECKSTYLE_VERSION=10.21.0
RUN curl -sSLo /usr/local/bin/checkstyle.jar \
      "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar" \
    && printf '#!/bin/sh\nexec java -jar /usr/local/bin/checkstyle.jar "$@"\n' > /usr/local/bin/checkstyle \
    && chmod +x /usr/local/bin/checkstyle

# JavaScript and Python linters.
RUN npm install -g eslint@10 --no-fund --no-audit \
    && pip3 install --no-cache-dir --break-system-packages ruff bandit

# NOTE: SwiftLint is absent. It has no maintained Linux build, so the Swift arm still runs on the
# host. That is a real gap in this image and is recorded rather than papered over — a Swift row
# produced outside the container is not produced under the same isolation.

WORKDIR /work
RUN useradd -m runner && chown -R runner /work
USER runner

# No ENTRYPOINT: the runner script decides which arm to run, so the image stays a toolchain
# rather than a single-purpose command.
CMD ["/bin/bash"]
