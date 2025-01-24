package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.robot.ExecutionResults;

import java.util.Map;

record EvaluateScriptResponse(ExecutionResults.Result result, String log, Map<String, Object> variables) { }
