package com.ecom.cartora.user;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String senderAddress;
    private final String mailHost;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@cartora.local}") String senderAddress,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.senderAddress = senderAddress;
        this.mailHost = mailHost;
    }

    public boolean isConfigured() {
        return !mailHost.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    public void send(String recipient, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailHost.isBlank()) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException ex) {
            logger.warn("Cartora email delivery failed for recipient domain {}", domainOf(recipient));
        }
    }

    private String domainOf(String address) {
        int separator = address == null ? -1 : address.lastIndexOf('@');
        return separator < 0 ? "unknown" : address.substring(separator + 1);
    }
}
