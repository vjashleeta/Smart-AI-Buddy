package com.smartai.buddy.service;

public interface VerificationEmailSender {

	void sendVerificationCode(String email, String code);

	void sendPasswordResetLink(String email, String resetLink);
}
