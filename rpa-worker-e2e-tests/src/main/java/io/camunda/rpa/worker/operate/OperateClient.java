package io.camunda.rpa.worker.operate;

import feign.FeignException;
import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public interface OperateClient {
	
	@RequestLine("GET /process-instances/{key}")
	Mono<GetProcessInstanceResponse> getProcessInstance(@Param("key") long key);
	
	record GetProcessInstanceResponse (
			Long key,
			Long id,
			Long processInstanceKey,
			int processVersion,
			String processVersionTag,
			String bpmnProcessId,
			Long parentKey,
			Long parentFlowNodeInstanceKey,
			State state,
			boolean incident,
			long processDefinitionKey) {
		
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
				.onErrorComplete(FeignException.NotFound.class)
				.onErrorComplete(FeignException.BadRequest.class)
				.switchIfEmpty(getIncidents88b(new GetIncidentsRequest88b(new GetIncidentsRequest88b.Filter(String.valueOf(request.filter().processInstanceKey()))))
						.onErrorComplete(FeignException.BadRequest.class)
						.switchIfEmpty(getIncidents88a(request.filter.processInstanceKey(), request)));
	}

	@RequestLine("POST /incidents/search")
	Mono<GetIncidentsResponse> getIncidents87(GetIncidentsRequest request);

	@RequestLine("POST /process-instances/{key}/incidents/search")
	Mono<GetIncidentsResponse> getIncidents88a(@Param("key") long key, GetIncidentsRequest request);
	
	@RequestLine("POST /incidents/search")
	Mono<GetIncidentsResponse> getIncidents88b(GetIncidentsRequest88b request);

	record GetIncidentsRequest(Filter filter) {
		public record Filter(long processInstanceKey) {}
	}

	record GetIncidentsRequest88b(Filter filter) {
		public record Filter(String processInstanceKey) {
		}
	}

	record GetIncidentsResponse(List<Item> items) {
		public record Item(
				long key, 
				long processDefinitionKey, 
				long processInstanceKey, 
				Type type, 
				Type errorType,
				String message, 
				String errorMessage,
				State state, 
				long jobKey) {
			
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
	
	@RequestLine("POST /variables/search")
	Mono<GetVariablesResponse> getVariables(GetVariablesRequest request);

	record GetVariablesRequest(Filter filter) {
		public record Filter(long processInstanceKey) { }
	}

	record GetVariablesResponse(List<Item> items) {
		public record Item(
				long key,
				long processDefinitionKey,
				long processInstanceKey,
				String name,
				String value) {
		}
	}
}
