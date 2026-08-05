package com.example.featureflags.exception;

public class MissingProjectIdException extends RuntimeException {

	public MissingProjectIdException() {
		super("Required header 'X-Project-Id' is missing");
	}
}
