package io.camunda.rpa.worker.io;

import java.io.IOException;

@FunctionalInterface
public interface IoCheckedFunction<T, R> {
	R apply(T t) throws IOException;
}
