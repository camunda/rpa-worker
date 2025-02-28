package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.script.RobotScript;

public record PreparedScript(String executionKey, RobotScript script) {
}
