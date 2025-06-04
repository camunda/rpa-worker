package io.camunda.rpa.worker.util;

import io.camunda.rpa.worker.io.IO;
import io.vavr.control.Try;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class ArchiveUtils {

	public static void extractArchive(IO io, Path archive, Path destination) {
		io.doWithFileSystem(archive, Map.of("enablePosixFileAttributes", true),zip -> {
			Path root = zip.getPath("/");
			io.walk(root)
					.filter(io::isRegularFile)
					.forEach(p -> {
						io.createDirectories(destination.resolve(root.relativize(p).getParent().toString()));
						Path thisFileDest = destination.resolve(root.relativize(p).toString());
						io.copy(p, thisFileDest);
						Try.run(() -> io.setPosixFilePermissions(thisFileDest,
								Files.getFileAttributeView(p, PosixFileAttributeView.class).readAttributes().permissions()));
					});
		});
	}
	
	private static final ExecutorService gzipExecutor = Executors.newVirtualThreadPerTaskExecutor();
	
	public static Mono<byte[]> inflateBase64(IO io, String string) {
		return inflateBytes(io, Base64.getDecoder().decode(string.getBytes()));
	}
	
	public static Mono<byte[]> inflateBytes(IO io, byte[] bytes) {
		return io.supply(() -> Try.withResources(
						() -> new GZIPInputStream(new ByteArrayInputStream(bytes)),
						ByteArrayOutputStream::new)
				.of((gzipInputStream, out) -> {
					gzipInputStream.transferTo(out);
					return out.toByteArray();
				})
				.get());
	}
}
