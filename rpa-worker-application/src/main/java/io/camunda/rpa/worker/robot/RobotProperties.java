package io.camunda.rpa.worker.robot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("camunda.rpa.robot")
record RobotProperties(Duration defaultTimeout, boolean failFast) { }
