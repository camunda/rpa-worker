package io.camunda.rpa.worker.zeebe;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;

record ZeebeLinkedResource(
		String resourceId,
		ZeebeBindingType bindingType,
		String resourceType,
		String versionTag,
		String linkName,
		String resourceKey) {
}
