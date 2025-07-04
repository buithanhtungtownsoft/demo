package com.example.demo.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Lưu trữ Sinks cho từng companyCode
    private final Map<String, Sinks.Many<ChatMessage>> companySinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("New WebSocket connection: {}", session.getId());

        // Sinks để lưu companyCode
        Sinks.Many<String> companyCodeSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> companyCodeFlux = companyCodeSink.asFlux().cache(1);

        // Xử lý tin nhắn nhận được
        Flux<ChatMessage> incoming = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> log.info("Received message: {}", text))
                .flatMap(text -> {
                    try {
                        ChatMessage message = objectMapper.readValue(text, ChatMessage.class);
                        if (message.getSender() == null || message.getCompanyCode() == null) {
                            log.warn("Invalid message: sender or companyCode missing");
                            return Mono.empty();
                        }
                        // Phát companyCode cho luồng outgoing
                        companyCodeSink.tryEmitNext(message.getCompanyCode());
                        message.setTimestamp(System.currentTimeMillis());
                        // Lưu tin nhắn vào database
                        return chatMessageRepository.save(message)
                                .doOnSuccess(saved -> {
                                    log.info("Saved message: {}", saved.getContent());
                                    // Phát tin nhắn tới Sinks của companyCode
                                    Sinks.Many<ChatMessage> sink = companySinks.computeIfAbsent(
                                            message.getCompanyCode(),
                                            k -> {
                                                log.info("Created new sink for companyCode: {}", k);
                                                return Sinks.many().multicast().onBackpressureBuffer();
                                            }
                                    );
                                    sink.tryEmitNext(saved);
                                });
                    } catch (Exception e) {
                        log.error("Error parsing message: {}", e.getMessage());
                        return Mono.error(e);
                    }
                })
                .doOnError(e -> log.error("Error in incoming stream: {}", e.getMessage()))
                .onErrorContinue((e, obj) -> log.warn("Skipping invalid message: {}", e.getMessage()));

        // Gửi tin nhắn mới trong cùng companyCode
        Flux<WebSocketMessage> outgoing = companyCodeFlux
                .flatMap(companyCode -> {
                    // Lấy Sinks cho companyCode, tạo mới nếu chưa có
                    Sinks.Many<ChatMessage> sink = companySinks.computeIfAbsent(
                            companyCode,
                            k -> {
                                log.info("Created new sink for companyCode: {}", k);
                                return Sinks.many().multicast().onBackpressureBuffer();
                            }
                    );
                    return sink.asFlux();
                })
                .map(message -> {
                    try {
                        return objectMapper.writeValueAsString(message);
                    } catch (Exception e) {
                        log.error("Error serializing message: {}", e.getMessage());
                        return "";
                    }
                })
                .filter(str -> !str.isEmpty())
                .map(session::textMessage)
                .doOnError(e -> log.error("Error in outgoing stream: {}", e.getMessage()))
                .onErrorContinue((e, obj) -> log.warn("Skipping error in outgoing stream: {}", e.getMessage()));

        return session.send(outgoing)
                .and(incoming)
                .doOnError(e -> log.error("WebSocket error, closing session: {}", e.getMessage()))
                .doOnTerminate(() -> {
                    log.info("WebSocket session terminated: {}", session.getId());
                    companyCodeSink.tryEmitComplete();
                    // Dọn dẹp Sinks nếu không còn client
                    companyCodeFlux.subscribe(companyCode -> {
                        Sinks.Many<ChatMessage> sink = companySinks.get(companyCode);
                        if (sink != null && sink.currentSubscriberCount() == 0) {
                            companySinks.remove(companyCode);
                            sink.tryEmitComplete();
                            log.info("Removed sink for companyCode: {}", companyCode);
                        }
                    });
                });
    }
}
