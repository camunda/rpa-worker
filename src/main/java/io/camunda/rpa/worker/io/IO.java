package io.camunda.rpa.worker.io;

import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Supplier;

public interface IO {
	<T> Mono<T> supply(Supplier<T> fn);

	<T> T withReader(Path path, IoCheckedFunction<Reader, T> fn);
//
	Path createDirectories(Path path);
//
//	void withWriter(Path path, IoCheckedConsumer<Writer> fn);
//
//	Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs);
//
//	BufferedReader newBufferedReader(Path path);
//
//	void close(Closeable closeable);
//
	Path writeString(Path path, CharSequence csq, OpenOption... options);
//
//	String readString(Path path);
//
//	Stream<String> lines(Path path);
//
//	Stream<Path> list(Path path);
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
}
