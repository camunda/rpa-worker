package io.camunda.rpa.worker.io;

import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class DefaultIO implements IO {

	private final Scheduler ioScheduler;

	@Override
	public <T> Mono<T> supply(Supplier<T> fn) {
		return Mono.fromSupplier(fn).subscribeOn(ioScheduler);
	}

	@Override
	public Mono<Void> run(Runnable fn) {
		return Mono.fromRunnable(fn).subscribeOn(ioScheduler).then();
	}

	@Override
	public <T> Mono<T> wrap(Mono<T> other) {
		return other.subscribeOn(ioScheduler);
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
		try {
			return Files.copy(source, target, copyOptions);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public long copy(Path source, OutputStream out) {
		try {
			return Files.copy(source, out);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
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
	public Path createTempDirectory(Path dir, String prefix, FileAttribute<?>... attrs) {
		try {
			return Files.createTempDirectory(dir, prefix, attrs);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public boolean exists(Path path, LinkOption... linkOptions) {
		return Files.exists(path, linkOptions);
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
	public void doWithFileSystem(Path path, Map<String, ?> env, Consumer<FileSystem> fn) {
		try (FileSystem fs = FileSystems.newFileSystem(path, env)) {
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
	public Flux<DataBuffer> write(Publisher<DataBuffer> source, OutputStream outputStream) {
		return DataBufferUtils.write(source, outputStream);
	}

	@Override
	public void write(String source, OutputStream outputStream) {
		try {
			OutputStreamWriter w = new OutputStreamWriter(outputStream);
			w.write(source);
			w.close();
		} catch(IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public boolean isRegularFile(Path path, LinkOption... linkOptions) {
		return Files.isRegularFile(path, linkOptions);
	}

	@Override
	public Path walkFileTree(Path start, FileVisitor<Path> visitor) {
		try {
			return Files.walkFileTree(start, visitor);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public String probeContentType(Path path) {
		try {
			return Files.probeContentType(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public long size(Path path) {
		try {
			return Files.size(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public boolean isDirectory(Path path, LinkOption... linkOptions) {
		return Files.isDirectory(path, linkOptions);
	}


	@Override
	public OutputStream newOutputStream(Path destination, OpenOption... openOptions) {
		try {
			return Files.newOutputStream(destination, openOptions);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public void delete(Path path) {
		try {
			Files.delete(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public boolean deleteIfExists(Path path) {
		try {
			return Files.deleteIfExists(path);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public void deleteDirectoryRecursively(Path path) {
		
		if(notExists(path)) return;
		
		walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				super.postVisitDirectory(dir, exc);
				delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public PathMatcher globMatcher(String glob) {
		return FileSystems.getDefault().getPathMatcher("glob:%s".formatted(glob));
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... openOptions) {
		try {
			return Files.newInputStream(path, openOptions);
		} 
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public long transferTo(InputStream inputStream, OutputStream outputStream) {
		try {
			return inputStream.transferTo(outputStream);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Path setPosixFilePermissions(Path path, Set<PosixFilePermission> permissions) {
		try {
			return Files.setPosixFilePermissions(path, permissions);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}

	@Override
	public Set<PosixFilePermission> getPosixFilePermissions(Path path, LinkOption... linkOptions) {
		try {
			return Files.getPosixFilePermissions(path, linkOptions);
		}
		catch (IOException ioex) {
			throw new UncheckedIOException(ioex);
		}
	}
}
