# Java / Spring Boot Coding Standards - Copilot Instructions

> **File location:** `.github/copilot-instructions.md` at the repository root.
> Copilot Chat, Copilot code review, and Copilot coding agent read this file automatically for every request made in this repository. A few Copilot integrations require a one-time setting to enable loading it - check your IDE's Copilot settings if it doesn't seem to be picked up.

These rules apply to all Java code Copilot generates, edits, or reviews in this repository. Baseline stack: **Java 21 (LTS)**, **Spring Boot 3.5.x**, **Maven**, **MyBatis** for persistence on new projects (see §6.5 for the JPA conventions to use instead on an existing project already built on Spring Data JPA). Consistency with this file takes priority over a shorter or more "familiar" alternative.

---

## 1. Naming & Imports

- Standard Java naming: `PascalCase` for classes/interfaces/records/enums, `camelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants, all-lowercase reversed-domain packages (`com.example.collateral.service`).
- **Never use a wildcard import** (`import java.util.*;`). Import each type explicitly, even when it means a long import block.
- **Never use local-variable type inference with `var`**. Declare each local variable with its actual class or interface type, for example, `final CustomerResponse response = ...;`, not `final var response = ...;`.
- **Never use a restricted/contextual keyword as an identifier** - `record`, `var`, `yield`, `sealed`, `permits`, `non-sealed`, `module`, `requires`, `exports`, `opens`, `uses`, `provides`, `with`, `transitive` must not be used as a variable, parameter, or method name, even where the compiler allows it.
  - This is about *names*, not the *language feature*. Using the `record` type for an immutable DTO is encouraged (see §4) - only naming something `record` is disallowed.

## 2. Code Style

### 2.1 `final` on local variables
Mark every local variable `final` where it is effectively final (never reassigned). Parameters do **not** need `final`.

```java
// Good //
final BigDecimal exposure = calculateExposure(counterparty);

// Not required on the parameter below
public void settle(Counterparty counterparty) { ... }
```

### 2.2 Use `this`
Qualify instance field and method access with `this.` wherever possible - in constructors, setters, and regular instance methods. (`this` doesn't exist in static contexts - skip it there.)

```java
public CollateralValuationService(CollateralPositionRepository repository) {
    this.repository = repository;
}

public BigDecimal currentExposure() {
    return this.repository.sumOpenPositions();
}
```

### 2.3 Single-line comments
Format every single-line comment as `// comment text //` - opened *and* closed with double slashes.

```java
// Apply the regulatory haircut before rounding to the reporting scale //
final BigDecimal haircutValue = marketValue.multiply(BigDecimal.ONE.subtract(HAIRCUT_RATE));
```

Javadoc (`/** ... */`) is unaffected by this rule - it documents classes/methods/fields (§3).

### 2.4 Multiline strings
Use Java text blocks (`"""`), never manual concatenation, for any multiline literal.

```java
final String reportTemplate = """
        Counterparty: %s
        Market Value: %s
        Haircut Value: %s
        """;
```

### 2.5 Blank lines separate logical steps
Use blank lines where Java formatting conventions call for them. Separate logically distinct steps inside a method body, such as fetching data, computing a result, and returning it. Separate field groups as specified in §2.6 and declarations from the methods or blocks they introduce. Do not add blank lines within a continued statement or between tightly coupled lines. (The `// ls` markers below continue a wrapped line - see §2.8.)

```java
public CollateralValuationResponse calculateValuation(String counterpartyId) {
    final CollateralPosition position = this.repository // ls
            .findByCounterpartyId(counterpartyId) // ls
            .orElseThrow(() -> new CollateralNotFoundException(counterpartyId));

    final BigDecimal marketValue = this.marketDataClient.priceFor(position);
    final BigDecimal haircutValue = marketValue // ls
            .multiply(BigDecimal.ONE.subtract(HAIRCUT_RATE)) // ls
            .setScale(VALUATION_SCALE, RoundingMode.HALF_UP);

    return new CollateralValuationResponse(counterpartyId, marketValue, haircutValue, this.baseCurrency);
}
```

