package io.camunda.rpa.worker

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TestExtension {
	
	static void awaitRequired(CountDownLatch self, long timeout, TimeUnit unit) {
		if( ! self.await(timeout, unit))
			throw new TimeoutException("Latch did not open within %s %s".formatted(timeout, unit));
	}
}
