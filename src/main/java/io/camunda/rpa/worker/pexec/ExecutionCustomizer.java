package io.camunda.rpa.worker.pexec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public interface ExecutionCustomizer {
	ExecutionCustomizer arg(String arg);
	ExecutionCustomizer bindArg(String arg, Object value);
	ExecutionCustomizer workDir(Path path);
	ExecutionCustomizer allowExitCode(int code);
	ExecutionCustomizer allowExitCodes(int[] codes);
	ExecutionCustomizer env(String name, String value);
	ExecutionCustomizer env(Map<String, String> map);
	ExecutionCustomizer inheritEnv();
	ExecutionCustomizer noFail();
	ExecutionCustomizer timeout(Duration timeout);
}
