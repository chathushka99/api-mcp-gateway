# Functional Specification — Enterprise API MCP Gateway

## 1. Purpose

Provide MCP clients with discoverable, strongly described tools for operations documented by configured OpenAPI 3.x specifications. The gateway handles protocol adaptation and HTTP transport, not API-specific business behavior.

## 2. Actors & Stakeholders

- MCP client: discovers and invokes registered tools.
- Gateway operator: configures API base URLs, OpenAPI files, allowed hosts, credentials, and operation policy.
- Target REST API: receives requests and returns the operation response.

## 3. User Stories / Use Cases

- As an operator, I can configure multiple named API specs and their fixed target base URLs.
- As an MCP client, I can discover each enabled OpenAPI operation as a separate tool with a meaningful description and schema.
- As an MCP client, I can invoke an operation using documented parameters and receive structured status/body output.
- As an operator, I can enable mutating API operations explicitly after reviewing the consequences.
- As an operator, I can rotate credentials through external configuration without changing source code.

## 4. Business Rules

- A client selects only from registered operation tools and cannot supply arbitrary URLs, methods, or unrestricted headers.
- GET and HEAD operations are read-only. Other supported HTTP methods are mutating and are disabled by default.
- A configured API's base URL host must be allow-listed. Private IPs/DNS results are blocked unless explicitly opted into.
- Required parameters/body fields and declared JSON types are validated before sending a request.
- OpenAPI header parameters must be allow-listed by configuration and cannot use sensitive or hop-by-hop header names.
- Non-2xx response bodies, credentials, internal host details, and stack traces are not disclosed to MCP callers.
- Target API response bodies are not cached.

## 5. Inputs & Outputs

Inputs consist of operator configuration plus MCP arguments shaped from each operation's path/query/header parameters and optional JSON request body. Output contains target status and parsed response body in structured MCP content. An error result describes the safe failure category without exposing target body contents or secrets.

## 6. Edge Cases & Error Handling

The gateway reports safe errors for malformed/unsupported specs, duplicate operation/tool identifiers, unknown/disabled tools, missing or invalid arguments, unsafe paths/targets, oversized bodies, timeout/connection failures, target 4xx/5xx, and malformed JSON responses. Empty responses omit the body. Non-JSON binary response bytes are represented as Base64 text.

## 7. Acceptance Criteria

- `GET /users/{userId}` with `operationId: getUser` is discoverable as `get_user`.
- Calling it with `{"userId":"123"}` dispatches a GET to the configured base URL plus `/users/123` and returns structured output.
- Query parameters are encoded; declared headers are sent only when allow-listed.
- JSON request bodies are serialized and bounded.
- A POST operation is not listed or executed while mutating operations are disabled.
- Arbitrary URL/method selection, unsafe targets, malformed arguments, and oversized requests/responses do not dispatch successfully.
- Errors do not disclose target response bodies, authorization credentials, or internal exceptions.
