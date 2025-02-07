package io.camunda.rpa.worker.zeebe;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;

import java.util.Collection;

record ZeebeLinkedResources(Collection<ZeebeLinkedResource> linkedResources) {
	
	record ZeebeLinkedResource(
			String resourceId,
			ZeebeBindingType bindingType,
			String resourceType,
			String versionTag,
			String linkName, 
			String key) {}

}
