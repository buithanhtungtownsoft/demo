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
public class RoomChatWebSocketHandler implements WebSocketHandler {

    @Autowired
    private RoomMessageRepository RoomMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Store Sinks for each roomId
    private final Map<String, Sinks.Many<RoomMessage>> roomSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("New WebSocket connection: {}", session.getId());

        // Sinks to capture roomId and sender
        Sinks.Many<String> roomIdSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> roomIdFlux = roomIdSink.asFlux().take(1).cache(); // Capture first roomId

        Sinks.Many<String> senderSink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<String> senderFlux = senderSink.asFlux().take(1).cache(); // Capture first sender

        // Handle incoming messages
        Flux<RoomMessage> incoming = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> log.info("Received message: {}", text))
                .flatMap(text -> {
                    try {
                        RoomMessage message = objectMapper.readValue(text, RoomMessage.class);
                        String action = message.getAction(); // "CREATE", "JOIN", or "CHAT"
                        if (message.getSender() == null || message.getRoomId() == null || action == null) {
                            log.warn("Invalid message: sender, roomId, or action missing");
                            return Mono.empty();
                        }

                        // Emit roomId and sender for CREATE or JOIN actions
                        if ("CREATE".equals(action) || "JOIN".equals(action)) {
                            roomIdSink.tryEmitNext(message.getRoomId());
                            senderSink.tryEmitNext(message.getSender());
                            log.info("{} room request: roomId={}, sender={}", action, message.getRoomId(), message.getSender());
                            if ("CREATE".equals(action)) {
                                // Initialize sink for new room
                                roomSinks.computeIfAbsent(message.getRoomId(), k -> {
                                    log.info("Created new sink for roomId: {}", k);
                                    return Sinks.many().multicast().onBackpressureBuffer();
                                });
                            }
                            // Notify client of successful action
                            message.setTimestamp(System.currentTimeMillis());
                            return RoomMessageRepository.save(message)
                                    .doOnSuccess(saved -> log.info("Saved {} action: roomId={}, sender={}",
                                            action, saved.getRoomId(), saved.getSender()));
                        } else if ("CHAT".equals(action)) {
                            // Handle chat messages
                            message.setTimestamp(System.currentTimeMillis());
                            return RoomMessageRepository.save(message)
                                    .doOnSuccess(saved -> {
                                        log.info("Saved chat message: {} from {}", saved.getContent(), saved.getSender());
                                        // Emit message to room's sink
                                        Sinks.Many<RoomMessage> sink = roomSinks.get(message.getRoomId());
                                        if (sink != null) {
                                            sink.tryEmitNext(saved);
                                            log.info("Emitted chat message to roomId: {}, sender: {}",
                                                    message.getRoomId(), saved.getSender());
                                        } else {
                                            log.warn("No sink found for roomId: {}", message.getRoomId());
                                        }
                                    });
                        } else {
                            log.warn("Unknown action: {}", action);
                            return Mono.empty();
                        }
                    } catch (Exception e) {
                        log.error("Error parsing message: {}", e.getMessage());
                        return Mono.error(e);
                    }
                })
                .doOnError(e -> log.error("Error in incoming stream: {}", e.getMessage()))
                .onErrorContinue((e, obj) -> log.warn("Skipping invalid message: {}", e.getMessage()));

        // Send messages to clients in the same room
        Flux<WebSocketMessage> outgoing = roomIdFlux
                .doOnNext(roomId -> {
                    roomSinks.computeIfAbsent(roomId, k -> {
                        log.info("Created new sink for roomId: {} (from outgoing stream)", k);
                        return Sinks.many().multicast().onBackpressureBuffer();
                    });
                })
                .flatMap(roomId -> senderFlux.flatMap(sender -> {
                    Sinks.Many<RoomMessage> sink = roomSinks.get(roomId);
                    if (sink == null) {
                        log.warn("No sink found for roomId: {}", roomId);
                        return Flux.empty();
                    }
                    // Filter messages to avoid sending sender's own messages back
                    return sink.asFlux()
                            .filter(message -> !"CHAT".equals(message.getAction()) || !message.getSender().equals(sender))
                            .doOnNext(message -> log.info("Filtering message from {} for sender {}: action={}",
                                    message.getSender(), sender, message.getAction()));
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
                    roomIdSink.tryEmitComplete();
                    senderSink.tryEmitComplete();
                    // Clean up sinks if no subscribers remain
                    roomIdFlux.subscribe(roomId -> {
                        Sinks.Many<RoomMessage> sink = roomSinks.get(roomId);
                        if (sink != null && sink.currentSubscriberCount() == 0) {
                            roomSinks.remove(roomId);
                            sink.tryEmitComplete();
                            log.info("Removed sink for roomId: {}", roomId);
                        }
                    });
                });
    }
}