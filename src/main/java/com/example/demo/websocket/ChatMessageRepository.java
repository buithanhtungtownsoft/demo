package com.example.demo.websocket;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ChatMessageRepository extends R2dbcRepository<ChatMessage, String> {
    Flux<ChatMessage> findByCompanyCode(String companyCode);
}