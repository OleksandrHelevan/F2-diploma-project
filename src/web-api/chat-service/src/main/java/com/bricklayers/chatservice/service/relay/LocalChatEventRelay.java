package com.bricklayers.chatservice.service.relay;

import com.bricklayers.chatservice.dto.ChatEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  LocalChatEventRelay
  @version  1.0.0
  @since 03.11.2026 - 15.00
*/

@Component
@ConditionalOnProperty(name = "chat.events.relay", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalChatEventRelay implements ChatEventRelay {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void relay(String destination, ChatEventResponse event) {
        messagingTemplate.convertAndSend(destination, event);
    }
}
