package com.example.featureflags.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RolloutBucket {

	private RolloutBucket() {
	}

	public static int bucket(String flagKey, String userId) {
		String input = flagKey + ":" + userId;
		byte[] hash = sha256(input);
		int value = ((hash[0] & 0xFF) << 24)
				| ((hash[1] & 0xFF) << 16)
				| ((hash[2] & 0xFF) << 8)
				| (hash[3] & 0xFF);
		return Math.floorMod(value, 100);
	}

	private static byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}
