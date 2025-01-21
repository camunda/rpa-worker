package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.robot.ExecutionResult;

import java.util.Map;

record EvaluateScriptResponse(ExecutionResult.Result result, String log, Map<String, Object> variables) { }
