FROM ghcr.io/camunda/rpa-worker-base-image:202505131146

COPY rpa-worker*.jar /home/application/

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Dserver.address= --enable-preview -jar rpa-worker*.jar"]