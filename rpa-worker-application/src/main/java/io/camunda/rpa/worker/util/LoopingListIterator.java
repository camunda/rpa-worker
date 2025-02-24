package io.camunda.rpa.worker.util;

import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class LoopingListIterator<T> implements Iterator<T> {
	
	private final AtomicInteger ptr = new AtomicInteger();
	
	private final List<T> items;
	
	@Override
	public boolean hasNext() {
		return ! items.isEmpty();
	}

	@Override
	public T next() {
		if( ! hasNext()) 
			throw new NoSuchElementException();
		
		return items.get(ptr.getAndUpdate(i -> (i + 1) % items.size()));
	}
}
