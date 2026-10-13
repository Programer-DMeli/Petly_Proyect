package com.petly.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

@TestConfiguration
class AuthTestConfig {
    @Bean
    JavaMailSender javaMailSender() {
        return org.mockito.Mockito.mock(JavaMailSender.class);
    }
}