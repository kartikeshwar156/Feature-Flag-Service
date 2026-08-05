package com.example.featureflags.exception;

public class FlagNotFoundException extends RuntimeException {

	public FlagNotFoundException(String projectId, String key) {
		super("Flag '%s' not found in project '%s'".formatted(key, projectId));
	}
}
