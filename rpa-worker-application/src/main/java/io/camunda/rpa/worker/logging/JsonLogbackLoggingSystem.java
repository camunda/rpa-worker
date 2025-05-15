package io.camunda.rpa.worker.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JsonLogbackLoggingSystem extends LogbackLoggingSystem {

	private static final String DEV_DEDUCER_CLASS_NAME = "org.springframework.boot.devtools.system.DevToolsEnablementDeducer";

	private static ObjectMapper objectMapper;
	private static ThrowableProxyConverter throwableProxyConverter;

    public JsonLogbackLoggingSystem(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected void loadDefaults(LoggingInitializationContext initializationContext, LogFile logFile) {
        super.loadDefaults(initializationContext, logFile);
        reconfigure(initializationContext.getEnvironment());
    }

    @Override
    protected void loadConfiguration(LoggingInitializationContext initializationContext, String location, LogFile logFile) {
        super.loadConfiguration(initializationContext, location, logFile);
        reconfigure(initializationContext.getEnvironment());
    }

    private void reconfigure(Environment environment) {
	    boolean useJson = environment.containsProperty("json.logging.enabled")
			    ? environment.getProperty("json.logging.enabled", boolean.class)
			    : ! isDev();

	    if (useJson)
		    reconfigureJson(environment);
	    else
		    reconfigurePlain(environment);
    }
	
	private void reconfigurePlain(Environment environment) {
		getAppenders().forEach(a -> {
			PatternLayoutEncoder encoder = (PatternLayoutEncoder) a.getEncoder();
			encoder.setPattern(encoder.getPattern()
					.replace("%m%n", "%m %kvp%n"));
			encoder.start();
		});
	}
	
	private void reconfigureJson(Environment environment) {
		objectMapper = createObjectMapper(environment);
		throwableProxyConverter = new ThrowableProxyConverter();
		throwableProxyConverter.start();

		getAppenders().forEach(a -> a.setLayout(new LayoutBase<>() {
			@Override
			public String doLayout(ILoggingEvent event) {
				return render(event);
			}
		}));
	}

	private static Stream<OutputStreamAppender<ILoggingEvent>> getAppenders() {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(((LoggerContext) LoggerFactory
						.getILoggerFactory())
						.getLogger(Logger.ROOT_LOGGER_NAME)
						.iteratorForAppenders(), 0), false)
				.map(a -> (OutputStreamAppender<ILoggingEvent>) a);
	}

	private String render(ILoggingEvent event) {
        try {
			return objectMapper.writeValueAsString(new LoggingEvent(
					event.getInstant().toString(),
					event.getLevel().toString(),
					event.getFormattedMessage(),
					event.getLoggerName(),
					event.getThreadName(),
					thrownFor(event),
					
					Optional.ofNullable(event.getKeyValuePairs())
							.orElseGet(Collections::emptyList)
							.stream()
							.collect(Collectors.toMap(kv -> kv.key, kv -> kv.value.toString())),
					
					Optional.ofNullable(event.getMarkerList())
							.stream()
							.flatMap(Collection::stream)
							.map(Marker::getName)
							.toList())) + "\n";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

	private LoggingEvent.Thrown thrownFor(ILoggingEvent loggingEvent) {
		IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
		
		if(throwableProxy == null)
			return null;
		
		return new LoggingEvent.Thrown(
				throwableProxy.getClassName(),
				throwableProxy.getMessage(), 
				throwableProxyConverter.convert(loggingEvent));
	}

	private boolean isDev() {
		if( ! ClassUtils.isPresent(DEV_DEDUCER_CLASS_NAME, getClassLoader()))
			return false;

		try {
			Class<?> deducer = Class.forName(DEV_DEDUCER_CLASS_NAME);
			Method shouldEnable = deducer.getMethod("shouldEnable", Thread.class);
			return (boolean) shouldEnable.invoke(null, Thread.currentThread());
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private ObjectMapper createObjectMapper(Environment environment) {
		try {
			Class<ObjectMapperFactory> omfClass =
					(Class<ObjectMapperFactory>) Class.forName(
							environment.getProperty(
									"logging.json.objectMapperFactory", 
									"io.camunda.rpa.worker.logging.DefaultObjectMapperFactory"));

			ObjectMapperFactory omf = (ObjectMapperFactory) omfClass.getDeclaredConstructors()[0].newInstance();
			return omf.get();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	@Order(Ordered.HIGHEST_PRECEDENCE)
    public static class Factory implements LoggingSystemFactory {

		@Override
        public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
            return new JsonLogbackLoggingSystem(classLoader);
        }
	}
}
