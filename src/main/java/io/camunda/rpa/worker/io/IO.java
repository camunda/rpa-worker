package io.camunda.rpa.worker.io;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitor;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface IO {
	<T> Mono<T> supply(Supplier<T> fn);

	Mono<Void> run(Runnable r);

	<T> T withReader(Path path, IoCheckedFunction<Reader, T> fn);
//
	Path createDirectories(Path path);
//
//	void withWriter(Path path, IoCheckedConsumer<Writer> fn);
//
	Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs);
//
//	BufferedReader newBufferedReader(Path path);
//
//	void close(Closeable closeable);
//
	Path writeString(Path path, CharSequence csq, OpenOption... options);
//
	String readString(Path path);
//
//	Stream<String> lines(Path path);
//
	Stream<Path> list(Path path);
//
	Path write(Path path, byte[] bytes, OpenOption... options);
//
	Path copy(Path source, Path target, CopyOption... copyOptions);
//
	long copy(Path source, OutputStream out);
//
	long copy(InputStream in, Path target, CopyOption... copyOptions);
//
	Path createTempDirectory(String prefix, FileAttribute<?>... attrs);
//
//	boolean exists(Path path, LinkOption... linkOptions);
//
	boolean notExists(Path path, LinkOption... linkOptions);

	void doWithFileSystem(Path path, Consumer<FileSystem> fn);
	
	Stream<Path> walk(Path path, FileVisitOption... fileVisitOptions);

	Mono<Void> write(Publisher<DataBuffer> source, Path destination, OpenOption... openOptions);

	boolean isRegularFile(Path path, LinkOption... linkOptions);

	void delete(Path path);

	void deleteDirectoryRecursively(Path p);
	
	Path walkFileTree(Path start, FileVisitor<Path> visitor);
}
