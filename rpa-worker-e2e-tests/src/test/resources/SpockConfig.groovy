import io.camunda.rpa.worker.robot.SmokeTestsE2ESpec

runner {
	if(System.getenv("E2E_SMOKE_TEST_ONLY"))
		include SmokeTestsE2ESpec
}