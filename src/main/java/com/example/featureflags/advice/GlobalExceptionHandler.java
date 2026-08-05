package com.example.featureflags.advice;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.featureflags.exception.DuplicateFlagKeyException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.exception.MissingProjectIdException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(FlagNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(FlagNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(DuplicateFlagKeyException.class)
	public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateFlagKeyException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(MissingProjectIdException.class)
	public ResponseEntity<Map<String, String>> handleMissingProjectId(MissingProjectIdException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("error", "Validation failed");
		Map<String, String> fields = new HashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fields.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		body.put("fields", fields);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	private Map<String, String> errorBody(String message) {
		return Map.of("error", message);
	}
}
