# The observation endpoint for `run-sandboxed.sh --observe`.
#
# This container is the ONLY thing reachable from the experiment container in observe mode. It runs
# as root and holds NET_ADMIN, which is the opposite of the posture the experiment container keeps
# — deliberately so. The privilege lives with the recorder, which runs code we wrote, and never
# with the arm that reads somebody else's malware.
#
# It has no route off the host either: `run-sandboxed.sh` puts it on a Docker network created
# `--internal`. That, and not any code in this image, is what makes observation safe.

FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends iptables \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir cryptography

COPY sinkhole.py /opt/sinkhole.py
COPY entrypoint.sh /opt/entrypoint.sh
RUN chmod +x /opt/entrypoint.sh

ENTRYPOINT ["/opt/entrypoint.sh"]
