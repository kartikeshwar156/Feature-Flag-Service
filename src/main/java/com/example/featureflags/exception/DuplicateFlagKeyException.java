package com.example.featureflags.exception;

public class DuplicateFlagKeyException extends RuntimeException {

	public DuplicateFlagKeyException(String projectId, String key) {
		super("Flag '%s' already exists in project '%s'".formatted(key, projectId));
	}
}
