package com.cloudvault.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final ObjectMapper json = new ObjectMapper();
    private final JavaMailSender smtp;
    private final String mode,from,key;
    public MailService(JavaMailSender smtp,@Value("${vault.mail-mode}") String mode,@Value("${vault.mail-from}") String from,@Value("${vault.brevo-key}") String key) { this.smtp=smtp; this.mode=mode.strip(); this.from=from.strip(); this.key=key.strip(); }
    public void send(String email,String subject,String body) {
        try {
            if(mode.equals("brevo")) {
                var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
                RestClient.builder().requestFactory(factory).build().post().uri("https://api.brevo.com/v3/smtp/email").header("api-key",key).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("sender",Map.of("email",from,"name","CloudVault"),"to",List.of(Map.of("email",email)),"subject",subject,"textContent",body)).retrieve().toBodilessEntity();
                log.info("Brevo accepted email request");
            } else if(mode.equals("smtp")) {
                SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(from); message.setTo(email); message.setSubject(subject); message.setText(body); smtp.send(message);
            } else throw new IllegalStateException("Unknown mail mode");
        } catch(HttpStatusCodeException e) {
            String code = "unknown";
            try {
                String value = json.readTree(e.getResponseBodyAsString()).path("code").asText("unknown");
                if (value.matches("[A-Za-z0-9_-]{1,80}")) code = value;
            } catch (Exception ignored) { }
            log.warn("Email API rejected request: HTTP {}, provider code={}", e.getStatusCode().value(), code);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery unavailable. Try again later.",e);
        } catch(ResourceAccessException e) {
            log.warn("Email network request failed: {}", e.getMostSpecificCause().getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery unavailable. Try again later.",e);
        } catch(RuntimeException e) {
            log.warn("Email sending failed: mode={}, error type={}", mode, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery unavailable. Try again later.",e);
        }
    }
}
