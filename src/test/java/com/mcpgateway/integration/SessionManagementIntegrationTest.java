package com.mcpgateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpgateway.domain.McpServer;
import com.mcpgateway.domain.Session;
import com.mcpgateway.domain.User;
import com.mcpgateway.dto.session.CreateSessionRequest;
import com.mcpgateway.repository.McpServerRepository;
import com.mcpgateway.repository.SessionRepository;
import com.mcpgateway.repository.UserRepository;
import com.mcpgateway.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private McpServerRepository mcpServerRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private McpServer testServer;
    private String authToken;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        mcpServerRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);

        // Generate auth token
        authToken = jwtService.generateToken(testUser);

        // Create test MCP server
        testServer = new McpServer();
        testServer.setServiceName("Test MCP Server");
        testServer.setDescription("Test Server");
        testServer.setTransportType("sse");
        testServer.setServiceEndpoint("https://example.com/sse");
        testServer.setMessageEndpoint("https://example.com/message");
        testServer.setStatus(McpServer.ServerStatus.ACTIVE);
        testServer.setUser(testUser);
        testServer = mcpServerRepository.save(testServer);
    }

    @Test
    void createSession_WithValidRequest_ShouldCreateSession() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setServerId(testServer.getId());
        request.setTransportType("sse");

        mockMvc.perform(post("/api/v1/sessions")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.serverId").value(testServer.getId().toString()))
                .andExpect(jsonPath("$.transportType").value("sse"))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void createSession_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setServerId(testServer.getId());
        request.setTransportType("sse");

        mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSession_WithInvalidServerId_ShouldReturnBadRequest() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setServerId(UUID.randomUUID()); // Non-existent server
        request.setTransportType("sse");

        mockMvc.perform(post("/api/v1/sessions")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSession_WithValidSessionId_ShouldReturnSession() throws Exception {
        // Create session first
        Session session = new Session();
        session.setMcpServer(testServer);
        session.setTransportType("sse");
        session.setUser(testUser);
        session.setStatus(Session.SessionStatus.ACTIVE);
        session = sessionRepository.save(session);

        mockMvc.perform(get("/api/v1/sessions/" + session.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.transportType").value("sse"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getSession_WithNonExistentSessionId_ShouldReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/sessions/" + nonExistentId)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSessions_ShouldReturnAllUserSessions() throws Exception {
        // Create multiple sessions
        Session session1 = new Session();
        session1.setMcpServer(testServer);
        session1.setTransportType("sse");
        session1.setUser(testUser);
        session1.setStatus(Session.SessionStatus.ACTIVE);
        sessionRepository.save(session1);

        Session session2 = new Session();
        session2.setMcpServer(testServer);
        session2.setTransportType("websocket");
        session2.setUser(testUser);
        session2.setStatus(Session.SessionStatus.ACTIVE);
        sessionRepository.save(session2);

        mockMvc.perform(get("/api/v1/sessions")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].transportType", hasItems("sse", "websocket")));
    }

    @Test
    void closeSession_WithValidSessionId_ShouldCloseSession() throws Exception {
        // Create session first
        Session session = new Session();
        session.setMcpServer(testServer);
        session.setTransportType("sse");
        session.setUser(testUser);
        session.setStatus(Session.SessionStatus.ACTIVE);
        session = sessionRepository.save(session);

        mockMvc.perform(delete("/api/v1/sessions/" + session.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // Verify session is closed
        Session closedSession = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(closedSession.getStatus(), is(Session.SessionStatus.CLOSED));
    }

    @Test
    void fullSessionLifecycle_CreateUseAndClose_ShouldSucceed() throws Exception {
        // 1. Create session
        CreateSessionRequest createRequest = new CreateSessionRequest();
        createRequest.setServerId(testServer.getId());
        createRequest.setTransportType("sse");

        String createResponse = mockMvc.perform(post("/api/v1/sessions")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID sessionId = objectMapper.readTree(createResponse).get("id").asText(UUID.class);

        // 2. Get session details
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 3. Close session
        mockMvc.perform(delete("/api/v1/sessions/" + sessionId)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());

        // 4. Verify session is closed
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}
