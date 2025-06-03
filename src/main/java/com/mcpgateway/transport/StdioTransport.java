package com.mcpgateway.transport;

import com.mcpgateway.domain.MessageLog;
import com.mcpgateway.domain.Session;
import com.mcpgateway.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class StdioTransport implements McpTransport {
    private final MessageLogService messageLogService;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Session session;
    private volatile boolean running;

    @Override
    public void initialize(Session session) {
        this.session = session;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", session.getSessionToken());
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            running = true;
            startReaderThread();
            
        } catch (IOException e) {
            log.error("Failed to initialize STDIO transport", e);
            throw new RuntimeException("Failed to initialize STDIO transport", e);
        }
    }

    @Override
    public void sendMessage(String message) {
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
            messageLogService.logMessage(session.getId(), MessageLog.MessageType.REQUEST, message);
        } catch (IOException e) {
            log.error("Failed to send message via STDIO", e);
            throw new RuntimeException("Failed to send message via STDIO", e);
        }
    }

    @Override
    public void handleMessage(String message) {
        messageLogService.logMessage(session.getId(), MessageLog.MessageType.RESPONSE, message);
    }

    @Override
    public void close() {
        running = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (process != null) {
                process.destroy();
                process.waitFor();
            }
        } catch (Exception e) {
            log.error("Error closing STDIO transport", e);
        }
    }

    private void startReaderThread() {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error reading from STDIO", e);
                    messageLogService.logMessage(session.getId(), MessageLog.MessageType.ERROR, 
                            "Error reading from STDIO: " + e.getMessage());
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }
} 