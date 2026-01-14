package io.camunda.rpa.worker.zeebe;

public record ZeebeJobInfo(String procesDefinitionId, Long processInstanceKey) {
}
