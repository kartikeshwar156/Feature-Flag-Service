package com.example.featureflags.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "feature_flags")
@CompoundIndexes({
	@CompoundIndex(name = "project_key_unique", def = "{'projectId': 1, 'key': 1}", unique = true)
})
public class FeatureFlag {

	@Id
	private String id;

	@Indexed
	private String projectId;

	private String key;
	private String description;
	private FlagState state;
	private int rolloutPercentage;
	private Instant createdAt;
	private Instant updatedAt;

	public FeatureFlag() {
	}

	public FeatureFlag(String projectId, String key, String description, FlagState state, int rolloutPercentage) {
		this.projectId = projectId;
		this.key = key;
		this.description = description;
		this.state = state;
		this.rolloutPercentage = rolloutPercentage;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public FlagState getState() {
		return state;
	}

	public void setState(FlagState state) {
		this.state = state;
	}

	public int getRolloutPercentage() {
		return rolloutPercentage;
	}

	public void setRolloutPercentage(int rolloutPercentage) {
		this.rolloutPercentage = rolloutPercentage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
