package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  ThreadInfo
  @version  1.0.0 
  @since 06.10.2026 - 19.23
*/

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ThreadInfo {
    private int replyCount;
    private LocalDateTime lastReplyAt;

    @Builder.Default
    private Set<String> participantIds = new HashSet<>();
}
