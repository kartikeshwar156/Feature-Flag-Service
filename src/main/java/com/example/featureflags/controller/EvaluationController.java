package com.example.featureflags.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.featureflags.dto.EvalResponse;
import com.example.featureflags.exception.MissingProjectIdException;
import com.example.featureflags.service.FeatureFlagService;

@RestController
public class EvaluationController {

	public static final String PROJECT_ID_HEADER = "X-Project-Id";

	private final FeatureFlagService featureFlagService;

	public EvaluationController(FeatureFlagService featureFlagService) {
		this.featureFlagService = featureFlagService;
	}

	@GetMapping("/eval")
	public EvalResponse evaluate(
			@RequestHeader(value = PROJECT_ID_HEADER, required = false) String projectId,
			@RequestParam String flag,
			@RequestParam String user) {
		if (projectId == null || projectId.isBlank()) {
			throw new MissingProjectIdException();
		}
		boolean enabled = featureFlagService.evaluate(projectId, flag, user);
		return new EvalResponse(flag, user, enabled);
	}
}
