package io.camunda.rpa.worker

class TempZeebeClientE2ESpec extends AbstractE2ESpec {
	
	void "Nothing"() {
		expect:
		println zeebeClient.newTopologyRequest().send().join()
	}
}
