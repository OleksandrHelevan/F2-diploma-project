package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatBucket
  @version  1.0.0 
  @since 06.10.2026 - 19.11
*/

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_buckets")
@CompoundIndex(name = "chat_thread_msg_idx", def = "{'chatId': 1, 'threadId': 1, 'messages._id': 1}")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatBucket {
    @Id
    private String id;

    private String chatId;

    private String threadId;

    private int count;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
}