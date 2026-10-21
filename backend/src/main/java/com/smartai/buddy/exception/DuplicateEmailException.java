package com.smartai.buddy.exception;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("An account with this college email already exists.");
	}
}