### 2.6 Field ordering
Order class members top-to-bottom as below; separate each group with a blank line. This applies to Spring-annotated fields too - a `@Value`-injected field is still just a "configuration-bound field" for ordering purposes. Within each category, group fields by their logical purpose and leave a blank line between those groups, even when adjacent groups have the same Java type. Do not add a blank line merely because adjacent fields have different types when they serve the same purpose.

For example, keep fields describing the same API identity together, separate timeout settings from identity, and separate allowed-header policy from authentication credentials. Conversely, fields with different Java types may remain together when they serve one purpose, such as a host allow-list and its private-target flag.

```java
private String apiName;
private URI baseUri;

private Duration timeout;

private List<String> allowedHeaders;

private AuthenticationConfiguration authentication;
```

1. Static final constants
2. Injected collaborators (constructor-injected beans: services, repositories, clients)
3. Configuration-bound values (constructor-injected `@Value`)
4. Mutable instance state (avoid where possible)
5. Constructor(s)
6. Public methods
7. Protected/package-private methods
8. Private helper methods

```java
public class ExampleService {

    private static final int MAX_RETRIES = 3;

    private final ExampleRepository exampleRepository;
    private final ExampleClient exampleClient;

    private final String environment;

    private BigDecimal lastResult;

    // constructor and methods follow
}
```

### 2.7 `package-info.java`
Every package needs a `package-info.java` with a short Javadoc summary of the package's purpose.

```java
/**
 * Provides services for calculating collateral valuations against live
 * market data, used for margin and risk reporting.
 */
package com.example.collateral.service;
```

### 2.8 Line separators for continued statements
Whenever a statement or expression continues onto the next physical line - a fluent/method chain, a wrapped boolean condition, a multi-line argument list, a wrapped method signature, or anything else - mark every line except the last one with `// ls` at the end. It flags "line separator: continues below," and it applies everywhere a line is split, not just method chains. This is a structural marker, not a prose comment, so it's exempt from the double-slash-close rule in §2.3.

```java
final List<String> activeCounterpartyIds = counterparties.stream() // ls
        .filter(Counterparty::isActive) // ls
        .map(Counterparty::getId) // ls
        .toList();
```

```java
if (position.isActive() // ls
        && position.getMarketValue().compareTo(BigDecimal.ZERO) > 0) {
    // ...
}
```

### 2.9 Cognitive complexity
Keep the cognitive complexity of every method at **15 or less**, measured by the standard Sonar cognitive-complexity metric. When a method would exceed 15, simplify its control flow or extract cohesive private methods instead of suppressing or ignoring the threshold.

## 3. Documentation

- Every class and every method (public, protected, package-private, *and* private) gets a concise Javadoc comment - one or two sentences is usually enough.
- Every parameter gets an `@param` description; every non-`void` return gets `@return`; thrown exceptions get `@throws`.
- Keep it useful, not padded - describe *why*/*what*, not a restatement of the signature.
- Do not use em dash punctuation in documentation or comments, including Javadoc, Markdown, and source-code comments; use the plain hyphen-minus character (`-`) instead.

```java
/**
 * Calculates the current valuation for a counterparty's collateral position.
 *
 * @param counterpartyId unique identifier of the counterparty
 * @return the calculated valuation
 * @throws CollateralNotFoundException if no position exists for the counterparty
 */
public CollateralValuationResponse calculateValuation(String counterpartyId) { ... }
```

### 3.1 `technical-specification.md`
Keep it inside a `/docs` folder at the repository root - `/docs/technical-specification.md` for a single-module project, or `/docs/<module-name>/technical-specification.md` per module in a multi-module repo. Create it when the module is created (see §3.3 for keeping it current).

```markdown
# Technical Specification - <Module/Feature Name>

## 1. Overview
## 2. Architecture
## 3. Technology Stack
## 4. Data Model
## 5. API Contracts
## 6. Design Decisions
## 7. Non-Functional Requirements
## 8. Dependencies & Integrations
```

### 3.2 `functional-specification.md`
Same `/docs` location convention, describing behavior rather than implementation:

```markdown
# Functional Specification - <Module/Feature Name>

