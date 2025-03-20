package io.camunda.rpa.worker.workspace.api;

import java.util.Map;

record AttachVariablesRequest(Map<String, Object> variables) { }
