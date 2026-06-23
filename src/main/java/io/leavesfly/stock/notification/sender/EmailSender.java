package io.leavesfly.stock.notification.sender;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.model.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 邮件通知发送器
 */
@Component
public class EmailSender implements BaseNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private final AppConfig config;

    public EmailSender(AppConfig config) {
        this.config = config;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean supportsMarkdown() {
        return false;
    }

    @Override
    public boolean send(String title, String content) {
        String host = config.getEmailSmtpHost();
        String user = config.getEmailUser();
        String to = config.getEmailTo();
        if (host == null || host.isEmpty() || user == null || user.isEmpty()) {
            log.warn("邮件配置不完整");
            return false;
        }

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(config.getEmailSmtpPort());
            mailSender.setUsername(user);
            mailSender.setPassword(config.getEmailPassword());

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.timeout", "10000");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(user);
            message.setTo(to.split(","));
            message.setSubject(title);
            message.setText(content);

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
            return false;
        }
    }
}
