package io.camunda.rpa.worker.operate;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@HttpExchange
public interface OperateClient {
	
	@GetExchange("/process-instances/{key}")
	Mono<GetProcessInstanceResponse> getProcessInstance(@PathVariable long key);
	
	record GetProcessInstanceResponse (
			Long key,
			Long id,
			Long processInstanceKey,
			Integer processVersion,
			String processVersionTag,
			String bpmnProcessId,
			Long parentKey,
			Long parentFlowNodeInstanceKey,
			State state,
			Boolean incident,
			Long processDefinitionKey) {
		
		public Long key() {
			return Optional.ofNullable(key)
					.or(() -> Optional.ofNullable(id))
					.or(() -> Optional.ofNullable(processInstanceKey))
					.orElseThrow();
		}
		
		public enum State {
			ACTIVE, COMPLETED, CANCELED
		}
	}
	
	default Mono<GetIncidentsResponse> getIncidents(GetIncidentsRequest request) {
		return getIncidents87(request)
				.onErrorComplete(WebClientResponseException.NotFound.class)
				.onErrorComplete(WebClientResponseException.BadRequest.class)
				.switchIfEmpty(getIncidents88b(new GetIncidentsRequest88b(new GetIncidentsRequest88b.Filter(String.valueOf(request.filter().processInstanceKey()))))
						.onErrorComplete(WebClientResponseException.BadRequest.class)
						.switchIfEmpty(getIncidents88a(request.filter.processInstanceKey(), request)));
	}

	@PostExchange("/incidents/search")
	Mono<GetIncidentsResponse> getIncidents87(@RequestBody GetIncidentsRequest request);

	@PostExchange("/process-instances/{key}/incidents/search")
	Mono<GetIncidentsResponse> getIncidents88a(@PathVariable long key, @RequestBody GetIncidentsRequest request);
	
	@PostExchange("/incidents/search")
	Mono<GetIncidentsResponse> getIncidents88b(@RequestBody GetIncidentsRequest88b request);

	record GetIncidentsRequest(Filter filter) {
		public record Filter(Long processInstanceKey) {}
	}

	record GetIncidentsRequest88b(Filter filter) {
		public record Filter(String processInstanceKey) {
		}
	}

	record GetIncidentsResponse(List<Item> items) {
		public record Item(
				Long key, 
				Long processDefinitionKey, 
				Long processInstanceKey, 
				Type type, 
				Type errorType,
				String message, 
				String errorMessage,
				State state, 
				Long jobKey) {
			
			public Type type() {
				return Optional.ofNullable(type)
						.or(() -> Optional.ofNullable(errorType))
						.orElse(null);
			}
			
			public String message() {
				return Optional.ofNullable(message)
						.or(() -> Optional.ofNullable(errorMessage))
						.orElse(null);
			}

			public enum Type {
				UNSPECIFIED, UNKNOWN, IO_MAPPING_ERROR, JOB_NO_RETRIES, EXECUTION_LISTENER_NO_RETRIES, CONDITION_ERROR, EXTRACT_VALUE_ERROR, CALLED_ELEMENT_ERROR, UNHANDLED_ERROR_EVENT, MESSAGE_SIZE_EXCEEDED, CALLED_DECISION_ERROR, DECISION_EVALUATION_ERROR, FORM_NOT_FOUND
			}
			
			public enum State {
				ACTIVE, MIGRATED, RESOLVED, PENDING
			}
		}
	}
	
	default Mono<GetVariablesResponse> getVariables(GetVariablesRequest request) {
		return doGetVariables(request)
				.onErrorComplete(WebClientResponseException.BadRequest.class)
				.switchIfEmpty(doGetVariables88(
						new GetVariablesRequest88(
								new GetVariablesRequest88.Filter(String.valueOf(request.filter.processInstanceKey())))));
	}

	@PostExchange("/variables/search")
	Mono<GetVariablesResponse> doGetVariables(@RequestBody GetVariablesRequest request);

	@PostExchange("/variables/search")
	Mono<GetVariablesResponse> doGetVariables88(@RequestBody GetVariablesRequest88 request);

	record GetVariablesRequest(Filter filter) {
		public record Filter(long processInstanceKey) { }
	}

	record GetVariablesRequest88(Filter filter) {
		public record Filter(String processInstanceKey) { }
	}

	record GetVariablesResponse(List<Item> items) {
		public record Item(
				Long key,
				Long processDefinitionKey,
				Long processInstanceKey,
				String name,
				String value) {
		}
	}
}
