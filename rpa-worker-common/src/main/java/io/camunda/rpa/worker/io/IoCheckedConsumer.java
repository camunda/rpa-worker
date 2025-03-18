package io.camunda.rpa.worker.io;

import java.io.IOException;

@FunctionalInterface
public interface IoCheckedConsumer<T> {
	void accept(T t) throws IOException;
}
