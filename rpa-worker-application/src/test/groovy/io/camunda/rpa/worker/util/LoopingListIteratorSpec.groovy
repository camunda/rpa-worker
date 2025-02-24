package io.camunda.rpa.worker.util

import spock.lang.Specification

class LoopingListIteratorSpec extends Specification {
	
	void "Does not have next for empty collection"() {
		given:
		LoopingListIterator iter = new LoopingListIterator([])
		
		expect:
		! iter.hasNext()
	}
	
	void "Throws on next for empty colletion"() {
		given:
		LoopingListIterator iter = new LoopingListIterator([])

		when:
		iter.next()
		
		then:
		thrown(NoSuchElementException)
	}
	
	void "Loops through items"() {
		given:
		LoopingListIterator<String> iter = new LoopingListIterator<>(["one", "two", "three"])

		expect:
		iter.next() == "one"
		iter.next() == "two"
		iter.next() == "three"
		iter.next() == "one"
		iter.next() == "two"
		iter.next() == "three"
		iter.next() == "one"
	}
}
