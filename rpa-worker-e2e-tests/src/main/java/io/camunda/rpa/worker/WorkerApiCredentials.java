package io.camunda.rpa.worker;

public final class WorkerApiCredentials {

	private WorkerApiCredentials() {}

	public static String token() {
		String token = System.getenv("CAMUNDA_RPA_API_TOKEN");
		if (token == null || token.isBlank())
			throw new IllegalStateException("Set CAMUNDA_RPA_API_TOKEN for authenticated worker E2E tests");
		return token;
	}
}
