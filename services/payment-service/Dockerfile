# Build from the repository root, including when selecting a module Dockerfile.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE=services/payment-service
COPY services/service-common/ services/service-common/
RUN mvn -B -f services/service-common/pom.xml install -DskipTests
COPY services/ services/
COPY api-gateway/ api-gateway/
COPY discovery-server/ discovery-server/
COPY backend/src/main/resources/static/ backend/src/main/resources/static/
RUN case "$MODULE" in api-gateway|discovery-server|services/auth-service|services/product-service|services/order-service|services/payment-service|services/notification-service|services/recommendation-service) ;; *) exit 2 ;; esac     && mvn -B -f "$MODULE/pom.xml" package -DskipTests     && cp "$MODULE"/target/*-1.0.0.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-jammy
ARG MODULE=services/payment-service
ENV APP_MODULE=$MODULE     SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/config/     JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=10 -XX:+ExitOnOutOfMemoryError"
WORKDIR /app
RUN groupadd --gid 10001 shopease && useradd --uid 10001 --gid shopease --no-create-home shopease
COPY --from=build /tmp/app.jar /app/app.jar
COPY deploy/config/ /app/config/
COPY deploy/start-java.sh /app/start-java.sh
RUN chmod 0555 /app/start-java.sh && chown -R shopease:shopease /app
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["/app/start-java.sh"]
