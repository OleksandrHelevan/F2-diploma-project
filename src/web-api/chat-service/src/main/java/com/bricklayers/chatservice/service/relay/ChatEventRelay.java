package com.bricklayers.chatservice.service.relay;

import com.bricklayers.chatservice.dto.ChatEventResponse;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  ChatEventRelay
  @version  1.0.0
  @since 03.11.2026 - 15.00
*/

public interface ChatEventRelay {

    void relay(String destination, ChatEventResponse event);
}
