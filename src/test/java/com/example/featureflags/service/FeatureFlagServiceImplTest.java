package com.example.featureflags.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagKeyException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.FeatureFlag;
import com.example.featureflags.model.FlagState;
import com.example.featureflags.repository.FeatureFlagRepository;
import com.example.featureflags.util.RolloutBucket;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceImplTest {

	private static final String PROJECT_A = "proj-a";
	private static final String PROJECT_B = "proj-b";
	private static final String FLAG_KEY = "checkout";

	@Mock
	private FeatureFlagRepository repository;

	private FeatureFlagServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new FeatureFlagServiceImpl(repository);
	}

	@Test
	void createSucceedsWhenKeyIsUniqueWithinProject() {
		CreateFlagRequest request = createRequest(FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.existsByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(false);
		when(repository.save(any(FeatureFlag.class))).thenAnswer(invocation -> {
			FeatureFlag flag = invocation.getArgument(0);
			flag.setId("flag-1");
			return flag;
		});

		var response = service.create(PROJECT_A, request);

		assertThat(response.getKey()).isEqualTo(FLAG_KEY);
		assertThat(response.getProjectId()).isEqualTo(PROJECT_A);
	}

	@Test
	void createRejectsDuplicateKeyWithinSameProject() {
		CreateFlagRequest request = createRequest(FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.existsByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(true);

		assertThatThrownBy(() -> service.create(PROJECT_A, request))
				.isInstanceOf(DuplicateFlagKeyException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void createHandlesDuplicateKeyRaceViaDuplicateKeyException() {
		CreateFlagRequest request = createRequest(FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.existsByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(false);
		when(repository.save(any(FeatureFlag.class))).thenThrow(new DuplicateKeyException("duplicate"));

		assertThatThrownBy(() -> service.create(PROJECT_A, request))
				.isInstanceOf(DuplicateFlagKeyException.class);
	}

	@Test
	void sameKeyAllowedAcrossDifferentProjects() {
		CreateFlagRequest request = createRequest(FLAG_KEY, FlagState.DISABLED, 0);
		when(repository.existsByProjectIdAndKey(PROJECT_B, FLAG_KEY)).thenReturn(false);
		when(repository.save(any(FeatureFlag.class))).thenAnswer(invocation -> {
			FeatureFlag flag = invocation.getArgument(0);
			flag.setId("flag-2");
			return flag;
		});

		var response = service.create(PROJECT_B, request);

		assertThat(response.getProjectId()).isEqualTo(PROJECT_B);
	}

	@Test
	void getReturnsNotFoundWhenFlagBelongsToDifferentProject() {
		when(repository.findByProjectIdAndKey(PROJECT_B, FLAG_KEY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(PROJECT_B, FLAG_KEY))
				.isInstanceOf(FlagNotFoundException.class);
	}

	@Test
	void evaluateReturnsTrueForEnabledState() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.of(flag));

		assertThat(service.evaluate(PROJECT_A, FLAG_KEY, "user-1")).isTrue();
	}

	@Test
	void evaluateReturnsFalseForDisabledState() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.DISABLED, 0);
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.of(flag));

		assertThat(service.evaluate(PROJECT_A, FLAG_KEY, "user-1")).isFalse();
	}

	@Test
	void evaluateRolloutIsStableForSameUser() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ROLLOUT, 50);
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.of(flag));

		boolean first = service.evaluate(PROJECT_A, FLAG_KEY, "stable-user");
		boolean second = service.evaluate(PROJECT_A, FLAG_KEY, "stable-user");

		assertThat(second).isEqualTo(first);
	}

	@Test
	void evaluateRolloutZeroPercentIsAlwaysOff() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ROLLOUT, 0);
		for (int i = 0; i < 20; i++) {
			assertThat(service.evaluateFlag(flag, "user-" + i)).isFalse();
		}
	}

	@Test
	void evaluateRolloutOneHundredPercentIsAlwaysOn() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ROLLOUT, 100);
		for (int i = 0; i < 20; i++) {
			assertThat(service.evaluateFlag(flag, "user-" + i)).isTrue();
		}
	}

	@Test
	void evaluateRolloutUsesBucketComparison() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ROLLOUT, 50);
		int bucket = RolloutBucket.bucket(FLAG_KEY, "bucket-user");
		boolean expected = bucket < 50;

		assertThat(service.evaluateFlag(flag, "bucket-user")).isEqualTo(expected);
	}

	@Test
	void deleteVerifiesOwnershipBeforeDeleting() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.of(flag));

		service.delete(PROJECT_A, FLAG_KEY);

		verify(repository).delete(flag);
	}

	@Test
	void deleteThrowsWhenFlagNotInProject() {
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(PROJECT_A, FLAG_KEY))
				.isInstanceOf(FlagNotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void updatePersistsChanges() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.DISABLED, 0);
		when(repository.findByProjectIdAndKey(PROJECT_A, FLAG_KEY)).thenReturn(Optional.of(flag));
		when(repository.save(any(FeatureFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdateFlagRequest request = new UpdateFlagRequest();
		request.setDescription("Updated");
		request.setState(FlagState.ROLLOUT);
		request.setRolloutPercentage(25);

		var response = service.update(PROJECT_A, FLAG_KEY, request);

		assertThat(response.getState()).isEqualTo(FlagState.ROLLOUT);
		assertThat(response.getRolloutPercentage()).isEqualTo(25);

		ArgumentCaptor<FeatureFlag> captor = ArgumentCaptor.forClass(FeatureFlag.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getDescription()).isEqualTo("Updated");
	}

	@Test
	void listAllReturnsOnlyProjectFlags() {
		FeatureFlag flag = savedFlag(PROJECT_A, FLAG_KEY, FlagState.ENABLED, 0);
		when(repository.findAllByProjectId(PROJECT_A)).thenReturn(List.of(flag));

		var flags = service.listAll(PROJECT_A);

		assertThat(flags).hasSize(1);
		assertThat(flags.get(0).getProjectId()).isEqualTo(PROJECT_A);
	}

	private CreateFlagRequest createRequest(String key, FlagState state, int rolloutPercentage) {
		CreateFlagRequest request = new CreateFlagRequest();
		request.setKey(key);
		request.setDescription("desc");
		request.setState(state);
		request.setRolloutPercentage(rolloutPercentage);
		return request;
	}

	private FeatureFlag savedFlag(String projectId, String key, FlagState state, int rolloutPercentage) {
		FeatureFlag flag = new FeatureFlag(projectId, key, "desc", state, rolloutPercentage);
		flag.setId("id-1");
		flag.setCreatedAt(Instant.now());
		flag.setUpdatedAt(Instant.now());
		return flag;
	}
}