## 1. Purpose
## 2. Actors & Stakeholders
## 3. User Stories / Use Cases
## 4. Business Rules
## 5. Inputs & Outputs
## 6. Edge Cases & Error Handling
## 7. Acceptance Criteria
```

### 3.3 Keep specs current
When a change edits behavior or design - not just when a module is first created - update the matching section(s) of `technical-specification.md` and/or `functional-specification.md` in the same change. A pull request that changes what the code does without a matching spec update is incomplete.

## 4. Design Principles

- **SRP** - one reason to change per class (valuation math in a service, persistence in a repository).
- **OCP** - extend via a new implementation (e.g., a new `HaircutStrategy`) instead of editing an existing one.
- **LSP** - an interface's implementations must honor its contract without surprising callers.
- **ISP** - prefer small, focused interfaces over large ones with unrelated methods.
- **DIP** - depend on interfaces; inject concrete implementations via the constructor.
- **DRY** / **KISS** / **YAGNI** - no duplicated logic, simplest design that satisfies the requirement, no speculative generality.
- **Law of Demeter** - avoid reaching through chains like `a.getB().getC().getD()`.
- **Composition over inheritance** - compose behavior from collaborators rather than deep class hierarchies.
- **Favor immutability** - `final` fields, immutable DTOs. Java `record` types are the preferred shape for value objects (see §1 for the naming caveat).

### Design patterns - apply where they fit

| Pattern | Typical use here |
|---|---|
| Builder | Constructing DTOs/request objects with many optional fields |
| Strategy | Interchangeable calculations (e.g., a haircut strategy per asset class) |
| Factory / Factory Method | Creating format- or protocol-specific objects |
| Template Method | A shared workflow with customizable steps (e.g., an abstract batch job) |
| Decorator | Cross-cutting behavior (caching, retry, logging) around a service/repository |
| Observer | Domain events via `ApplicationEventPublisher` / `@EventListener` |
| Repository | Domain-focused persistence abstraction, wrapping a MyBatis mapper |
| DTO + Mapper | Separating API/persistence shapes from domain objects (MapStruct) |
| Singleton | Default Spring bean scope - one instance per `ApplicationContext` |

## 5. Maven (`pom.xml`)

Standard Maven layout (`src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`), one artifact per module, semantic versioning.

- **All dependency versions live under `<properties>`**, even ones inherited from `spring-boot-starter-parent` - redeclare the parent's own property name to bump a single library without waiting for a full Boot upgrade; declare a new property for anything the Boot BOM doesn't manage (MyBatis, springdoc, MapStruct).
- **Group dependencies with comments**, in a consistent order (starters → persistence → API docs → utilities → test).
- **Include `<scm>` and team/owner information** (`<organization>`, `<developers>`).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>${spring-boot.version}</version>
        <relativePath/>
    </parent>

    <groupId>com.example.collateral</groupId>
    <artifactId>collateral-valuation-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>collateral-valuation-service</name>
    <description>Real-time collateral valuation and risk reporting service</description>

    <organization>
        <name>Risk Engineering Team</name>
        <url>https://example.internal/teams/risk-engineering</url>
    </organization>

    <scm>
        <connection>scm:git:https://github.com/your-org/collateral-valuation-service.git</connection>
        <developerConnection>scm:git:ssh://git@github.com/your-org/collateral-valuation-service.git</developerConnection>
        <url>https://github.com/your-org/collateral-valuation-service</url>
        <tag>HEAD</tag>
    </scm>

    <developers>
        <developer>
            <name>Risk Engineering Team</name>
            <email>risk-engineering@example.internal</email>
            <organization>Your Organization</organization>
        </developer>
    </developers>

    <properties>
        <!-- Core platform -->
        <java.version>21</java.version>
        <spring-boot.version>3.5.16</spring-boot.version>

        <!-- Persistence -->
        <mybatis-spring-boot.version>3.0.4</mybatis-spring-boot.version>

        <!-- API documentation -->
        <springdoc-openapi.version>2.7.0</springdoc-openapi.version>

        <!-- Utilities -->
        <mapstruct.version>1.6.3</mapstruct.version>

        <!-- Code coverage -->
        <jacoco.version>0.8.15</jacoco.version>
    </properties>

    <dependencies>

        <!-- Spring Boot starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Persistence: MyBatis -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis-spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- API documentation -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc-openapi.version}</version>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```

*(Version numbers and the MySQL driver above are illustrative - swap in your actual database and check current releases before pinning them.)*

## 6. Spring Boot Conventions

