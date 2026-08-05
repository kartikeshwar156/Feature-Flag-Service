package com.example.featureflags.dto;

public class EvalResponse {

	private String flag;
	private String user;
	private boolean enabled;

	public EvalResponse(String flag, String user, boolean enabled) {
		this.flag = flag;
		this.user = user;
		this.enabled = enabled;
	}

	public String getFlag() {
		return flag;
	}

	public String getUser() {
		return user;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
