# MCP Gateway Development Guide

## Development Environment Setup

### Prerequisites

1. **Java Development Kit (JDK) 21**
   ```bash
   # Check Java version
   java -version
   ```

2. **Maven 3.8.x or higher**
   ```bash
   # Check Maven version
   mvn -version
   ```

3. **IDE (Recommended: IntelliJ IDEA)**
   - Required Plugins:
     - Lombok
     - Spring Boot
     - SonarLint
   - Optional Plugins:
     - Database Navigator
     - HTTP Client

4. **Git**
   ```bash
   # Check Git version
   git --version
   ```

### Project Setup

1. **Clone and Build**
   ```bash
   git clone [repository-url]
   cd mcp-gateway
   mvn clean install
   ```

2. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

3. **Access Application**
   - API: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - H2 Console: `http://localhost:8080/h2-console`

4. **Run Tests**
   ```bash
   # Unit tests
   mvn test
   
   # Integration tests
   mvn verify
   
   # With coverage
   mvn clean test jacoco:report
   ```

## Project Structure

### Package Organization

```
src/main/java/com/mcpgateway/
├── config/                 # Configuration classes
│   ├── SecurityConfig.java
│   ├── WebClientConfig.java
│   └── CorsConfig.java
├── controller/             # REST controllers
│   ├── auth/
│   │   └── AuthController.java
│   ├── billing/
│   │   └── BillingController.java
│   ├── server/
│   │   └── McpServerController.java
│   └── transport/
│       └── SessionTransportController.java
├── domain/                 # JPA entities
│   ├── User.java
│   ├── McpServer.java
│   ├── Session.java
│   ├── UsageRecord.java
│   └── BillingRule.java
├── dto/                    # Data Transfer Objects
│   ├── auth/
│   ├── server/
│   ├── session/
│   └── billing/
├── repository/             # JPA repositories
├── service/                # Business logic
│   ├── SessionService.java
│   ├── McpServerConnectionService.java
│   ├── UsageBillingService.java
│   └── UserService.java
└── util/                   # Utility classes
    └── TimeUtil.java

src/main/resources/
├── db/migration/           # Flyway migrations
│   ├── V001__create_users.sql
│   ├── V002__create_mcp_servers.sql
│   ├── V003__create_sessions.sql
│   └── V006__create_billing_tables.sql
├── application.yml         # Configuration
└── static/                 # Static resources
```

## Coding Standards

### Java Code Style

1. **Naming Conventions**
   ```java
   // Classes: PascalCase
   public class McpServerService { }
   
   // Methods/Variables: camelCase
   private String sessionToken;
   public void createSession() { }
   
   // Constants: UPPER_SNAKE_CASE
   private static final String DEFAULT_TRANSPORT = "SSE";
   
   // Packages: lowercase
   package com.mcpgateway.service;
   ```

2. **Code Formatting**
   - Use 4 spaces for indentation
   - Maximum line length: 120 characters
   - One statement per line
   - Consistent bracket placement

3. **Documentation**
   ```java
   /**
    * Creates a new session for the specified MCP server.
    * 
    * @param serverId the MCP server ID
    * @param request the session creation request
    * @return the created session DTO
    * @throws ServerNotFoundException if server not found
    * @since 2.0.0
    */
   public SessionDTO createSession(UUID serverId, CreateSessionRequest request) {
       // Implementation
   }
   ```

### Best Practices

1. **Exception Handling**
   ```java
   // DO: Specific exception handling
   try {
       mcpServerService.connectToServer(serverId);
   } catch (ConnectionException e) {
       log.error("Failed to connect to MCP server {}: {}", serverId, e.getMessage(), e);
       throw new ServiceException("Unable to establish connection", e);
   }

   // DON'T: Generic exception catching
   try {
       // Operation
   } catch (Exception e) {
       e.printStackTrace();
   }
   ```

2. **Logging**
   ```java
   // DO: Structured logging with context
   private static final Logger log = LoggerFactory.getLogger(SessionService.class);
   
   log.info("Creating session for server {} with transport {}", serverId, transportType);
   log.error("Session creation failed for user {}: {}", userId, errorMessage, exception);

   // DON'T: System.out or generic logging
   System.out.println("Error: " + errorMessage);
   ```

3. **Dependency Injection**
   ```java
   // DO: Constructor injection with Lombok
   @Service
   @RequiredArgsConstructor
   public class SessionService {
       private final SessionRepository sessionRepository;
       private final UsageBillingService billingService;
   }

   // DON'T: Field injection
   @Service
   public class SessionService {
       @Autowired
       private SessionRepository sessionRepository;
   }
   ```

