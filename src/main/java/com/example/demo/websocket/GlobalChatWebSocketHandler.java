package com.example.demo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class GlobalChatWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Sinks.Many<RawMessage> globalSink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("New connection: {}", sessionId);

        // Nhận message và validate JSON
        Flux<String> incoming = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    if (isValidJson(payload)) {
                        log.info("Valid JSON from {}: {}", sessionId, payload);
                        globalSink.tryEmitNext(new RawMessage(sessionId, payload));
                        return Mono.empty();
                    } else {
                        log.warn("Invalid JSON from {}: {}", sessionId, payload);
                        // Có thể chọn: return session.close() để ngắt kết nối luôn
                        return Mono.empty();
                    }
                });

        // Gửi lại JSON hợp lệ từ người khác
        Flux<WebSocketMessage> outgoing = globalSink.asFlux()
                .filter(msg -> !msg.senderId().equals(sessionId))
                .map(msg -> session.textMessage(msg.payload()));

        return session.send(outgoing)
                .and(incoming.then())
                .doFinally(sig -> log.info("Session {} closed ({})", sessionId, sig));
    }

    private boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record RawMessage(String senderId, String payload) {}
}