package com.example.featureflags.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.service.FeatureFlagService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/projects/{projectId}/flags")
public class FeatureFlagController {

	private final FeatureFlagService featureFlagService;

	public FeatureFlagController(FeatureFlagService featureFlagService) {
		this.featureFlagService = featureFlagService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FlagResponse create(@PathVariable String projectId, @Valid @RequestBody CreateFlagRequest request) {
		return featureFlagService.create(projectId, request);
	}

	@GetMapping
	public List<FlagResponse> listAll(@PathVariable String projectId) {
		return featureFlagService.listAll(projectId);
	}

	@GetMapping("/{key}")
	public FlagResponse get(@PathVariable String projectId, @PathVariable String key) {
		return featureFlagService.get(projectId, key);
	}

	@PutMapping("/{key}")
	public FlagResponse update(
			@PathVariable String projectId,
			@PathVariable String key,
			@Valid @RequestBody UpdateFlagRequest request) {
		return featureFlagService.update(projectId, key, request);
	}

	@DeleteMapping("/{key}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String projectId, @PathVariable String key) {
		featureFlagService.delete(projectId, key);
	}
}
