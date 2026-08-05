package com.example.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.featureflags.controller.EvaluationController;
import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EvalResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.model.FlagState;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeatureFlagIntegrationTest {

	private static final String SHARED_KEY = "shared-feature";

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void createAndGetRoundTrip() {
		String projectId = "integration-create-get";
		CreateFlagRequest request = createRequest("round-trip", FlagState.ENABLED, 0);

		ResponseEntity<FlagResponse> created = postFlag(projectId, request);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<FlagResponse> fetched = restTemplate.getForEntity(
				url("/projects/" + projectId + "/flags/round-trip"),
				FlagResponse.class);

		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetched.getBody()).isNotNull();
		assertThat(fetched.getBody().getKey()).isEqualTo("round-trip");
		assertThat(fetched.getBody().getState()).isEqualTo(FlagState.ENABLED);
	}

	@Test
	void duplicateKeyReturns409() {
		String projectId = "integration-duplicate";
		CreateFlagRequest request = createRequest("dup-key", FlagState.DISABLED, 0);

		ResponseEntity<FlagResponse> first = postFlag(projectId, request);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> second = restTemplate.postForEntity(
				url("/projects/" + projectId + "/flags"),
				request,
				String.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void updateIsReflectedInEvaluation() {
		String projectId = "integration-update-eval";
		String key = "toggle-me";
		postFlag(projectId, createRequest(key, FlagState.DISABLED, 0));

		UpdateFlagRequest update = new UpdateFlagRequest();
		update.setDescription("now enabled");
		update.setState(FlagState.ENABLED);
		update.setRolloutPercentage(0);

		ResponseEntity<FlagResponse> updated = restTemplate.exchange(
				url("/projects/" + projectId + "/flags/" + key),
				HttpMethod.PUT,
				new HttpEntity<>(update, jsonHeaders()),
				FlagResponse.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

		EvalResponse eval = evaluate(projectId, key, "user-1");
		assertThat(eval.isEnabled()).isTrue();
	}

	@Test
	void deleteRemovesFlag() {
		String projectId = "integration-delete";
		String key = "to-delete";
		postFlag(projectId, createRequest(key, FlagState.ENABLED, 0));

		ResponseEntity<Void> deleted = restTemplate.exchange(
				url("/projects/" + projectId + "/flags/" + key),
				HttpMethod.DELETE,
				null,
				Void.class);

		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<String> fetched = restTemplate.getForEntity(
				url("/projects/" + projectId + "/flags/" + key),
				String.class);

		assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void evaluationRespectsEnabledAndDisabledStates() {
		String projectId = "integration-states";
		postFlag(projectId, createRequest("on-flag", FlagState.ENABLED, 0));
		postFlag(projectId, createRequest("off-flag", FlagState.DISABLED, 0));

		assertThat(evaluate(projectId, "on-flag", "user-1").isEnabled()).isTrue();
		assertThat(evaluate(projectId, "off-flag", "user-1").isEnabled()).isFalse();
	}

	@Test
	void evaluationIsStableAcrossRepeatedCalls() {
		String projectId = "integration-stable";
		postFlag(projectId, createRequest("stable-flag", FlagState.ROLLOUT, 50));

		EvalResponse first = evaluate(projectId, "stable-flag", "repeat-user");
		EvalResponse second = evaluate(projectId, "stable-flag", "repeat-user");

		assertThat(second.isEnabled()).isEqualTo(first.isEnabled());
	}

	@Test
	void evaluationWithoutProjectHeaderReturns400() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				url("/eval?flag=any&user=any"),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownFlagReturns404() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(EvaluationController.PROJECT_ID_HEADER, "integration-unknown");

		ResponseEntity<String> response = restTemplate.exchange(
				url("/eval?flag=missing&user=user-1"),
				HttpMethod.GET,
				new HttpEntity<>(headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void crossTenantIsolation_sameKeyDifferentProjectsDoNotLeak() {
		String projectA = "isolation-proj-a";
		String projectB = "isolation-proj-b";
		String projectC = "isolation-proj-c";

		postFlag(projectA, createRequest(SHARED_KEY, FlagState.ENABLED, 0));
		postFlag(projectB, createRequest(SHARED_KEY, FlagState.DISABLED, 0));

		assertThat(evaluate(projectA, SHARED_KEY, "user-x").isEnabled()).isTrue();
		assertThat(evaluate(projectB, SHARED_KEY, "user-x").isEnabled()).isFalse();

		ResponseEntity<java.util.List<FlagResponse>> listA = restTemplate.exchange(
				url("/projects/" + projectA + "/flags"),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});
		ResponseEntity<java.util.List<FlagResponse>> listB = restTemplate.exchange(
				url("/projects/" + projectB + "/flags"),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});
		ResponseEntity<java.util.List<FlagResponse>> listC = restTemplate.exchange(
				url("/projects/" + projectC + "/flags"),
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {
				});

		assertThat(listA.getBody()).extracting(FlagResponse::getKey).containsExactly(SHARED_KEY);
		assertThat(listA.getBody()).allMatch(flag -> projectA.equals(flag.getProjectId()));

		assertThat(listB.getBody()).extracting(FlagResponse::getKey).containsExactly(SHARED_KEY);
		assertThat(listB.getBody()).allMatch(flag -> projectB.equals(flag.getProjectId()));

		assertThat(listC.getBody()).isEmpty();

		ResponseEntity<String> crossProjectGet = restTemplate.getForEntity(
				url("/projects/" + projectB + "/flags/" + SHARED_KEY + "-only-in-a"),
				String.class);
		assertThat(crossProjectGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<String> evalMissingInC = restTemplate.exchange(
				url("/eval?flag=" + SHARED_KEY + "&user=user-x"),
				HttpMethod.GET,
				projectHeaderEntity(projectC),
				String.class);
		assertThat(evalMissingInC.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private ResponseEntity<FlagResponse> postFlag(String projectId, CreateFlagRequest request) {
		return restTemplate.postForEntity(
				url("/projects/" + projectId + "/flags"),
				request,
				FlagResponse.class);
	}

	private EvalResponse evaluate(String projectId, String flag, String user) {
		ResponseEntity<EvalResponse> response = restTemplate.exchange(
				url("/eval?flag=" + flag + "&user=" + user),
				HttpMethod.GET,
				projectHeaderEntity(projectId),
				EvalResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private HttpEntity<Void> projectHeaderEntity(String projectId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(EvaluationController.PROJECT_ID_HEADER, projectId);
		return new HttpEntity<>(headers);
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private CreateFlagRequest createRequest(String key, FlagState state, int rolloutPercentage) {
		CreateFlagRequest request = new CreateFlagRequest();
		request.setKey(key);
		request.setDescription("integration test flag");
		request.setState(state);
		request.setRolloutPercentage(rolloutPercentage);
		return request;
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
