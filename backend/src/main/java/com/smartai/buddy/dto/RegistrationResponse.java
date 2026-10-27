package com.smartai.buddy.dto;

public record RegistrationResponse(Long id, String name, String email, boolean verificationEmailSent) {
}
