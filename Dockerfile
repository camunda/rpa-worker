FROM ubuntu:24.04 AS python-headers

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3-dev \
    && python3 -c "import sys, sysconfig; print(f'{sys.version_info.major}.{sys.version_info.minor} {sysconfig.get_config_var(\"MULTIARCH\")}')" > /python-header-abi \
    && version="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')" \
    && multiarch="$(python3 -c 'import sysconfig; print(sysconfig.get_config_var("MULTIARCH"))')" \
    && mkdir -p "/headers/usr/include/${multiarch}" \
    && cp -a "/usr/include/python${version}" /headers/usr/include/ \
    && cp -a "/usr/include/${multiarch}/python${version}" "/headers/usr/include/${multiarch}/"

FROM ghcr.io/camunda/rpa-worker-base-image:202510061519

ARG TARGETARCH

COPY --from=python-headers /headers/ /
COPY --from=python-headers --chown=application:application /python-header-abi /tmp/python-header-abi
RUN python3 -c "import sys, sysconfig; print(f'{sys.version_info.major}.{sys.version_info.minor} {sysconfig.get_config_var(\"MULTIARCH\")}')" | cmp - /tmp/python-header-abi \
    && rm /tmp/python-header-abi

COPY rpa-worker-application-docker*.jar /home/application/
RUN bash -c "if [[ \"${TARGETARCH}\" == \"amd64\" ]] then rm /home/application/*aarch64*.jar; else rm /home/application/*amd64*.jar; fi"

RUN unzip -p /home/application/rpa-worker-application-docker*.jar 'BOOT-INF/lib/rpa-worker-python-requirements-*.jar' > /tmp/python-requirements.jar \
    && unzip -p /tmp/python-requirements.jar python/requirements.txt > /tmp/python-requirements.txt \
    && test -s /tmp/python-requirements.txt \
    && python3 -m venv /home/application/python/venv \
    && /home/application/python/venv/bin/pip install --no-cache-dir -r /tmp/python-requirements.txt \
    && sha256sum /tmp/python-requirements.txt | cut -d ' ' -f 1 | tr -d '\n' > /home/application/python/requirements.last \
    && rm /tmp/python-requirements.jar /tmp/python-requirements.txt

ENV CAMUNDA_RPA_SANDBOX_ENABLED=false

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dserver.address= -Dlogging.file.name= --enable-preview -jar rpa-worker*.jar"]