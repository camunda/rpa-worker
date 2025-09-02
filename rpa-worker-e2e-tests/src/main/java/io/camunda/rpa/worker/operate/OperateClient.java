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
				.switchIfEmpty(getIncidents88(request.filter.processInstanceKey(), request));
	}

	@RequestLine("POST /incidents/search")
	Mono<GetIncidentsResponse> getIncidents87(GetIncidentsRequest request);

	@RequestLine("POST /process-instances/{key}/incidents/search")
	Mono<GetIncidentsResponse> getIncidents88(@Param("key") long key, GetIncidentsRequest request);

	record GetIncidentsRequest(Filter filter) {
		public record Filter(long processInstanceKey) {}
	}
	record GetIncidentsResponse(List<Item> items) {
		public record Item(
				long key, 
				long processDefinitionKey, 
				long processInstanceKey, 
				Type type, 
				String message, 
				State state, 
				long jobKey) {

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
