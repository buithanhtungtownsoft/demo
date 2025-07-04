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

        // Sinks để lưu companyCode và sender
        Sinks.Many<String> companyCodeSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> companyCodeFlux = companyCodeSink.asFlux().take(1).cache(); // Chỉ lấy companyCode đầu tiên

        Sinks.Many<String> senderSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> senderFlux = senderSink.asFlux().take(1).cache(); // Chỉ lấy sender đầu tiên

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
                        // Phát companyCode và sender cho các luồng
                        companyCodeSink.tryEmitNext(message.getCompanyCode());
                        senderSink.tryEmitNext(message.getSender());
                        message.setTimestamp(System.currentTimeMillis());
                        // Lưu tin nhắn vào database
                        return chatMessageRepository.save(message)
                                .doOnSuccess(saved -> {
                                    log.info("Saved message: {} from {}", saved.getContent(), saved.getSender());
                                    // Phát tin nhắn tới Sinks của companyCode
                                    Sinks.Many<ChatMessage> sink = companySinks.computeIfAbsent(
                                            message.getCompanyCode(),
                                            k -> {
                                                log.info("Created new sink for companyCode: {}", k);
                                                return Sinks.many().multicast().onBackpressureBuffer();
                                            }
                                    );
                                    sink.tryEmitNext(saved);
                                    log.info("Emitted message to sink for companyCode: {}, sender: {}",
                                            message.getCompanyCode(), saved.getSender());
                                });
                    } catch (Exception e) {
                        log.error("Error parsing message: {}", e.getMessage());
                        return Mono.error(e);
                    }
                })
                .doOnError(e -> log.error("Error in incoming stream: {}", e.getMessage()))
                .onErrorContinue((e, obj) -> log.warn("Skipping invalid message: {}", e.getMessage()));

        // Gửi tin nhắn mới từ các client khác trong cùng companyCode
        Flux<WebSocketMessage> outgoing = companyCodeFlux
                .flatMap(companyCode -> senderFlux.flatMap(sender -> {
                    // Lấy Sinks cho companyCode
                    Sinks.Many<ChatMessage> sink = companySinks.computeIfAbsent(
                            companyCode,
                            k -> {
                                log.info("Created new sink for companyCode: {}", k);
                                return Sinks.many().multicast().onBackpressureBuffer();
                            }
                    );
                    // Lọc tin nhắn để không gửi lại tin nhắn của chính client
                    return sink.asFlux()
                            .filter(message -> {
                                boolean shouldSend = !message.getSender().equals(sender);
                                log.info("Filtering message from {} for sender {}: shouldSend={}",
                                        message.getSender(), sender, shouldSend);
                                return shouldSend;
                            });
                }))
                .map(message -> {
                    try {
                        String serialized = objectMapper.writeValueAsString(message);
                        log.info("Sending message to client: {}", serialized);
                        return serialized;
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
                    senderSink.tryEmitComplete();
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
