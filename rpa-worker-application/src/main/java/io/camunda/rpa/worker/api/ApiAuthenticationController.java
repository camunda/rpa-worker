package io.camunda.rpa.worker.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiAuthenticationController {

	@GetMapping("/api/authentication")
	ResponseEntity<Void> authenticate() {
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}
}
