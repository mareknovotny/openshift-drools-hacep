FROM registry.access.redhat.com/ubi8/ubi-minimal
RUN microdnf install --nodocs java-1.8.0-openjdk-headless && microdnf clean all
COPY target/*-springboot.jar /deployments/app.jar
EXPOSE 8080
CMD ["java","-jar","/deployments/app.jar"]