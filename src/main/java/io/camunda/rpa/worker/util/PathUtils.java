package io.camunda.rpa.worker.util;

import java.nio.file.Path;

public class PathUtils {
	
	public static String fixSlashes(Path path) {
		return fixSlashes(path.toString());
	}
	
	public static String fixSlashes(String path) {
		return path.replaceAll("\\\\", "/");
	}
}
