package com.example.featureflags.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagKeyException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.FeatureFlag;
import com.example.featureflags.model.FlagState;
import com.example.featureflags.repository.FeatureFlagRepository;
import com.example.featureflags.util.RolloutBucket;

@Service
public class FeatureFlagServiceImpl implements FeatureFlagService {

	private final FeatureFlagRepository repository;

	public FeatureFlagServiceImpl(FeatureFlagRepository repository) {
		this.repository = repository;
	}

	@Override
	public FlagResponse create(String projectId, CreateFlagRequest request) {
		if (repository.existsByProjectIdAndKey(projectId, request.getKey())) {
			throw new DuplicateFlagKeyException(projectId, request.getKey());
		}

		Instant now = Instant.now();
		FeatureFlag flag = new FeatureFlag(
				projectId,
				request.getKey(),
				request.getDescription(),
				request.getState(),
				request.getRolloutPercentage());
		flag.setCreatedAt(now);
		flag.setUpdatedAt(now);

		try {
			return FlagResponse.from(repository.save(flag));
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateFlagKeyException(projectId, request.getKey());
		}
	}

	@Override
	public List<FlagResponse> listAll(String projectId) {
		return repository.findAllByProjectId(projectId).stream()
				.map(FlagResponse::from)
				.toList();
	}

	@Override
	public FlagResponse get(String projectId, String key) {
		return FlagResponse.from(findFlagOrThrow(projectId, key));
	}

	@Override
	public FlagResponse update(String projectId, String key, UpdateFlagRequest request) {
		FeatureFlag flag = findFlagOrThrow(projectId, key);
		flag.setDescription(request.getDescription());
		flag.setState(request.getState());
		flag.setRolloutPercentage(request.getRolloutPercentage());
		flag.setUpdatedAt(Instant.now());
		return FlagResponse.from(repository.save(flag));
	}

	@Override
	public void delete(String projectId, String key) {
		FeatureFlag flag = findFlagOrThrow(projectId, key);
		repository.delete(flag);
	}

	@Override
	public boolean evaluate(String projectId, String flagKey, String userId) {
		FeatureFlag flag = findFlagOrThrow(projectId, flagKey);
		return evaluateFlag(flag, userId);
	}

	boolean evaluateFlag(FeatureFlag flag, String userId) {
		return switch (flag.getState()) {
			case ENABLED -> true;
			case DISABLED -> false;
			case ROLLOUT -> RolloutBucket.bucket(flag.getKey(), userId) < flag.getRolloutPercentage();
		};
	}

	private FeatureFlag findFlagOrThrow(String projectId, String key) {
		return repository.findByProjectIdAndKey(projectId, key)
				.orElseThrow(() -> new FlagNotFoundException(projectId, key));
	}
}
