FROM debian:bullseye
RUN apt update && apt install -y curl ffmpeg openjdk-17-jdk gradle

RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
RUN apt update && apt install -y nodejs

COPY . /rewind

WORKDIR /rewind

RUN ["chmod", "+x", "/rewind/bin/rewind"]
RUN ./gradlew build

CMD ["/rewind/bin/rewind"]