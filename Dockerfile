FROM adoptopenjdk/openjdk12:alpine-slim

# install curl, unzip, and bash to run gradle
RUN apk --no-cache add curl unzip bash

ENV GRADLE_HOME /gradle-5.4
ENV PATH $PATH:$GRADLE_HOME/bin

RUN curl -L https://services.gradle.org/distributions/gradle-5.4-bin.zip -o gradle-5.4-bin.zip && unzip gradle-5.4-bin.zip && rm gradle-5.4-bin.zip

# create and copy work directory
WORKDIR /app

ENTRYPOINT [ "gradle", "run" ]