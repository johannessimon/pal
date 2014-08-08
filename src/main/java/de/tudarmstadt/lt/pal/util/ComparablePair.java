package de.tudarmstadt.lt.pal.util;

/**
 * A key-value pair that can be compared (and thus also sorted) by its value. Values are sorted in reversed order.
 */
public class ComparablePair<K,V extends Comparable<V>> implements Comparable<ComparablePair<K,V>> {
	public K key;
	public V value;
	
	public ComparablePair(K k, V v) {
		key = k;
		value = v;
	}

	@Override
	public int compareTo(ComparablePair<K, V> o) {
		return o.value.compareTo(value);
	}
	
	@Override
	public String toString() {
		return "(" + key + ", " + value + ")";
	}
}
