package com.smartai.buddy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public SmtpVerificationEmailSender(
		JavaMailSender mailSender,
		@Value("${smart-ai-buddy.mail.from:}") String fromAddress
	) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	@Override
	public void sendVerificationCode(String email, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		if (!fromAddress.isBlank()) {
			message.setFrom(fromAddress);
		}
		message.setTo(email);
		message.setSubject("Verify your Smart AI Buddy email");
		message.setText("Your email verification code is " + code + ". It expires in 10 minutes.");
		mailSender.send(message);
	}

	@Override
	public void sendPasswordResetLink(String email, String resetLink) {
		SimpleMailMessage message = new SimpleMailMessage();
		if (!fromAddress.isBlank()) {
			message.setFrom(fromAddress);
		}
		message.setTo(email);
		message.setSubject("Reset your Smart AI Buddy password");
		message.setText("Use this link to reset your password. It expires in 15 minutes:\n\n" + resetLink);
		mailSender.send(message);
	}
}
