package com.mcpgateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class McpStreamService {
    // 存储活跃的流处理器
    private final Map<UUID, StreamProcessor> activeProcessors = new ConcurrentHashMap<>();
    private final BillingService billingService;
    private final SubscriptionService subscriptionService;
    
    /**
     * 处理 SSE 流
     */
    public void handleSSE(UUID toolId, UUID userId, SseEmitter emitter, Consumer<String> messageHandler) {
        // 记录工具使用
        recordToolUsage(toolId, userId);
        
        StreamProcessor processor = new StreamProcessor(messageHandler);
        activeProcessors.put(toolId, processor);
        processor.start();
    }
    
    /**
     * 处理 HTTP 流
     */
    public void handleHTTPStream(UUID toolId, UUID userId, Consumer<String> chunkHandler) {
        // 记录工具使用
        recordToolUsage(toolId, userId);
        
        StreamProcessor processor = new StreamProcessor(chunkHandler);
        activeProcessors.put(toolId, processor);
        processor.start();
    }
    
    /**
     * 处理响应式流
     */
    public Flux<String> handleReactiveStream(UUID toolId, UUID userId) {
        // 记录工具使用
        recordToolUsage(toolId, userId);
        
        return Flux.create(sink -> {
            StreamProcessor processor = new StreamProcessor(message -> {
                sink.next(message);
                if (isComplete(message)) {
                    sink.complete();
                }
            });
            activeProcessors.put(toolId, processor);
            processor.start();
            
            sink.onDispose(() -> cleanup(toolId));
        });
    }
    
    /**
     * 记录工具使用
     */
    private void recordToolUsage(UUID toolId, UUID userId) {
        try {
            // 记录使用情况，默认消耗为1（每次使用计1次）
            billingService.recordUsage(toolId, userId, 1L);
        } catch (Exception e) {
            throw new RuntimeException("Failed to record tool usage", e);
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup(UUID toolId) {
        StreamProcessor processor = activeProcessors.remove(toolId);
        if (processor != null) {
            processor.stop();
        }
    }
    
    private boolean isComplete(String message) {
        // 实现你的完成判断逻辑
        return message != null && message.contains("[DONE]");
    }
    
    /**
     * 流处理器
     */
    private static class StreamProcessor {
        private final Consumer<String> messageHandler;
        private volatile boolean running = true;
        
        public StreamProcessor(Consumer<String> messageHandler) {
            this.messageHandler = messageHandler;
        }
        
        public void start() {
            // 在实际应用中，这里可能是从MCP工具获取数据的逻辑
            new Thread(() -> {
                while (running) {
                    try {
                        // 模拟数据流
                        messageHandler.accept("Processing...");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                messageHandler.accept("[DONE]");
            }).start();
        }
        
        public void stop() {
            running = false;
        }
    }
} 