FROM ghcr.io/camunda/rpa-worker-base-image:202501281531

COPY rpa-worker*.jar /home/application/

CMD ["sh", "-c", "java --enable-preview -jar rpa-worker*.jar"]