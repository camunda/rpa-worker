package io.camunda.rpa.worker.zeebe.api;

import io.camunda.rpa.worker.api.StubbedResponseGenerator;
import io.camunda.rpa.worker.zeebe.ZeebeJobService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/zeebe/job")
@RequiredArgsConstructor
@Slf4j
class ZeebeJobController {
	
	private final ZeebeClient zeebeClient;
	private final ZeebeJobService zeebeJobService;
	private final StubbedResponseGenerator stubbedResponseGenerator;
	
	@PostMapping("{jobKey}/throw")
	public Mono<ResponseEntity<?>> throwError(
			@PathVariable long jobKey, 
			@RequestBody @Valid JobThrowErrorRequest request) {
		
		return stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", request)
				.switchIfEmpty(Mono.defer(() -> doThrowError(jobKey, request)));
	}

	private Mono<ResponseEntity<?>> doThrowError(long jobKey, JobThrowErrorRequest request) {
		ThrowErrorCommandStep1.ThrowErrorCommandStep2 builder = zeebeClient
				.newThrowErrorCommand(jobKey)
				.errorCode(request.errorCode());

		if(StringUtils.hasText(request.errorMessage()))
			builder.errorMessage(request.errorMessage());

		if(request.variables() != null)
			builder.variables(request.variables());

		builder.send();
		zeebeJobService.pushDetached(jobKey);
		return Mono.just(ResponseEntity.accepted().build());
	}

}
