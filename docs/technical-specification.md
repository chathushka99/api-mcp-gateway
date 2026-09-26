# Technical Specification — Enterprise API MCP Gateway

## 1. Overview

The gateway loads one or more local OpenAPI 3.x definitions and exposes each enabled operation as an individually typed MCP tool. Calls are dispatched to a configured REST API without embedding API-domain behavior in the gateway.

## 2. Architecture

- `OpenApiSpecificationLoader` reads and validates YAML/JSON OpenAPI resources, caches parsed specifications, rejects external references, and exposes a reload operation.
- `OpenApiToolGenerator` creates operation definitions, MCP-safe names, JSON input schemas, and response output schemas.
- `ToolRegistry` indexes generated tools, detects duplicate tool names and conflicting operation IDs, and only lists enabled operations.
- `McpToolHandler` receives Spring AI MCP async tool calls, tracks Micrometer metrics, and maps safe gateway failures to MCP errors.
- `GenericApiExecutor` validates arguments and forms outbound HTTP requests from the registered operation's fixed method/path and configured base URL.
- Authentication providers implement no-auth, static API key, and bearer-token credentials.
- `TargetUrlPolicy` enforces configured host allow-lists, public-target restrictions, and DNS/IP checks before dispatch.

## 3. Technology Stack

- Java 21
- Spring Boot 4.1.1
- Spring AI MCP server WebFlux starter 2.0.1 and MCP Java SDK 2.0.0
- Maven, WebClient/WebFlux, Jackson 2, Swagger Parser v3
- JUnit 5, Mockito/AssertJ via `spring-boot-starter-test`, MockWebServer for HTTP integration tests
- JaCoCo XML reporting with a Maven `verify`-phase bundle gate of at least 90% line, branch, method, and class coverage
- Micrometer through Spring Boot Actuator

## 4. Data Model

`ApiOperationDefinition` is the immutable normalized execution contract: API name, operation ID, fixed HTTP method/path, base URI, descriptions, parameters, request/response schemas, authentication, timeout, read/mutating classification, mutating permission, and permitted headers. `GeneratedTool` joins that contract with the MCP tool name, description, and schemas. `ApiResponse` holds the target HTTP status and parsed body.

## 5. API Contracts

- Configuration binds under `gateway.apis[]`, with `name`, `base-url`, `openapi`, `timeout`, optional `allowed-headers`, and `auth`.
- `gateway.security.allowed-hosts` is a required host allow-list. Private targets are denied unless explicitly enabled; non-TLS HTTP is restricted to private-target-enabled configuration.
- `gateway.operations.allow-mutating` defaults to false. Mutating tools are omitted and the executor rejects mutations unless enabled.
- MCP server uses Spring AI's Streamable HTTP transport at `/mcp`.
- MCP tool input schemas describe operation parameters and optional `body`; response output has `statusCode` and `body`.
- Target API errors are represented with safe status/category details; target error bodies and internal transport details are not returned.

## 6. Design Decisions

- Spring AI 2.0.1's native asynchronous `McpServerFeatures.AsyncToolSpecification` is used to register operation-specific tools.
- The MCP caller cannot set arbitrary URL, method, or unallow-listed headers.
- Local specification references may be resolved; remote/file references are rejected to avoid loading untrusted external resources.
- Parsed specification caching is local to the loader. `reload(location)` invalidates one cached document; registry hot replacement is not part of this iteration.
- Only JSON request bodies are supported. Target JSON and text responses are parsed as JSON/text; other response bytes are returned as Base64 text.
- Credentials must be supplied using external configuration such as environment variables or a secret manager.

## 7. Non-Functional Requirements

- Bound spec, request body, response body, connect, and request durations.
- Validate generated arguments and path segments before outbound access.
- Avoid logging credentials, headers, and request/response bodies.
- Record tool duration, outcome, target status, and timeout metrics.
- Keep the MCP transport behind an authentication/network boundary in production.

## 8. Dependencies & Integrations

Target APIs are configured HTTP(S) services. OpenAPI inputs are local classpath or filesystem documents. The server transport and native tool registration are supplied by Spring AI MCP; no database or container runtime is required.
