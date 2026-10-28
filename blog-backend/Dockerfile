FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 配置阿里云 Maven 镜像，加速依赖下载。
# mirrorOf 必须用 * 而不是 central：插件仓库(pluginRepositories)与依赖仓库是分开的，
# 只镜像 central 会让插件仍走 repo.maven.apache.org，实测会 Connect timed out 导致构建失败。
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

COPY pom.xml .
# 用 BuildKit 缓存挂载缓存依赖仓库：改代码重建时不必重新下载依赖
RUN --mount=type=cache,target=/root/.m2/repository mvn dependency:go-offline -B

COPY src ./src

# 镜像构建时执行单元测试，测试失败则构建失败。
# 需要快速重建时可用 --build-arg SKIP_TESTS=true 跳过。
ARG SKIP_TESTS=false
RUN --mount=type=cache,target=/root/.m2/repository mvn clean package -B -DskipTests=${SKIP_TESTS}

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]