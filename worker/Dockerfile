FROM debian:bullseye
RUN apt update && apt install -y ffmpeg openjdk-17-jdk gradle

COPY ./build/libs/worker-0.0.1.jar /rewind/worker.jar

WORKDIR /rewind

CMD ["java", "-jar", "/rewind/worker.jar"]
