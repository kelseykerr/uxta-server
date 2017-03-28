FROM java:8-jre
FROM maven:3-jdk-8
COPY pom.xml /opt/dropwizard/
WORKDIR /opt/dropwizard
RUN mvn clean install
COPY nearby-alpha.yml /opt/dropwizard/
COPY target/nearby-1.0-SNAPSHOT.jar /opt/dropwizard/
EXPOSE 8080
WORKDIR /opt/dropwizard
CMD ["java", "-jar", "-Done-jar.silent=true", "nearby-1.0-SNAPSHOT.jar", "server", "nearby-alpha.yml"]