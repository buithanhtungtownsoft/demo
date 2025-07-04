package com.example.demo.websocket;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("chat_message")
public class ChatMessage {
    @Id
    private String id;
    private String sender;
    private String content;
    private String companyCode;
    private long timestamp;
}