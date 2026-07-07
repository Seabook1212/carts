# carts

`carts` is the shopping-cart service used in the enhanced Sock Shop system for the EviRCA microservice root cause analysis benchmark.

This repository is based on the original Sock Shop carts service, but has been modernized and instrumented for telemetry-rich RCA experiments described in:

**EviRCA: An Evidence-Aware Skill-Based LLM Agent and a Telemetry-Rich Multi-Modal Benchmark for Microservice Root Cause Analysis**

The enhanced Sock Shop benchmark provides synchronized metrics, logs, traces, service topology, fault-injection artifacts, upgraded service implementations, and fine-grained labels for reproducible microservice RCA evaluation.

## Role in the Benchmark

The carts service stores and manages users' shopping carts. In the Sock Shop request flow it is called by the front-end service and persists cart data in MongoDB.

In the EviRCA benchmark, this service contributes application, database, log, and trace evidence used for service-level, pod-level, service-fault, and pod-fault RCA tasks.

## Main Changes from the Original Service

- Migrated to Java 17 and Spring Boot 3.4.1.
- Replaced legacy tracing support with Micrometer Tracing and Brave.
- Exposes Prometheus-compatible metrics through Spring Boot Actuator and `/metrics`.
- Adds Zipkin-compatible trace export.
- Filters health and metrics endpoints from tracing to reduce telemetry noise.
- Adds Kubernetes metadata tags to exported spans when pod, namespace, node, and container environment variables are available.
- Uses trace-aware structured log patterns with `traceId` and `spanId`.
- Improves MongoDB connection configuration for benchmark deployments.
- Enables Chaos Monkey Spring Boot integration for controlled Java-service fault injection.

## Service Interfaces

Primary cart item endpoints:

- `GET /carts/{customerId}/items`
- `GET /carts/{customerId}/items/{itemId}`
- `POST /carts/{customerId}/items`
- `PATCH /carts/{customerId}/items`
- `DELETE /carts/{customerId}/items/{itemId}`

Operational endpoints:

- `GET /health`
- `GET /metrics`
- `GET /actuator/prometheus`

API specification files are available in [`api-spec/`](api-spec/).

## Configuration

Important runtime settings are defined in [`src/main/resources/application.properties`](src/main/resources/application.properties).

Common environment variables:

- `port`: service port, defaults to `8081`.
- `zipkin_host`: Zipkin or Jaeger collector host, defaults to `jaeger-collector.observability.svc.cluster.local`.
- `SPRING_PROFILES_ACTIVE`: Spring profile, defaults to `chaos-monkey`.
- `POD_NAME`, `POD_NAMESPACE`, `NODE_NAME`, `CONTAINER_NAME`: optional Kubernetes metadata added to traces.

The default MongoDB URI targets the Sock Shop Kubernetes namespace:

```properties
spring.data.mongodb.uri=mongodb://carts-db.sock-shop.svc.cluster.local:27017/data?socketTimeoutMS=30000&connectTimeoutMS=10000&serverSelectionTimeoutMS=10000&maxPoolSize=50
```

For local development, override this property with a reachable MongoDB instance.

## Build

```bash
mvn -DskipTests package
```

## Test

Run Java unit tests:

```bash
mvn test
```

Run integration tests:

```bash
mvn verify
```

The legacy test wrapper is still available:

```bash
./test/test.sh unit.py
```

## Run Locally

Start a MongoDB instance that the service can reach, then run:

```bash
mvn spring-boot:run
```

Check the service:

```bash
curl http://localhost:8081/health
curl http://localhost:8081/metrics
```

## Docker

Build an image:

```bash
GROUP=weaveworksdemos COMMIT=test ./scripts/build.sh
```

Push an image:

```bash
GROUP=weaveworksdemos COMMIT=test ./scripts/push.sh
```

