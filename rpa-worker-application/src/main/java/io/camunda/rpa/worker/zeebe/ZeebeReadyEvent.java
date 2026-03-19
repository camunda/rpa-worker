package io.camunda.rpa.worker.zeebe;

import io.camunda.client.CamundaClient;
import org.springframework.context.ApplicationEvent;

class ZeebeReadyEvent extends ApplicationEvent {
	public ZeebeReadyEvent(CamundaClient zeebeClient) {
		super(zeebeClient);
	}
}
