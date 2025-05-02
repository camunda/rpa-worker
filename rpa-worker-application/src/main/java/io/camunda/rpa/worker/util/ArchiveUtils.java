package io.camunda.rpa.worker.util;

import io.camunda.rpa.worker.io.IO;
import io.vavr.control.Try;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Map;

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
}
