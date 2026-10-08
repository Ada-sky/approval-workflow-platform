FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/Dockerfile backend/Dockerfile
COPY backend/src backend/src
COPY compose.yaml compose.yaml
COPY .dockerignore .dockerignore
COPY tools/development tools/development
COPY tools/docker tools/docker
RUN mvn -B -ntp -f backend/pom.xml package
RUN mkdir -p /runtime && cd /runtime && jar -xf /workspace/backend/target/hpoa-1.0-SNAPSHOT.jar BOOT-INF/lib BOOT-INF/classes && javac -encoding UTF-8 -cp 'BOOT-INF/lib/*:BOOT-INF/classes' -d seed /workspace/tools/docker/DockerDemoSeed.java

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 hpoa && useradd --uid 10001 --gid hpoa --no-create-home hpoa
WORKDIR /app
COPY --from=build /workspace/backend/target/hpoa-1.0-SNAPSHOT.jar app.jar
COPY --from=build /runtime/BOOT-INF/lib lib/
COPY --from=build /runtime/BOOT-INF/classes classes/
COPY --from=build /runtime/seed seed/
COPY tools/development/development-fixture.sql demo-fixture.sql
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
