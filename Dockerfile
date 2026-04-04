FROM maven:3.8-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:8-jre

LABEL maintainer="DataNote Contributors"
LABEL description="DataNote - Lightweight All-in-One Data Development Platform"

# 安装 DataX 依赖（python3 + curl）
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 curl \
    && ln -sf /usr/bin/python3 /usr/bin/python \
    && rm -rf /var/lib/apt/lists/*

# 下载 DataX
RUN curl -sSL -o /tmp/datax.tar.gz \
    "https://datax-opensource.oss-cn-hangzhou.aliyuncs.com/202309/datax.tar.gz" \
    && tar -xzf /tmp/datax.tar.gz -C /opt/ \
    && rm -f /tmp/datax.tar.gz

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8099

ENV DB_HOST=127.0.0.1 \
    DB_PORT=3306 \
    DB_USERNAME=root \
    DB_PASSWORD= \
    HIVE_URL="jdbc:hive2://127.0.0.1:10000/default;auth=noSasl" \
    HADOOP_HOME=/opt/hadoop \
    DATAX_HOME=/opt/datax \
    JAVA_OPTS="-Xms512m -Xmx1g"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
