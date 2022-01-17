

FROM gradle:6.9-jdk17-alpine AS build

#WORKDIR /home
#ADD server/build/libs/server.jar /home/
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon


FROM openjdk:8-jre-slim

COPY --from=build /home/gradle/src/build/libs/*.jar /app/
EXPOSE 8080
#
#WORKDIR /home/shitcoin
#ADD . .
#
#
#ENV NUM_SHARDS=2
#CMD ["java", "-jar", "server/build/libs/server.jar"]