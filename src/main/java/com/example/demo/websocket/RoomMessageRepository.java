package com.example.demo.websocket;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface RoomMessageRepository extends R2dbcRepository<RoomMessage, String> {

}