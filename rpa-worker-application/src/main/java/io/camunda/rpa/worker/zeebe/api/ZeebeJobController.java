package io.camunda.rpa.worker.zeebe.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.zeebe.ZeebeClientStatus;
import io.camunda.rpa.worker.zeebe.ZeebeJobService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.vavr.control.Try;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
	private final ZeebeClientStatus zeebeClientStatus;
	private final ObjectMapper objectMapper;
	
	@PostMapping("{jobKey}/throw")
	public Mono<ResponseEntity<?>> throwError(
			@PathVariable long jobKey, 
			@RequestBody @Valid JobThrowErrorRequest request) {
		
		if( ! zeebeClientStatus.isZeebeClientEnabled())
			return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
					.header("Content-type", "text/plain")
					.body("""
		*** STUB: zeebe/newThrowErrorCommand ***
		Would have issued this command to Zeebe:
		%s
		""".formatted(Try.of(() -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request)).get())));

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