### 6.1 Constructor injection - always
Never use field or setter `@Autowired`. Inject every collaborator through the constructor.

```java
// Wrong: field injection
@Autowired
private CollateralPositionRepository repository;

// Right: constructor injection
private final CollateralPositionRepository repository;

public CollateralValuationService(CollateralPositionRepository repository) {
    this.repository = repository;
}
```

### 6.2 Layering
`Controller → Service → Repository → Mapper`. The `Repository` is a thin class that wraps a MyBatis `@Mapper` interface, so persistence-technology detail never leaks past it.

```java
@Mapper
public interface CollateralPositionMapper {

    /**
     * Finds the current collateral position for a counterparty.
     *
     * @param counterpartyId unique identifier of the counterparty
     * @return the matching position, or {@code null} if none exists
     */
    CollateralPosition findByCounterpartyId(@Param("counterpartyId") String counterpartyId);
}
```

```java
@Repository
public class CollateralPositionRepository {

    private final CollateralPositionMapper collateralPositionMapper;

    /**
     * Creates a new repository.
     *
     * @param collateralPositionMapper mapper used to query collateral position records
     */
    public CollateralPositionRepository(CollateralPositionMapper collateralPositionMapper) {
        this.collateralPositionMapper = collateralPositionMapper;
    }

    /**
     * Finds the current collateral position for a counterparty.
     *
     * @param counterpartyId unique identifier of the counterparty
     * @return the matching position, if one exists
     */
    public Optional<CollateralPosition> findByCounterpartyId(String counterpartyId) {
        final CollateralPosition position = this.collateralPositionMapper.findByCounterpartyId(counterpartyId);

        return Optional.ofNullable(position);
    }
}
```

SQL lives in `src/main/resources/mapper/CollateralPositionMapper.xml`, never inline in Java.

### 6.3 REST endpoints - sample curl + OpenAPI
Every REST endpoint's Javadoc includes a working `curl` example, and every controller/method carries `springdoc-openapi` annotations.

```java
@RestController
@Tag(name = "Collateral Valuation", description = "Endpoints for retrieving collateral valuations")
public class CollateralValuationController {

    private final CollateralValuationService collateralValuationService;

    /**
     * Creates a new controller.
     *
     * @param collateralValuationService service used to calculate collateral valuations
     */
    public CollateralValuationController(CollateralValuationService collateralValuationService) {
        this.collateralValuationService = collateralValuationService;
    }

    /**
     * Retrieves the current collateral valuation for a counterparty.
     *
     * <p>Example request:
     * <pre>{@code
     * curl -X GET "http://localhost:8080/api/v1/collateral/CPTY-1042/valuation" \
     *      -H "Authorization: Bearer <token>"
     * }</pre>
     *
     * @param counterpartyId unique identifier of the counterparty
     * @return the current collateral valuation
     */
    @Operation(summary = "Get collateral valuation", description = "Returns the current valuation for a counterparty's collateral position")
    @ApiResponse(responseCode = "200", description = "Valuation calculated successfully")
    @ApiResponse(responseCode = "404", description = "No collateral position found for the counterparty")
    @GetMapping("/api/v1/collateral/{counterpartyId}/valuation")
    public CollateralValuationResponse getValuation( // ls
            @Parameter(description = "Unique identifier of the counterparty") @PathVariable String counterpartyId) {
        return this.collateralValuationService.calculateValuation(counterpartyId);
    }
}
```

### 6.4 OpenAPI generation
Add a small `@Configuration` class for the spec metadata:

```java
@Configuration
public class OpenApiConfig {

    /**
     * Builds the OpenAPI metadata bean used to generate API documentation.
     *
     * @return the configured OpenAPI definition
     */
    @Bean
    public OpenAPI collateralServiceOpenApi() {
        return new OpenAPI() // ls
                .info(new Info() // ls
                        .title("Collateral Valuation Service API") // ls
                        .version("1.0.0") // ls
                        .description("Endpoints for retrieving real-time collateral valuations"));
    }
}
```

This exposes `/v3/api-docs` and `/swagger-ui.html` automatically. For a static file, add the `springdoc-openapi-maven-plugin` to generate `openapi.yaml` at build time into `/docs/openapi/`.

