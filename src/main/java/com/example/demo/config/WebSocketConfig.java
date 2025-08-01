package com.example.demo.config;

import com.example.demo.websocket.ChatWebSocketHandler;
import com.example.demo.websocket.GlobalChatWebSocketHandler;
import com.example.demo.websocket.RoomChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping(
            ChatWebSocketHandler chatWebSocketHandler,
            RoomChatWebSocketHandler roomChatWebSocketHandler,
            GlobalChatWebSocketHandler globalChatWebSocketHandler
    ) {
        Map<String, WebSocketHandler> urlMap = new HashMap<>();
        urlMap.put("/chat", chatWebSocketHandler);
        urlMap.put("/room", roomChatWebSocketHandler);
        urlMap.put("/global", globalChatWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }
}