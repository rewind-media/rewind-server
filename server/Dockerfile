FROM debian:bullseye
RUN apt update && apt install -y openjdk-17-jdk gradle

COPY ./build/libs/server-0.0.1.jar /rewind/server.jar

WORKDIR /rewind

CMD ["java", "-jar", "/rewind/server.jar"]
