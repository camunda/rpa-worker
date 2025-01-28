package io.camunda.rpa.worker.pexec;

import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Service
@Slf4j
public class ProcessService {
	
	private final Scheduler robotWorkScheduler;
	private final Supplier<DefaultExecutor.Builder<?>> executorBuilderFactory;
	
	public record ExecutionResult(int exitCode, String stdout, String stderr) {}

	@Autowired
	public ProcessService(Scheduler robotWorkScheduler) {
		this(robotWorkScheduler, DefaultExecutor.Builder::new);
	}
	
	ProcessService(Scheduler robotWorkScheduler, Supplier<DefaultExecutor.Builder<?>> executorBuilderFactory) {
		this.robotWorkScheduler = robotWorkScheduler;
		this.executorBuilderFactory = executorBuilderFactory;
	}
	
	public Mono<ExecutionResult> execute(Object executable, UnaryOperator<ExecutionCustomizer> customization) {

		Map<String, Object> bindings = new HashMap<>();
		Set<Integer> allowedExitCodes = new HashSet<>();
		Map<String, String> environment = new HashMap<>();

		CommandLine cmdLine = executable instanceof Path p 
				? new CommandLine(p.toFile()) 
				: new CommandLine(executable.toString());
		DefaultExecutor.Builder<?> executorBuilder = executorBuilderFactory.get();

		allowedExitCodes.add(0);

		IntrospectableExecutionCustomizer executionCustomizer = new IntrospectableExecutionCustomizer() {
			
			@Getter
			private Duration timeout;
			
			@Override
			public ExecutionCustomizer arg(String arg) {
				cmdLine.addArgument(arg);
				return this;
			}

			@Override
			public ExecutionCustomizer bindArg(String name, Object value) {
				cmdLine.addArgument("${%s}".formatted(name));
				bindings.put(name, value instanceof Path p ? p.toFile() : value);
				return this;
			}

			@Override
			public ExecutionCustomizer workDir(Path path) {
				executorBuilder.setWorkingDirectory(path.toFile());
				return this;
			}

			@Override
			public ExecutionCustomizer allowExitCode(int code) {
				allowedExitCodes.add(code);
				return this;
			}

			@Override
			public ExecutionCustomizer allowExitCodes(int[] codes) {
				Arrays.stream(codes).forEach(allowedExitCodes::add);
				return this;
			}

			@Override
			public ExecutionCustomizer env(String name, String value) {
				environment.put(name, value);
				return this;
			}

			@Override
			public ExecutionCustomizer env(Map<String, String> map) {
				environment.putAll(map);
				return this;
			}

			@Override
			public ExecutionCustomizer inheritEnv() {
				return env(System.getenv());
			}

			@Override
			public ExecutionCustomizer noFail() {
				return allowExitCode(Integer.MIN_VALUE);
			}

			@Override
			public ExecutionCustomizer timeout(Duration newTimeout) {
				this.timeout = newTimeout;
				return this;
			}
		};
		customization.apply(executionCustomizer);

		cmdLine.setSubstitutionMap(bindings);
		DefaultExecutor defaultExecutor = executorBuilder.get();
		defaultExecutor.setExitValues(allowedExitCodes.contains(Integer.MIN_VALUE) 
				? null 
				: allowedExitCodes.stream().mapToInt(i -> i).toArray());
		ExecuteWatchdog2 watchdog = new ExecuteWatchdog2();
		defaultExecutor.setWatchdog(watchdog);

		StreamHandler streamHandler = new StreamHandler();
		defaultExecutor.setStreamHandler(streamHandler);

		return Mono.fromSupplier(() -> Try.of(() -> defaultExecutor.execute(cmdLine, environment))
						.onFailure(thrown -> log.atError()
								.setCause(thrown)
								.addKeyValue("stderr", streamHandler.getErrString())
								.addKeyValue("stdout", streamHandler.getOutString())
								.log("Process execution failed"))
						.recover(ExecuteException.class, ExecuteException::getExitValue)
						.map(exitCode -> new ExecutionResult(
								exitCode,
								streamHandler.getOutString(),
								streamHandler.getErrString()))
						.get())
				.subscribeOn(robotWorkScheduler)
				.as(p -> executionCustomizer.getTimeout() != null 
						? p.timeout(executionCustomizer.getTimeout(),
								Mono.<ExecutionResult>error(() -> new ProcessTimeoutException(streamHandler.getOutString(), streamHandler.getErrString()))
							.doOnSubscribe(_ -> watchdog.getProcess().toHandle().destroy()))
						: p);
	}
	
	private interface IntrospectableExecutionCustomizer extends ExecutionCustomizer {
		Duration getTimeout();
	}

	static class StreamHandler extends PumpStreamHandler {
		
		private final StringBuilder out;
		private final StringBuilder err;

		public StreamHandler() {
			StringBuilder out = new StringBuilder();
			LogOutputStream outStream = new LogOutputStream() {
				@Override
				protected void processLine(String line, int logLevel) {
					out.append(line).append("\n");
				}
			};
			StringBuilder err = new StringBuilder();
			LogOutputStream errStream = new LogOutputStream() {

				@Override
				protected void processLine(String line, int logLevel) {
					err.append(line).append("\n");
				}
			};
			
			super(outStream, errStream);
			this.out = out;
			this.err = err;
		}
		
		public String getOutString() {
			return out.toString().trim();
		}
		
		public String getErrString() {
			return err.toString().trim();
		}

		@Override
		public OutputStream getOut() {
			return super.getOut();
		}

		@Override
		public OutputStream getErr() {
			return super.getErr();
		}
	}
}
