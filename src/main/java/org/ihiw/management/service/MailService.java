package org.ihiw.management.service;

import org.ihiw.management.domain.IhiwLab;
import org.ihiw.management.domain.IhiwUser;
import org.ihiw.management.domain.Project;
import org.ihiw.management.domain.User;

import io.github.jhipster.config.JHipsterProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.mail.internet.MimeMessage;

import org.ihiw.management.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Service for sending emails.
 * <p>
 * We use the {@link Async} annotation to send emails asynchronously.
 */
@Service
public class MailService {

    private final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";
    private static final String LAB = "lab";
    private static final String PROJECT = "project";
    private static final String FIRSTNAME = "firstname";

    private static final String BASE_URL = "baseUrl";

    private final JHipsterProperties jHipsterProperties;

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    public MailService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender, MessageSource messageSource, SpringTemplateEngine templateEngine) {

        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}",
            isMultipart, isHtml, to, subject, content);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.debug("Sent email to User '{}'", to);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.warn("Email could not be sent to user '{}'", to, e);
            } else {
                log.warn("Email could not be sent to user '{}': {}", to, e.getMessage());
            }
        }
    }

    @Async
    public void sendEmailFromTemplate(User user, String receiver, String templateName, String titleKey, Map<String, Object> contextParameters) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());
        for (String key : contextParameters.keySet()){
            context.setVariable(key, contextParameters.get(key));
        }
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        sendEmail(receiver, subject, content, false, true);
    }

    @Async
    public void sendActivationEmail(User user, String receiver, String firstNameOfReceiver) {
        log.debug("Sending activation email to '{}'", receiver);
        HashMap<String, Object> context = new HashMap<>();
        context.put(FIRSTNAME, firstNameOfReceiver);
        sendEmailFromTemplate(user, receiver, "mail/activationEmail", "email.activation.title", context);
    }

    @Async
    public void sendProjectLeaderSubscriptionNotificationEmail(User user, IhiwLab lab, Project project) {
        log.debug("Sending subscription email to '{}'", user.getEmail());
        Map<String, Object> context = new HashMap<>();
        context.put(LAB, lab);
        context.put(PROJECT, project);
        sendEmailFromTemplate(user, user.getEmail(), "mail/subscriptionEmail", "email.subscription.title", context);
    }

    @Async
    public void sendActivationConfirmation(User user) {
        log.debug("Sending activation2 email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, user.getEmail(), "mail/activationconfirmation", "email.activationconfirmation.title", new HashMap<>());
    }

    @Async
    public void sendCreationEmail(User user) {
        log.debug("Sending creation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, user.getEmail(), "mail/creationEmail", "email.activation.title", new HashMap<>());
    }

    @Async
    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, user.getEmail(), "mail/passwordResetEmail", "email.reset.title", new HashMap<>());
    }
}
