package com.example.demo.websocket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "room_message")
public class RoomMessage {

    @Id
    private Integer id;
    private String sender;
    private String content;
    private String roomId;
    private Long timestamp;

    @Transient
    private String action;
}
