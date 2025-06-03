package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamableHttpTransport implements McpTransport {
    private final MessageLogService messageLogService;
    private static final Map<String, BlockingQueue<String>> messageQueues = new ConcurrentHashMap<>();
    private Session session;

    @Override
    public void initialize(Session session) {
        this.session = session;
        messageQueues.put(session.getSessionToken(), new LinkedBlockingQueue<>());
    }

    @Override
    public void sendMessage(String message) {
        BlockingQueue<String> queue = messageQueues.get(session.getSessionToken());
        if (queue != null) {
            try {
                queue.put(message);
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.REQUEST, message);
            } catch (InterruptedException e) {
                log.error("Failed to queue message", e);
                messageLogService.logMessage(session.getId(), MessageLog.MessageType.ERROR,
                        "Failed to queue message: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void handleMessage(String message) {
        messageLogService.logMessage(session.getId(), MessageLog.MessageType.RESPONSE, message);
    }

    @Override
    public void close() {
        messageQueues.remove(session.getSessionToken());
    }

    public StreamingResponseBody getStreamingResponse(String sessionToken) {
        return outputStream -> {
            BlockingQueue<String> queue = messageQueues.get(sessionToken);
            if (queue == null) {
                return;
            }

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String message = queue.take();
                    writeMessage(outputStream, message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.error("Error writing to stream", e);
            }
        };
    }

    private void writeMessage(OutputStream outputStream, String message) throws IOException {
        outputStream.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public MediaType getMediaType() {
        return MediaType.TEXT_EVENT_STREAM;
    }
} 