package com.example.featureflags.dto;

import com.example.featureflags.model.FlagState;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateFlagRequest {

	@Size(max = 512)
	private String description;

	@NotNull
	private FlagState state;

	@Min(0)
	@Max(100)
	private int rolloutPercentage;

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
}
