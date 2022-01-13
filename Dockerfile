

FROM zookeeper

WORKDIR /home
ADD server/build/libs/server.jar /home/

EXPOSE 8080
#RUN find . -type f -exec chmod 644 {} \;
#RUN find . -type d -exec chmod 755 {} \;
CMD ["java", "-jar", "server.jar"]