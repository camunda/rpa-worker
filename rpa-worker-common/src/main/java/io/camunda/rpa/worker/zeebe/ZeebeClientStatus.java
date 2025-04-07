package io.camunda.rpa.worker.zeebe;

import java.util.function.BooleanSupplier;

public interface ZeebeClientStatus extends BooleanSupplier {
	default boolean isZeebeClientEnabled() {
		return getAsBoolean();
	}
}
