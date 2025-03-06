FROM ghcr.io/camunda/rpa-worker-base-image:202501291438

COPY rpa-worker*.jar /home/application/

CMD ["sh", "-c", "java -Dserver.address= --enable-preview -jar rpa-worker*.jar"]