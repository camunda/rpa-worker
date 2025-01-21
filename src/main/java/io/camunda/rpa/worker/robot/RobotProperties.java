package io.camunda.rpa.worker.robot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.rpa.robot")
record RobotProperties(int maxConcurrentJobs) { }
