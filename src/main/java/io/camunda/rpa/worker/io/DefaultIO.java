package io.camunda.rpa.worker.io;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Component
class DefaultIO implements IO {
	
	@Override
	public <T> Mono<T> supply(Supplier<T> fn) {
		return Mono.fromSupplier(fn).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<Void> run(Runnable fn) {
		return Mono.fromRunnable(fn).subscribeOn(Schedulers.boundedElastic()).then();
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
	public Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) {
		try {
			return Files.createTempFile(prefix, suffix, attrs);
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
	public String readString(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Stream<Path> list(Path path) {
		try {
			return Files.list(path);
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
		try {
			return Files.copy(in, target, copyOptions);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
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

	@Override
	public void doWithFileSystem(Path path, Consumer<FileSystem> fn) {
		try(FileSystem fs = FileSystems.newFileSystem(path)) {
			fn.accept(fs);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Stream<Path> walk(Path path, FileVisitOption... fileVisitOptions) {
		try {
			return Files.walk(path, fileVisitOptions);
		}
		catch (IOException ioex) {
			throw new RuntimeException(ioex);
		}
	}

	@Override
	public Mono<Void> write(Publisher<DataBuffer> source, Path destination, OpenOption... openOptions) {
		return DataBufferUtils.write(source, destination, openOptions);
	}

	@Override
	public boolean isRegularFile(Path path, LinkOption... linkOptions) {
		return Files.isRegularFile(path, linkOptions);
	}
}
