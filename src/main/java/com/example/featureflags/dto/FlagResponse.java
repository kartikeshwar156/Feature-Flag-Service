package com.example.featureflags.dto;

import java.time.Instant;

import com.example.featureflags.model.FeatureFlag;
import com.example.featureflags.model.FlagState;

public class FlagResponse {

	private String id;
	private String projectId;
	private String key;
	private String description;
	private FlagState state;
	private int rolloutPercentage;
	private Instant createdAt;
	private Instant updatedAt;

	public static FlagResponse from(FeatureFlag flag) {
		FlagResponse response = new FlagResponse();
		response.id = flag.getId();
		response.projectId = flag.getProjectId();
		response.key = flag.getKey();
		response.description = flag.getDescription();
		response.state = flag.getState();
		response.rolloutPercentage = flag.getRolloutPercentage();
		response.createdAt = flag.getCreatedAt();
		response.updatedAt = flag.getUpdatedAt();
		return response;
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getKey() {
		return key;
	}

	public String getDescription() {
		return description;
	}

	public FlagState getState() {
		return state;
	}

	public int getRolloutPercentage() {
		return rolloutPercentage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