4. **Entity Design**
   ```java
   // DO: Proper entity design
   @Entity
   @Table(name = "sessions")
   @Data
   @NoArgsConstructor
   @AllArgsConstructor
   @Builder
   public class Session {
       @Id
       @GeneratedValue
       private UUID id;
       
       @Column(unique = true, nullable = false)
       private String sessionToken;
       
       @Enumerated(EnumType.STRING)
       private TransportType transportType;
       
       @ManyToOne(fetch = FetchType.LAZY)
       @JoinColumn(name = "user_id")
       private User user;
   }
   ```

## Testing Strategy

### Unit Tests

1. **Test Structure**
   ```java
   @ExtendWith(MockitoExtension.class)
   class SessionServiceTest {
       
       @Mock
       private SessionRepository sessionRepository;
       
       @Mock
       private UsageBillingService billingService;
       
       @InjectMocks
       private SessionService sessionService;
       
       @Test
       void createSession_validRequest_returnsSessionDTO() {
           // Given
           var serverId = UUID.randomUUID();
           var request = CreateSessionRequest.builder()
               .transportType(TransportType.SSE)
               .build();
           
           // When
           var result = sessionService.createSession(serverId, request);
           
           // Then
           assertThat(result).isNotNull();
           assertThat(result.getTransportType()).isEqualTo(TransportType.SSE);
       }
   }
   ```

2. **Test Naming Convention**
   ```java
   // Pattern: methodName_scenario_expectedBehavior
   @Test
   void createSession_invalidServerId_throwsServerNotFoundException() { }
   
   @Test
   void sendMessage_validMessage_recordsUsage() { }
   
   @Test
   void calculateCost_multipleRules_appliesHighestPriority() { }
   ```

### Integration Tests

1. **Test Configuration**
   ```java
   @SpringBootTest
   @AutoConfigureMockMvc
   @TestPropertySource(properties = {
       "spring.datasource.url=jdbc:h2:mem:testdb",
       "spring.jpa.hibernate.ddl-auto=create-drop"
   })
   class SessionControllerIntegrationTest {
       
       @Autowired
       private MockMvc mockMvc;
       
       @Autowired
       private TestRestTemplate restTemplate;
       
       @Test
       void createSession_authenticatedUser_returnsCreated() throws Exception {
           mockMvc.perform(post("/api/v1/mcp-server/{serverId}/sessions", serverId)
                   .header("Authorization", "Bearer " + jwtToken)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.sessionId").exists());
       }
   }
   ```

### Test Data Management

1. **Test Data Builders**
   ```java
   public class TestDataBuilder {
       public static User.UserBuilder defaultUser() {
           return User.builder()
               .username("testuser")
               .email("test@example.com")
               .password("encoded-password");
       }
       
       public static McpServer.McpServerBuilder defaultMcpServer() {
           return McpServer.builder()
               .serviceName("Test MCP Server")
               .transportType(TransportType.SSE)
               .serviceEndpoint("https://test.example.com/sse");
       }
   }
   ```

## Database Development

### Migration Guidelines

1. **Flyway Migration Files**
   ```sql
   -- V007__add_new_feature.sql
   -- Add description of changes
   
   ALTER TABLE sessions ADD COLUMN new_field VARCHAR(255);
   
   CREATE INDEX idx_sessions_new_field ON sessions(new_field);
   ```

2. **Migration Best Practices**
   - Always use versioned migrations
   - Include rollback scripts when possible
   - Test migrations on sample data
   - Use descriptive file names

### Entity Development

1. **JPA Annotations**
   ```java
   @Entity
   @Table(name = "usage_records", indexes = {
       @Index(name = "idx_usage_user_timestamp", columnList = "user_id, timestamp")
   })
   public class UsageRecord {
       @Id
       @GeneratedValue(strategy = GenerationType.AUTO)
       private UUID id;
       
       @Column(nullable = false)
       private String apiEndpoint;
       
       @CreationTimestamp
       private Timestamp timestamp;
   }
   ```

## API Development

### Controller Guidelines

1. **REST Controller Structure**
   ```java
   @RestController
   @RequestMapping("/api/v1/sessions")
   @RequiredArgsConstructor
   @Validated
   public class SessionController {
       
       private final SessionService sessionService;
       
       @PostMapping("/{serverId}/sessions")
       public ResponseEntity<SessionDTO> createSession(
               @PathVariable UUID serverId,
               @Valid @RequestBody CreateSessionRequest request,
               Authentication authentication) {
           
           var session = sessionService.createSession(serverId, request);
           return ResponseEntity.ok(session);
       }
   }
   ```

2. **Error Handling**
   ```java
   @ControllerAdvice
   public class GlobalExceptionHandler {
       
       @ExceptionHandler(ServerNotFoundException.class)
       public ResponseEntity<ErrorResponse> handleServerNotFound(ServerNotFoundException e) {
           var error = ErrorResponse.builder()
               .error("SERVER_NOT_FOUND")
               .message(e.getMessage())
               .timestamp(ZonedDateTime.now())
               .build();
           return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
       }
   }
   ```

