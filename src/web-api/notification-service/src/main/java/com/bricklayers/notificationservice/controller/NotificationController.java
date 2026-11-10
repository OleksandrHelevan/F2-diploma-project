package com.bricklayers.notificationservice.controller;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.notificationservice.notification.NotificationService;
import com.bricklayers.notificationservice.notification.NotificationService.Accepted;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Accepted> accept(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.accepted().body(notificationService.accept(request));
    }
}
