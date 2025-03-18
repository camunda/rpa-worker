package io.camunda.rpa.worker.io;

import java.io.IOException;

@FunctionalInterface
public interface IoCheckedSupplier<T> {
	T get() throws IOException;
}
