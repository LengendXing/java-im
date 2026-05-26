FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . /app
RUN gradle :server:installDist --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/server/build/install/server /app
RUN mkdir -p /app/uploads
EXPOSE 8800 8801 8080

ENV IM_REDIS_HOST=redis
ENV IM_REDIS_PORT=6379
ENV IM_MYSQL_HOST=mysql
ENV IM_MYSQL_PORT=3306
ENV IM_MYSQL_DB=im_db
ENV IM_MYSQL_USER=im_user
ENV IM_MYSQL_PASSWORD=im_pass_2024
ENV IM_JWT_SECRET=change-me-in-production-env
ENV DUFS_URL=http://dufs:5100
ENV dufs.upload.dir=/app/uploads

CMD ["/app/bin/server"]
