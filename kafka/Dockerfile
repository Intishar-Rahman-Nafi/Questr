# =============================================================================
# QUESTR — Kafka (self-hosted, single-node KRaft)
#
# This is a single-broker Kafka running in KRaft mode (no ZooKeeper), intended
# to run as its own service on Railway (or any container platform) alongside
# the backend. It mirrors the KRaft settings in docker-compose.yml, but with a
# lighter official image (apache/kafka instead of Confluent's cp-kafka) and
# Railway-safe networking (advertise on the internal "kafka" hostname).
#
# Two things are essential for data to survive redeploys:
#   1. A persistent volume mounted at /var/lib/kafka/data (KAFKA_LOG_DIRS).
#   2. A STABLE CLUSTER_ID — changing it forces a storage reformat (wipes data).
#
# Build:   docker build -f kafka/Dockerfile -t questr-kafka:latest kafka/
# Volume:  mount a volume at /var/lib/kafka/data
# Env:     all values below can be overridden at runtime (see ENV block).
# =============================================================================

FROM apache/kafka:3.9.0

# The apache/kafka image runs as non-root "appuser". Railway mounts volumes
# with root ownership by default, which would make /var/lib/kafka/data
# unwritable to appuser and break first-boot storage formatting. Running as
# root sidesteps volume-permission issues and is a pragmatic trade-off for a
# private, single-broker service (no public port is exposed).
USER root

ENV CLUSTER_ID=rLPyyr8ZRiC0JTAdapyi9A \
    KAFKA_NODE_ID=1 \
    KAFKA_PROCESS_ROLES=broker,controller \
    KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
    KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
    KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
    KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    KAFKA_LOG_DIRS=/var/lib/kafka/data

EXPOSE 9092 9093

# Readiness probe: the broker-api-versions handshake only succeeds once the
# broker has fully started and accepted its controller quorum. Railway and
# docker-compose both use this to delay dependent services (e.g. the backend).
HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=10 \
    CMD /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1 || exit 1
