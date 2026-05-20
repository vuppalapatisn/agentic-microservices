Generate a production-quality Python 3 script to simulate a sudden traffic spike against an ecommerce microservice running in Kubernetes.

GOAL
----
The script should create a short-duration but very high request-rate burst against a single API endpoint in the ecommerce system so that:

1. Request rate graph shows a sharp vertical spike
2. Heap usage trend increases noticeably
3. Some requests become slow
4. Some requests timeout
5. Logs contain request IDs for failed/slow requests
6. Metrics and logs can be correlated in Grafana
7. Service should recover automatically after traffic stops

This is intended for observability and performance-investigation demos.

TARGET SYSTEM
-------------
- Java Spring Boot ecommerce service
- Kubernetes deployment
- Metrics exposed through Prometheus
- Logs collected in Loki
- Grafana dashboards available
- Service already running
- API Gateway may exist
- Request tracing/correlation IDs already supported

REQUIRED SCRIPT BEHAVIOR
------------------------
The script must:

1. Generate NORMAL traffic for 30 seconds
2. Then generate EXTREME traffic spike for 2-5 minutes
3. Then completely stop traffic
4. Allow dashboards to clearly show:
   - sudden vertical request-rate spike
   - heap growth trend
   - increased latency
   - timeout increase
   - recovery after spike ends

API DETAILS
-----------

http://localhost:8090/ecommerce-service/ecommerceProducts

LOAD PROFILE
------------
Traffic profile must look like this:

Phase 1:
- warmup traffic
- 5 requests/sec
- duration 30 sec

Phase 2:
- sudden spike
- immediately jump to 300-500 requests/sec
- duration 180 sec

Phase 3:
- abrupt stop
- no gradual ramp down

This should create a graph shape like:
___||||||||||||____

REQUEST CHARACTERISTICS
-----------------------
Every request must include:

1. Unique request ID
2. Correlation ID
3. User ID
4. Random product IDs

Headers:

X-Request-ID
X-Correlation-ID
X-User-ID

Request IDs must use UUID format.

Example:
X-Request-ID: 550e8400-e29b-41d4-a716-446655440000

IMPORTANT OBSERVABILITY REQUIREMENTS
-----------------------------------
The script must log ALL of the following locally:

1. request ID
2. correlation ID
3. status code
4. response time
5. timeout events
6. exceptions
7. requests/sec statistics

This is critical because I want to investigate:
- problematic request IDs
- request-rate spikes
- heap trend correlation
- timeout correlation

SLOWDOWN REQUIREMENT
--------------------
The script should intentionally overload the service through concurrency and connection pressure.

Use:
- very high concurrency
- connection pooling
- async HTTP calls

The goal is NOT just high traffic.
The goal is:
- queue buildup
- thread pressure
- heap increase
- slow requests
- timeouts

TECHNICAL REQUIREMENTS
----------------------
Use:
- Python 3
- asyncio
- aiohttp

DO NOT use:
- threading
- requests library
- external load-testing frameworks

SCRIPT REQUIREMENTS
-------------------
The script must include:

1. Configurable concurrency
2. Configurable RPS
3. Timeout configuration
4. Retry disabled
5. Connection pooling
6. Graceful shutdown
7. Ctrl+C handling
8. Periodic statistics logging
9. Clear console output

OUTPUT METRICS
--------------
Every 5 seconds print:

- current RPS
- average latency
- p95 latency
- timeout count
- success count
- error count

TIMEOUT CONFIGURATION
---------------------
Use aggressive timeout settings:

- connect timeout
- socket timeout
- total request timeout

This should intentionally create timeout failures under load.

IMPORTANT
---------
Do NOT gradually ramp traffic.
The spike must be sudden and aggressive.

The purpose is to create:
- visible Grafana spike
- increased JVM heap usage
- slow requests
- timeout logs
- correlation opportunities

LOG FORMAT
----------
Use structured logging format like:

timestamp=<ts> requestId=<uuid> correlationId=<uuid> status=500 latencyMs=3200 error=timeout

KUBERNETES COMPATIBILITY
------------------------
The script must run:
- locally
- from a Kubernetes pod
- inside Docker container

CODE QUALITY
-------------
Requirements:
- clean code
- minimal dependencies
- production-quality structure
- low memory overhead
- no overengineering
- fully commented
- deterministic behavior

DELIVERABLES
------------
Generate:
1. complete Python script
2. requirements.txt
3. example execution commands
4. explanation of how this creates:
   - request spike
   - heap increase
   - latency increase
   - timeout symptoms

Also explain:
- which Grafana metrics will visibly change
- how to correlate problematic request IDs in Loki