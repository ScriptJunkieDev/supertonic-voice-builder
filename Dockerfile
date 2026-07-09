FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
ENV DEBIAN_FRONTEND=noninteractive \
    SERVER_PORT=8080 \
    VOICE_BUILDER_DATA_DIR=/data \
    VOICE_BUILDER_OUTPUT_DIR=/voices \
    TRAINER_DIR=./supertonic3-voice-clone \
    TRAINER_BACKUP_DIR=./trainer-backup \
    WORKER_SCRIPT=./worker/train_voice.py \
    PYTHON_BIN=python3 \
    OMP_NUM_THREADS=16 \
    MKL_NUM_THREADS=16 \
    PIP_USER_INSTALL=false

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv python3-full git ffmpeg libsndfile1 build-essential curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/supertonic-voice-builder-0.1.0.jar /app/app.jar
COPY worker /app/worker
COPY trainer-backup /app/trainer-backup
RUN chmod +x /app/worker/train_voice.py && mkdir -p /data /voices
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
