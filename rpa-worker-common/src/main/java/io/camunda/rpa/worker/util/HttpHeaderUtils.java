package io.camunda.rpa.worker.util;

import java.util.Base64;

public class HttpHeaderUtils {
	
	public static String basicAuth(String username, String password) {
		return "Basic %s".formatted(
				Base64.getEncoder().encodeToString("%s:%s".formatted(username, password).getBytes()));
	}
}
