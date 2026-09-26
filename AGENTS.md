# AGENTS.md

## Project
Enterprise API MCP Gateway: generic Spring Boot 4.1.1 / Spring AI MCP 2.0.1 application using Java 21 and Maven.

## Setup
Install JDK 21 and Maven, then run `mvn clean install`.

## Build and test
- Compile: `mvn -q -DskipTests compile`
- Test: `mvn test`
- Verify tests and the JaCoCo coverage gate: `mvn verify`
- Run: `mvn spring-boot:run`

## Coding standards
See `.github/copilot-instructions.md` for Java, documentation, and test conventions. Keep API behavior generic and do not add domain-specific logic.
