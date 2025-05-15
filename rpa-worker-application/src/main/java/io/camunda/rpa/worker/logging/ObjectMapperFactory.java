package io.camunda.rpa.worker.logging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

public interface ObjectMapperFactory extends Supplier<ObjectMapper> {
}