### 6.5 Persistence: JPA (existing projects only)
Default to MyBatis (§6.2) on any new project. Use the conventions below only on a project that's already built on Spring Data JPA - don't introduce JPA into a MyBatis codebase or vice versa.

- Repositories extend `JpaRepository<Entity, ID>` directly and are injected straight into services - the interface is already a sufficient abstraction, so skip the extra wrapping class that §6.2 uses for MyBatis mappers.
- Never return an `@Entity` from a controller. Map to a DTO (MapStruct, per the pattern table in §4) at the service boundary.
- Set `spring.jpa.open-in-view=false` explicitly and fetch everything a request needs inside the service's transaction, rather than relying on the Open Session in View default.
- Put `@Transactional` on service methods, not repositories or controllers; mark read-only queries `@Transactional(readOnly = true)`.
- Prefer derived query methods or `@Query` with JPQL and named parameters (`:counterpartyId`) over positional ones (`?1`).
- Watch for N+1 queries on known access patterns - use `@EntityGraph` or an explicit `JOIN FETCH` rather than relying on lazy loading.
- For audit trails, use `@EntityListeners(AuditingEntityListener.class)` with `@CreatedDate`/`@LastModifiedDate` instead of hand-rolled timestamp logic.
- Entity fields stay mutable (Hibernate needs a no-arg constructor and reflection-based field access) - group them with a blank line by role (identifier, then attributes, then audit fields), and keep the same Javadoc and `this.` conventions as every other class.

```java
@Entity
@Table(name = "collateral_position")
public class CollateralPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "counterparty_id", nullable = false)
    private String counterpartyId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // getters, setters, and constructors omitted for brevity
}
```

```java
public interface CollateralPositionRepository extends JpaRepository<CollateralPositionEntity, Long> {

    /**
     * Finds the current collateral position for a counterparty.
     *
     * @param counterpartyId unique identifier of the counterparty
     * @return the matching position, if one exists
     */
    Optional<CollateralPositionEntity> findByCounterpartyId(String counterpartyId);
}
```

Swap the MyBatis dependencies in §5 for `spring-boot-starter-data-jpa` plus your JDBC driver when working in a JPA codebase.

## 7. Testing

- Stack: **JUnit 5 (Jupiter) + Mockito + AssertJ** (all bundled by `spring-boot-starter-test`).
- All code gets unit tests. Test classes are named `<ClassUnderTest>Test`; integration tests `<ClassUnderTest>IT`.
- Every test method has `@DisplayName` with a short, descriptive sentence.
- Structure test bodies as `// prepare //` / `// act //` / `// assert //` / `// verify //` - skip whichever phase doesn't apply (e.g., no `// verify //` when there's no mock interaction to check).

```java
@ExtendWith(MockitoExtension.class)
class CollateralValuationServiceTest {

    @Mock
    private CollateralPositionRepository collateralPositionRepository;

    @Mock
    private MarketDataClient marketDataClient;

    @Test
    @DisplayName("Should apply the standard haircut when calculating a valuation")
    void shouldApplyHaircutWhenCalculatingValuation() {
        // prepare //
        final CollateralValuationService service = // ls
                new CollateralValuationService(collateralPositionRepository, marketDataClient, "USD");
        final CollateralPosition position = new CollateralPosition("CPTY-1042", "US-TREASURY-10Y");
        when(collateralPositionRepository.findByCounterpartyId("CPTY-1042")).thenReturn(Optional.of(position));
        when(marketDataClient.priceFor(position)).thenReturn(new BigDecimal("1000.0000"));

        // act //
        final CollateralValuationResponse result = service.calculateValuation("CPTY-1042");

        // assert //
        assertThat(result.haircutValue()).isEqualByComparingTo(new BigDecimal("980.0000"));

        // verify //
        verify(marketDataClient).priceFor(position);
    }
}
```

*(`CollateralPosition` and `MarketDataClient` are illustrative collaborators, omitted here for brevity.)*

### Code coverage (JaCoCo)
Every module runs JaCoCo. Any new or changed code needs **90% coverage across every counter** - line, branch/condition, method, and class - not just one blended number.

Plain JaCoCo `check` rules measure the whole bundle, not just the lines a change touches, so hitting 90% on *new* code specifically needs a diff-aware gate on top. §5's `pom.xml` already wires in `jacoco-maven-plugin` for report generation; the standard way to gate on *new* code in a Maven/Java shop is a SonarQube Quality Gate condition on **Coverage on New Code**, fed by that JaCoCo XML report:

