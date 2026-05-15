package com.pathyana.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification, password reset later).
 *
 * Why @Async on send methods:
 *   SMTP can take 1-3 seconds. Without @Async, the signup endpoint blocks until
 *   the email is sent — bad UX. With @Async, signup returns immediately and the
 *   email is sent in the background.
 *   To enable async, we need @EnableAsync on a config class — added in PathyanaBackendApplication.
 *
 * What we send:
 *   - HTML email with a clickable verification link
 *   - Link points to GET /api/auth/verify?token=...
 *   - Token is stored on the User row when the user signs up
 *
 * For dev: emails go via Gmail SMTP using the app password.
 * For prod: switch to a transactional service (SendGrid, AWS SES, Resend) — same JavaMailSender
 * interface, just change the SMTP settings.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${pathyana.mail.from}")
    private String fromAddress;

    @Value("${pathyana.app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String toEmail, String userName, String verificationToken) {
        String verifyLink = baseUrl + "/api/auth/verify?token=" + verificationToken;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Verify your Pathyana account");
            helper.setText(buildVerificationHtml(userName, verifyLink), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            // Don't rethrow — the user is already signed up. If the email fails, they can
            // request a resend. Throwing here would crash the signup flow.
        }
    }

    private String buildVerificationHtml(String userName, String verifyLink) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 560px; margin: 0 auto; padding: 24px; color: #111;">
              <h2 style="color: #2d6a4f;">Welcome to Pathyana, %s</h2>
              <p>Thanks for signing up. Please verify your email address to activate your account.</p>
              <p style="margin: 32px 0;">
                <a href="%s" style="background: #2d6a4f; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; display: inline-block;">
                  Verify Email Address
                </a>
              </p>
              <p style="color: #666; font-size: 14px;">
                Or paste this link into your browser:<br>
                <span style="word-break: break-all;">%s</span>
              </p>
              <hr style="border: none; border-top: 1px solid #eee; margin: 32px 0;">
              <p style="color: #999; font-size: 12px;">
                If you didn't sign up for Pathyana, you can ignore this email.
              </p>
            </body>
            </html>
            """.formatted(userName, verifyLink, verifyLink);
    }
}
