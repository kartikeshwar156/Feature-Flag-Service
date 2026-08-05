package com.example.featureflags.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.featureflags.model.FeatureFlag;

public interface FeatureFlagRepository extends MongoRepository<FeatureFlag, String> {

	List<FeatureFlag> findAllByProjectId(String projectId);

	Optional<FeatureFlag> findByProjectIdAndKey(String projectId, String key);

	boolean existsByProjectIdAndKey(String projectId, String key);

	void deleteByProjectIdAndKey(String projectId, String key);
}
