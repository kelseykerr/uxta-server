FROM java:8-jre
COPY lend.yml /opt/dropwizard/
COPY target/lend-1.0-SNAPSHOT.jar /opt/dropwizard/
EXPOSE 8080
WORKDIR /opt/dropwizard
CMD ["java", "-jar", "-Done-jar.silent=true", "lend-1.0-SNAPSHOT.jar", "server", "lend-prod.yml"]