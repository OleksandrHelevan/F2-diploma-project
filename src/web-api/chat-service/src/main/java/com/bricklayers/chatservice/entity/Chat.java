package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  Chat
  @version  1.0.0 
  @since 06.10.2026 - 19.21
*/

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chats")
@CompoundIndexes({
        @CompoundIndex(name = "company_type_idx", def = "{'companyId': 1, 'type': 1}"),
        @CompoundIndex(name = "participant_idx", def = "{'participants.userId': 1, 'lastMessageAt': -1}"),
        @CompoundIndex(name = "project_idx", def = "{'projectId': 1}")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Chat {
    @Id
    private String id;

    private String companyId;
    private ChatType type;

    private String name;
    private String projectId;
    private String brigadeId;

    @Indexed(unique = true, sparse = true)
    private String directKey;

    @Builder.Default
    private List<ChatParticipant> participants = new ArrayList<>();

    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
    private boolean archived;
}