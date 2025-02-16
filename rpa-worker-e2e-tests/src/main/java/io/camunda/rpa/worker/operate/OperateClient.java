package io.camunda.rpa.worker.operate;

import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.List;

public interface OperateClient {
	
	@RequestLine("GET /process-instances/{key}")
	Mono<GetProcessInstanceResponse> getProcessInstance(@Param("key") long key);
	
	record GetProcessInstanceResponse (
			long key,
			int processVersion,
			String processVersionTag,
			String bpmnProcessId,
			Long parentKey,
			Long parentFlowNodeInstanceKey,
			State state,
			boolean incident,
			long processDefinitionKey) {
		
		enum State {
			ACTIVE, COMPLETED, CANCELED
		}
	}

	@RequestLine("POST /incidents/search")
	Mono<GetIncidentsResponse> getIncidents(GetIncidentsRequest request);

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
}
