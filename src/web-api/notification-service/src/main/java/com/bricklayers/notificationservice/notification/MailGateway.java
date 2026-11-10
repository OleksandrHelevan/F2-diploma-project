package com.bricklayers.notificationservice.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MailGateway {

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration providerLatency;

    public MailGateway(JavaMailSender mailSender,
                       @Value("${app.notifications.from:no-reply@bricklayers.local}") String from,
                       @Value("${app.notifications.provider-latency:1s}") Duration providerLatency) {
        this.mailSender = mailSender;
        this.from = from;
        this.providerLatency = providerLatency;
    }

    public void send(String recipient, String subject, String body) {
        emulateProviderLatency();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void emulateProviderLatency() {
        if (providerLatency.isZero() || providerLatency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(providerLatency);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending e-mail", exception);
        }
    }
}
