package io.camunda.rpa.worker.util;

import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MoreCollectors {
	
	public static class MergeStrategy {
		public static <U> BinaryOperator<U> leftPrecedence() {
			return (l, _) -> l;
		}
		
		public static <U> BinaryOperator<U> rightPrecedence() {
			return (_, r) -> r;
		}
		
		public static <U> BinaryOperator<U> noDuplicatesExpected() {
			return (_, _) -> { throw new AssertionError("no duplicate expected"); };
		}
	}
	
	public static <T, K, U> Collector<T, ?, SequencedMap<K, U>> toSequencedMap(
			Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper, 
			BinaryOperator<U> mergeStrategy) {
		
		return Collectors.toMap(
				keyMapper,
				valueMapper,
				mergeStrategy,
				LinkedHashMap::new);
	}
}
