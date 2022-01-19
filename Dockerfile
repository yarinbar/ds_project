

FROM zookeeper

WORKDIR /home
ADD server/build/libs/server.jar /home/

EXPOSE 8080

ENV NUM_SHARDS=2
CMD ["java", "-jar", "server.jar"]