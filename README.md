# Enterprise API MCP Gateway

A generic Java 21 MCP server that loads local OpenAPI 3.x specifications and exposes each enabled operation as a typed MCP tool. MCP clients select registered tools; they cannot submit arbitrary URLs, HTTP methods, or unrestricted headers.

## Architecture

`OpenApiSpecificationLoader` reads and validates local YAML/JSON documents, resolves local references, rejects remote references, and caches parsed documents. `OpenApiToolGenerator` converts operations into MCP names and JSON schemas. `ToolRegistry` checks operation IDs and tool names and makes enabled tools searchable. Spring AI's asynchronous MCP server registers one native tool specification for each enabled operation. `McpToolHandler` validates calls, records metrics, and delegates to `GenericApiExecutor`.

The executor constructs requests only from the configured base URL and the operation's fixed method/path. Path, query, and allow-listed header parameters and JSON request bodies are serialized generically. It bounds request/response sizes, applies timeouts and configured authentication, and returns the target status and parsed response as structured MCP content.

## Run

Requires Java 21 and Maven.

```powershell
$env:GATEWAY_APIS_0_BASE_URL = "https://api.example.com"
mvn spring-boot:run
```

The Streamable HTTP MCP endpoint is available at `http://localhost:8080/mcp`. Replace the example host and OpenAPI file in `src/main/resources/application.yml` with the target API configuration. The provided example registers `get_user` from `src/main/resources/openapi/example-api.yaml`.

## Configure APIs

`gateway.apis` accepts one or more named API configurations. `openapi` must be a `classpath:` or `file:` location. The base URL is fixed by configuration and must use a host in `gateway.security.allowed-hosts`.

```yaml
gateway:
  operations:
    allow-mutating: false
  security:
    allowed-hosts:
      - users.example.net
    allow-private-targets: false
  limits:
    connect-timeout: 3s
    max-request-body-bytes: 1048576
    max-response-body-bytes: 2097152
  apis:
    - name: user-service
      base-url: https://users.example.net/api
      openapi: classpath:openapi/users.yaml
      timeout: 8s
      allowed-headers:
        - X-Tenant
      auth:
        type: bearer
        credential: ${USER_SERVICE_TOKEN}
    - name: catalog-service
      base-url: https://catalog.example.net
      openapi: file:./specs/catalog.json
      auth:
        type: api-key
        location: header
        header-name: X-API-Key
        credential: ${CATALOG_API_KEY}
```

Authentication `type` values are `none`, `api-key`, and `bearer`. API keys can be sent as a header or query parameter by configuring `location` and `parameter-name`; credentials should come from environment variables or a secret manager, never source control. Only headers listed under that API's `allowed-headers` are accepted as tool inputs. Authorization, host, cookie, and other hop-by-hop headers are not exposed as inputs.

GET and HEAD operations are classified as reads. Other supported operations are classified as mutating, and are omitted from the MCP tool list unless `gateway.operations.allow-mutating` is explicitly set to `true`; the executor independently rejects disabled mutations. Add an explicit private-target exception only when a configured service must use a private network address. Keep MCP transport access behind an authentication and network boundary in production; the transport itself does not authenticate clients.

## OpenAPI operation to MCP tool

The example specification declares `GET /users/{userId}` with `operationId: getUser`. It produces:

```text
Name: get_user
Description: Get a user by ID.
Input: { "type": "object", "properties": { "userId": { "type": "string" } }, "required": ["userId"] }
```

A client calls the selected tool with `{"userId":"123"}`. The gateway executes `GET https://api.example.com/users/123` and returns structured content shaped like:

```json
{
  "statusCode": 200,
  "body": {
    "id": "123",
    "name": "Example User"
  }
}
```

## MCP client usage

With the MCP Python SDK, connect over Streamable HTTP, discover registered tools, and invoke a tool by its generated name:

```python
import asyncio
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

async def main():
    async with streamablehttp_client("http://localhost:8080/mcp") as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            result = await session.call_tool("get_user", {"userId": "123"})
            print(tools.tools)
            print(result.structuredContent)

asyncio.run(main())
```

## Observability and errors

Micrometer records `gateway.mcp.tool.calls`, `gateway.mcp.tool.duration`, and `gateway.mcp.tool.timeouts`, tagged with tool name, outcome, and target status where available. Logs contain tool name and a safe error category only; credentials, headers, and request/response bodies are not logged. Client-visible errors do not include stack traces or target response bodies.

## Tests

```powershell
mvn test
```

Unit tests run with `mvn test`. Integration tests are named `*IT` and run with Failsafe during `mvn verify`, alongside JaCoCo XML report generation and the bundle-wide 90% line, branch, method, and class coverage thresholds. MockWebServer tests verify tool generation, path/query construction, structured output, path validation, and default rejection of a mutating operation.