```properties
# sonar-project.properties (or the equivalent Sonar Maven plugin property)
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Set the **Coverage on New Code** condition on the project's Quality Gate to ≥ 90% for line and condition coverage. Without SonarQube in the pipeline, fall back to a JaCoCo `check` rule at the `BUNDLE` level as an overall floor - it can't isolate new code, but it stops total coverage from sliding backward:

```xml
<execution>
    <id>check</id>
    <phase>verify</phase>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.90</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.90</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

Add this as a third `<execution>` inside the `jacoco-maven-plugin` block already in §5's `pom.xml`.

## 8. Repository Governance Files

Every project keeps these three files at the repository root, in addition to this one:

### `AGENTS.md`
Copilot combines this file with `AGENTS.md` when both exist at the repo root - GitHub's docs treat root-level `AGENTS.md` content as *primary* instructions and use both together. Keep `AGENTS.md` focused on setup/build/test commands and point it back here for style rules instead of duplicating them, so the two can't quietly drift apart.

```markdown
# AGENTS.md

## Project
Real-time collateral valuation and risk reporting service (Spring Boot, Java 21, MyBatis).

## Setup
mvn clean install

## Build & test
mvn verify

## Coding standards
See `.github/copilot-instructions.md` - it applies to all contributions, human- or agent-authored.
```

### `CODEOWNERS`
```
# Each line: file pattern → owner(s). Last match wins.
*                             @your-org/backend-team
/pom.xml                      @your-org/backend-team @your-org/platform-team
/src/main/resources/mapper/   @your-org/backend-team
.github/                      @your-org/platform-team
```

### `CHANGELOG.md`
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format, [Semantic Versioning](https://semver.org/).

```markdown
# Changelog

## [Unreleased]
### Added
### Changed
### Fixed

## [1.0.0] - 2026-09-26
### Added
- Initial release.
```

## 9. Mechanical enforcement (optional)

Several of these rules are cheap to enforce with Checkstyle bound to the Maven `validate`/`verify` phase rather than relying on review alone: `UnusedImports`/`AvoidStarImport` (§1), `FinalLocalVariable` (§2.1), `JavadocMethod`/`JavadocType` (§3), and a `RegexpSinglelineJava` rule for the `// … //` comment format (§2.3). Not built here since it wasn't asked for, but worth adding via `maven-checkstyle-plugin` if you want CI to catch drift automatically. Coverage is enforced the same way, bound to the same build - see the coverage subsection in §7.

## 10. Quick Checklist

- [ ] No wildcard imports; no restricted keywords as identifiers; use explicit types instead of `var`
- [ ] Constructor injection only - no field/setter `@Autowired`
- [ ] `final` on effectively-final locals (not required on parameters)
- [ ] `this.` used for instance field/method access
- [ ] Cognitive complexity of each method is 15 or less (§2.9)
- [ ] Blank lines separate logical code sections and field groups where appropriate (§2.5, §2.6)
- [ ] No em dashes in documentation or comments; use `-` instead
- [ ] Fields grouped per §2.6, blank line between groups
- [ ] Javadoc on every class/method, `@param` on every parameter
- [ ] `package-info.java` in every package
- [ ] Multiline strings use text blocks
- [ ] Single-line comments formatted `// comment //`; any line-wrapped statement marks every line but the last with `// ls`
- [ ] Every REST endpoint has OpenAPI annotations + a sample curl command
- [ ] Unit tests exist, `@DisplayName` + prepare/act/assert/verify structure
- [ ] New/changed code meets 90% JaCoCo coverage (line, branch, method, class) - gated on new code, not just the repo-wide average
- [ ] `pom.xml`: versions under `<properties>`, dependencies grouped with comments, `<scm>`/`<developers>` present
- [ ] New projects use MyBatis (§6.2); JPA conventions (§6.5) apply only on existing JPA projects
- [ ] `technical-specification.md` and `functional-specification.md` live under `/docs`, and are updated in the same change as any behavior/design edit
- [ ] `AGENTS.md`, `CODEOWNERS`, `CHANGELOG.md` present at the repository root

---
