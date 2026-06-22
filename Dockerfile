FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DSA Team"
LABEL description="股票智能分析系统 - Java版"

WORKDIR /app

# 复制构建产物
COPY target/daily-stock-analysis-1.0.0.jar app.jar

# 创建数据目录
RUN mkdir -p /app/data /app/logs

# 暴露端口
EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8000/api/v1/health || exit 1

# 启动
ENTRYPOINT ["java", "-jar", "-Xms256m", "-Xmx512m", \
    "-Dspring.profiles.active=prod", \
    "-Dserver.port=8000", \
    "app.jar"]
