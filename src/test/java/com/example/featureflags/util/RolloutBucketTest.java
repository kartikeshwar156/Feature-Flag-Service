package com.example.featureflags.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RolloutBucketTest {

	@Test
	void bucketIsDeterministicAcrossRepeatedCalls() {
		for (int i = 0; i < 100; i++) {
			int first = RolloutBucket.bucket("checkout-v2", "user-42");
			int second = RolloutBucket.bucket("checkout-v2", "user-42");
			assertThat(second).isEqualTo(first);
		}
	}

	@Test
	void bucketStaysWithinZeroToNinetyNine() {
		for (int i = 0; i < 10_000; i++) {
			int bucket = RolloutBucket.bucket("flag-" + (i % 50), "user-" + i);
			assertThat(bucket).isBetween(0, 99);
		}
	}

	@Test
	void bucketDistributionIsRoughlyUniform() {
		Map<Integer, Integer> counts = new HashMap<>();
		for (int i = 0; i < 10_000; i++) {
			int bucket = RolloutBucket.bucket("distribution-flag", "user-" + i);
			counts.merge(bucket, 1, Integer::sum);
		}

		double expected = 10_000 / 100.0;
		for (int bucket = 0; bucket < 100; bucket++) {
			int count = counts.getOrDefault(bucket, 0);
			assertThat(count).isBetween((int) (expected * 0.3), (int) (expected * 1.7));
		}
	}

	@Test
	void differentUsersProduceMultipleBuckets() {
		long distinctBuckets = java.util.stream.LongStream.range(0, 100)
				.map(i -> RolloutBucket.bucket("same-flag", "user-" + i))
				.distinct()
				.count();
		assertThat(distinctBuckets).isGreaterThan(1);
	}
}
