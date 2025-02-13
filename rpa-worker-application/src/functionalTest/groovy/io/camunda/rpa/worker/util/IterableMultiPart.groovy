package io.camunda.rpa.worker.util

import okhttp3.MultipartReader
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.multipart.DefaultParts
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.util.MultiValueMap

class IterableMultiPart implements Iterable<FormFieldPart> {
	
	private final MultipartReader multipartReader

	IterableMultiPart(MultipartReader multipartReader) {
		this.multipartReader = multipartReader
	}

	@Override
	Iterator<FormFieldPart> iterator() {
		return new MultiPartIterator(multipartReader)
	}
	
	static class MultiPartIterator implements Iterator<FormFieldPart> {
		
		private final MultipartReader multipartReader
		
		private MultipartReader.Part next = null

		MultiPartIterator(MultipartReader multipartReader) {
			this.multipartReader = multipartReader
		}

		@Override
		boolean hasNext() {
			next = multipartReader.nextPart()
			return next != null
		}

		@Override
		FormFieldPart next() { 
			MultipartReader.Part p = next
			next = null
			return DefaultParts.formFieldPart(
					new HttpHeaders(MultiValueMap.fromMultiValue(p.headers().toMultimap())), 
					p.body().readUtf8())
		}
	}
}
