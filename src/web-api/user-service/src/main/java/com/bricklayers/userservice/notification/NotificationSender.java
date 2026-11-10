package com.bricklayers.userservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.userservice.async.AsyncConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final NotificationClient client;

    @Async(AsyncConfig.EXECUTOR)
    public void send(NotificationRequest request) {
        client.send(request);
    }
}