### DTO Design

1. **Request/Response DTOs**
   ```java
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class CreateSessionRequest {
       @NotNull
       @Enumerated(EnumType.STRING)
       private TransportType transportType;
       
       @Valid
       private Map<String, String> configuration;
   }
   
   @Data
   @Builder
   public class SessionDTO {
       private UUID sessionId;
       private TransportType transportType;
       private SessionStatus status;
       private ZonedDateTime expiresAt;
   }
   ```

## Configuration Management

### Application Configuration

1. **Environment-specific Properties**
   ```yaml
   # application.yml
   spring:
     profiles:
       active: ${SPRING_PROFILES_ACTIVE:dev}
   
   ---
   spring:
     config:
       activate:
         on-profile: dev
     datasource:
       url: jdbc:h2:mem:devdb
   
   ---
   spring:
     config:
       activate:
         on-profile: prod
     datasource:
       url: ${DATABASE_URL}
   ```

2. **Configuration Classes**
   ```java
   @Configuration
   @ConfigurationProperties(prefix = "mcp.gateway")
   @Data
   public class GatewayProperties {
       private Session session = new Session();
       private Billing billing = new Billing();
       
       @Data
       public static class Session {
           private Duration defaultExpiration = Duration.ofHours(1);
           private int maxConcurrentSessions = 100;
       }
   }
   ```

## Debugging and Monitoring

### Logging Configuration

1. **Logback Configuration**
   ```xml
   <!-- logback-spring.xml -->
   <configuration>
       <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
           <encoder>
               <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
           </encoder>
       </appender>
       
       <logger name="com.mcpgateway" level="DEBUG"/>
       <logger name="org.springframework.web" level="INFO"/>
       
       <root level="INFO">
           <appender-ref ref="STDOUT"/>
       </root>
   </configuration>
   ```

### Development Tools

1. **H2 Console Access**
   ```
   URL: http://localhost:8080/h2-console
   JDBC URL: jdbc:h2:mem:mcpdb
   Username: sa
   Password: (empty)
   ```

2. **Actuator Endpoints**
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,env
   ```

## Performance Considerations

### Database Optimization

1. **Query Optimization**
   ```java
   // Use pagination for large datasets
   @Query("SELECT u FROM UsageRecord u WHERE u.userId = :userId ORDER BY u.timestamp DESC")
   Page<UsageRecord> findByUserIdOrderByTimestampDesc(
       @Param("userId") UUID userId, 
       Pageable pageable);
   
   // Use projections for specific fields
   @Query("SELECT new com.mcpgateway.dto.UsageSummaryDTO(u.apiEndpoint, COUNT(u), SUM(u.costAmount)) " +
          "FROM UsageRecord u WHERE u.userId = :userId GROUP BY u.apiEndpoint")
   List<UsageSummaryDTO> findUsageSummaryByUserId(@Param("userId") UUID userId);
   ```

2. **Connection Management**
   ```java
   @Service
   @RequiredArgsConstructor
   public class McpServerConnectionService {
       private final Map<UUID, Disposable> connections = new ConcurrentHashMap<>();
       
       @PreDestroy
       public void cleanup() {
           connections.values().forEach(Disposable::dispose);
           connections.clear();
       }
   }
   ```

## Security Guidelines

### Authentication Implementation

1. **JWT Security**
   ```java
   @Component
   public class JwtTokenProvider {
       @Value("${jwt.secret}")
       private String jwtSecret;
       
       @Value("${jwt.expiration}")
       private long jwtExpirationMs;
       
       public String generateToken(UserDetails userDetails) {
           return Jwts.builder()
               .setSubject(userDetails.getUsername())
               .setIssuedAt(new Date())
               .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
               .signWith(SignatureAlgorithm.HS512, jwtSecret)
               .compact();
       }
   }
   ```

### Input Validation

1. **Request Validation**
   ```java
   @Data
   public class CreateSessionRequest {
       @NotNull(message = "Transport type is required")
       private TransportType transportType;
       
       @Size(max = 500, message = "Description cannot exceed 500 characters")
       private String description;
       
       @Valid
       private TransportConfig config;
   }
   ```

## Deployment

### Build Process

1. **Maven Build**
   ```bash
   # Clean build
   mvn clean compile
   
   # Run tests
   mvn test
   
   # Package application
   mvn package
   
   # Skip tests (for CI/CD)
   mvn package -DskipTests
   ```

2. **Docker Support**
   ```dockerfile
   FROM openjdk:21-jre-slim
   
   COPY target/mcp-gateway-*.jar app.jar
   
   EXPOSE 8080
   
   ENTRYPOINT ["java", "-jar", "/app.jar"]
   ```

---

**Development Guide Version**: 2.0.0  
**Last Updated**: 2024-01-15 