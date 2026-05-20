You are implementing a new feature in this repository:



\[agentic-microservices repository](https://github.com/amollimaye/agentic-microservices?utm\_source=chatgpt.com)



Your task is to build a lightweight AI-powered “Talk To Observability” service that integrates with the EXISTING observability infrastructure already present in the repository.



IMPORTANT:

Before writing code, first analyze:



\* repository structure

\* coding conventions

\* Docker/Kubernetes setup

\* startup scripts

\* observability-agent implementation

\* configuration patterns

\* existing logging style

\* existing MCP integration patterns



Then implement using the SAME conventions and patterns.



==================================================

PRIMARY GOAL

============



Create a Python service that allows users to ask natural-language observability and troubleshooting questions such as:



\* "Why are ecommerce-service requests timing out?"

\* "Find the reason for request timeout between 11:30 PM and 11:45 PM."

\* "Show probable root cause for latency spike in product-service."

\* "Find all failures related to request id X."

\* "Why are requests failing for ecommerce-service?"

\* "Did heap usage spike before the failures?"

\* "Are thread spikes correlated with request timeouts?"



The service should:



1\. Fetch telemetry from the EXISTING observability-agent

2\. Correlate telemetry deterministically

3\. Use OpenAI only for reasoning/summarization

4\. Return concise RCA-style responses



==================================================

VERY IMPORTANT ARCHITECTURAL RULES

==================================



The LLM MUST NOT:



\* analyze huge raw log dumps

\* analyze massive metric payloads

\* perform uncontrolled recursive reasoning

\* act autonomously



The system MUST:



\* fetch telemetry

\* summarize telemetry deterministically

\* correlate findings in Python

\* send only structured findings to the LLM



This is REQUIRED.



==================================================

EXISTING OBSERVABILITY MCP SERVER

=================================



The repository already contains an observability MCP server:



\* observability-agent



It already integrates with:



\* Prometheus

\* Loki



DO NOT:



\* create another observability backend

\* duplicate telemetry infrastructure

\* introduce another MCP framework



Reuse the existing observability-agent.



==================================================

EXISTING MCP TOOLS

==================



Available tools:



\* get\_logs\_by\_request\_id

\* get\_logs\_by\_service

\* get\_error\_logs\_by\_service

\* get\_heap\_metrics

\* get\_thread\_metrics

\* get\_request\_rate

\* list\_observable\_services



Tool meanings:



1\. get\_logs\_by\_request\_id

&#x20;  Returns correlated logs for a request/correlation flow.



2\. get\_logs\_by\_service

&#x20;  Returns service logs for a time range.



3\. get\_error\_logs\_by\_service

&#x20;  Returns error logs for a service and time range.



4\. get\_heap\_metrics

&#x20;  Returns heap metric points for a service and time range.



5\. get\_thread\_metrics

&#x20;  Returns thread metric points for a service and time range.



6\. get\_request\_rate

&#x20;  Returns request-rate metrics for a service and time range.



7\. list\_observable\_services

&#x20;  Returns supported service names.



==================================================

CURRENT OBSERVABLE SERVICES

===========================



\* ecommerce-service

\* product-service

\* images-service



==================================================

EXISTING REST ENDPOINTS

=======================



The observability-agent already exposes:



\* /api/observability/logs/request/{requestId}

\* /api/observability/logs/service/{serviceName}

\* /api/observability/logs/errors/{serviceName}

\* /api/observability/metrics/heap/{serviceName}

\* /api/observability/metrics/threads/{serviceName}

\* /api/observability/metrics/request-rate/{serviceName}

\* /api/observability/services



Configured Prometheus/Loki URLs already exist in:



\* microservices/observability-agent/src/main/resources/application.yml



Reuse existing configuration conventions.



==================================================

IMPLEMENTATION REQUIREMENTS

===========================



Create a NEW lightweight Python service.



Suggested module name:



\* talk-to-observability-agent



Use:



\* Python

\* FastAPI

\* LangGraph

\* OpenAI API



Keep implementation SIMPLE.



==================================================

STRICT NON-GOALS

================



DO NOT introduce:



\* vector databases

\* RAG

\* autonomous agents

\* multi-agent systems

\* plugin architectures

\* distributed queues

\* Kafka workflows

\* memory systems

\* complex abstractions

\* speculative architecture

\* excessive configuration



DO NOT overengineer.



==================================================

HIGH-LEVEL FLOW

===============



User Query

\->

FastAPI Endpoint

\->

LangGraph Workflow

\->

Observability MCP Calls

\->

Deterministic Correlation

\->

LLM Reasoning

\->

RCA Response



==================================================

REQUIRED API

============



Create a minimal FastAPI API.



Example:



POST /api/v1/investigate



Request:



{

"query": "Why are requests timing out in ecommerce-service?"

}



Response:



{

"summary": "...",

"probableRootCause": "...",

"evidence": \[

"...",

"..."

]

}



Keep contracts clean and minimal.



==================================================

LANGGRAPH WORKFLOW

==================



Implement explicit workflow nodes.



Suggested nodes:



\* parse\_query\_node

\* identify\_service\_node

\* identify\_time\_range\_node

\* build\_investigation\_plan\_node

\* fetch\_logs\_node

\* fetch\_error\_logs\_node

\* fetch\_heap\_metrics\_node

\* fetch\_thread\_metrics\_node

\* fetch\_request\_rate\_node

\* correlation\_node

\* reasoning\_node

\* response\_node



Prefer deterministic workflows over agentic loops.



DO NOT create recursive planning systems.



==================================================

MCP INTEGRATION LAYER

=====================



Create lightweight wrappers around the existing MCP tools and/or REST endpoints.



Suggested wrappers:



\* get\_logs\_by\_request\_id()

\* get\_logs\_by\_service()

\* get\_error\_logs\_by\_service()

\* get\_heap\_metrics()

\* get\_thread\_metrics()

\* get\_request\_rate()

\* list\_observable\_services()



Keep wrappers thin and readable.



==================================================

REQUIRED INVESTIGATION CAPABILITIES

===================================



Support:



\* request timeout analysis

\* error spike analysis

\* heap spike analysis

\* thread saturation analysis

\* request-rate spike analysis

\* request flow analysis using correlation/request IDs



Example flow:



User query:

"Find why ecommerce-service requests timed out between 11:30 PM and 11:45 PM for request id X"



Expected execution:



1\. Fetch correlated request logs

2\. Fetch service error logs

3\. Fetch heap metrics

4\. Fetch thread metrics

5\. Fetch request-rate metrics

6\. Correlate anomalies

7\. Generate RCA summary



==================================================

CORRELATION ENGINE

==================



Implement deterministic correlation logic in Python.



DO NOT rely entirely on the LLM.



Examples:



heap spike + thread spike + timeout logs

\-> probable JVM/resource saturation



increased request-rate + increased error logs

\-> overload scenario



thread saturation + timeout errors

\-> request queueing/thread exhaustion



request-id correlated failures across services

\-> downstream dependency issue



Keep logic:



\* explicit

\* readable

\* maintainable



==================================================

DATA MODELS

===========



Use typed Pydantic models.



Suggested models:



\* InvestigationRequest

\* InvestigationContext

\* LogFinding

\* MetricFinding

\* CorrelationFinding

\* InvestigationResponse



Keep models small and focused.



==================================================

OPENAI INTEGRATION

==================



Use OpenAI APIs through environment configuration.



Required configuration:



\* OPENAI\_API\_KEY

\* OPENAI\_MODEL

\* OBSERVABILITY\_AGENT\_BASE\_URL

\* REQUEST\_TIMEOUT\_SECONDS



DO NOT hardcode secrets.



==================================================

PROMPTING STRATEGY

==================



The LLM should receive:



\* summarized telemetry

\* structured findings

\* deterministic correlations



NOT raw telemetry dumps.



Example:



{

"service": "ecommerce-service",

"issue": "request timeout",

"evidence": \[

"heap usage increased from 45% to 92%",

"thread count increased sharply",

"error logs contain timeout exceptions"

]

}



Expected LLM output:



\* concise explanation

\* probable root cause

\* supporting evidence



==================================================

LOGGING

=======



Use structured logging if repository already uses it.



Each investigation should include:



\* investigationId

\* query

\* execution duration

\* telemetry fetch timing



==================================================

ERROR HANDLING

==============



Handle:



\* MCP timeouts

\* unavailable observability-agent

\* malformed queries

\* invalid service names

\* empty telemetry



Return concise meaningful responses.



==================================================

CODE QUALITY REQUIREMENTS

=========================



Follow clean code principles:



\* small focused classes

\* small methods

\* meaningful naming

\* avoid duplication

\* avoid deep inheritance

\* avoid premature abstractions



Prefer:



\* composition

\* explicit orchestration

\* readability



Keep implementation maintainable by a single developer.



==================================================

SUGGESTED STRUCTURE

===================



Follow repository conventions.



Suggested structure:



talk-to-observability-agent/

app/

api/

graph/

mcp/

models/

services/

correlation/

prompts/

config/

logging/

Dockerfile

requirements.txt

README.md



Adjust only if repository conventions differ.



==================================================

START.BAT INTEGRATION

=====================



Repository startup script:



\[start.bat](https://github.com/amollimaye/agentic-microservices/blob/master/start.bat?utm\_source=chatgpt.com)



The new service MUST automatically start when start.bat runs.



Requirements:



1\. Follow EXISTING startup conventions.

2\. Extend startup flow minimally.

3\. Do NOT redesign startup architecture.

4\. Update required startup/configuration files.

5\. Respect dependency ordering.



Dependencies:



\* observability-agent

\* Prometheus

\* Loki



6\. Add minimal startup validation.

7\. Fail fast with meaningful logs if dependencies unavailable.



DO NOT introduce:



\* orchestration frameworks

\* complex service discovery

\* custom startup coordinators



==================================================

KUBERNETES / DOCKER

===================



Provide:



\* Dockerfile

\* Kubernetes manifests

\* ConfigMap example

\* Secret example



Reuse repository conventions.



==================================================

MOCK INVESTIGATION SUPPORT

==========================



The system will be used with simulated telemetry scenarios.



Support scenarios such as:



\* timeout spike

\* heap spike

\* thread saturation

\* request-rate spike

\* correlated request failures

\* downstream latency

\* service instability



==================================================

EXPECTED DELIVERABLES

=====================



Generate:



\* Python implementation

\* FastAPI endpoints

\* LangGraph workflow

\* MCP integration layer

\* correlation engine

\* Dockerfile

\* Kubernetes manifests

\* startup integration updates

\* concise README



Keep implementation:



\* complete

\* minimal

\* production-quality

\* maintainable

\* easy to understand



==================================================

IMPLEMENTATION PRIORITY

=======================



Prioritize in this order:



1\. Basic FastAPI service

2\. MCP integration wrappers

3\. LangGraph workflow

4\. Correlation engine

5\. OpenAI reasoning integration

6\. Startup integration

7\. Docker/Kubernetes integration

8\. Cleanup/refactoring



==================================================

FINAL IMPORTANT RULES

=====================



DO:



\* reuse repository patterns

\* keep code deterministic

\* keep telemetry structured

\* keep prompts concise

\* keep logic explicit

\* favor simplicity



DO NOT:



\* overengineer

\* add speculative architecture

\* create autonomous systems

\* dump huge telemetry into the LLM

\* introduce unnecessary dependencies

\* create giant abstractions



