package io.camunda.rpa.worker

import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest

class FileHashUtils {
	
	static String hashFile(Path path) {
		return hashFile(path.text)
	}
	
	static String hashFile(String text) {
		DigestOutputStream stream = new DigestOutputStream(OutputStream.nullOutputStream(), MessageDigest.getInstance("sha-256"))
		stream.write(text.bytes)
		return HexFormat.of().formatHex(stream.messageDigest.digest())
	}

}