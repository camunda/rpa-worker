package io.camunda.rpa.worker.io;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Supplier;

@Component
class DefaultIO implements IO {
	@Override
	public <T> Mono<T> supply(Supplier<T> fn) {
		return Mono.fromSupplier(fn).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public <T> T withReader(Path path, IoCheckedFunction<Reader, T> fn) {
		try(Reader r = Files.newBufferedReader(path)) {
			return fn.apply(r);
		} catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Path createDirectories(Path path) {
		try {
			return Files.createDirectories(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Path writeString(Path path, CharSequence csq, OpenOption... options) {
		try {
			return Files.writeString(path, csq, options);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}

	}

	@Override
	public Path write(Path path, byte[] bytes, OpenOption... options) {
		try {
			return Files.write(path, bytes, options);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}

	}

	@Override
	public Path copy(Path source, Path target, CopyOption... copyOptions) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long copy(Path source, OutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long copy(InputStream in, Path target, CopyOption... copyOptions) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Path createTempDirectory(String prefix, FileAttribute<?>... attrs) {
		try {
			return Files.createTempDirectory(prefix, attrs);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public boolean notExists(Path path, LinkOption... linkOptions) {
		return Files.notExists(path, linkOptions);
	}
}
