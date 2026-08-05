package com.example.featureflags.service;

import java.util.List;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;

public interface FeatureFlagService {

	FlagResponse create(String projectId, CreateFlagRequest request);

	List<FlagResponse> listAll(String projectId);

	FlagResponse get(String projectId, String key);

	FlagResponse update(String projectId, String key, UpdateFlagRequest request);

	void delete(String projectId, String key);

	boolean evaluate(String projectId, String flagKey, String userId);
}
