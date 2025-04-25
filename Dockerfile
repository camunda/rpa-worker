FROM ghcr.io/camunda/rpa-worker-base-image:202505131146

ARG TARGETARCH

COPY rpa-worker-application-docker*.jar /home/application/
RUN bash -c "if [[ \"${TARGETARCH}\" == \"amd64\" ]] then rm /home/application/*aarch64*.jar; else rm /home/application/*amd64*.jar; fi"

CMD ["sh", "-c", "java -Dserver.address= --enable-preview -jar rpa-worker*.jar"]