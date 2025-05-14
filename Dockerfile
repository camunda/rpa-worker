FROM ghcr.io/camunda/rpa-worker-base-image:202505131146

ARG TARGETARCH

COPY rpa-worker-application-docker*.jar /home/application/
RUN bash -c "if [[ \"${TARGETARCH}\" == \"amd64\" ]] then rm /home/application/*aarch64*.jar; else rm /home/application/*amd64.jar; fi"

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dserver.address= --enable-preview -jar rpa-worker*.jar"]