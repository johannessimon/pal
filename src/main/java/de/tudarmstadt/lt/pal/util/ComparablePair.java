package de.tudarmstadt.lt.pal.util;

/**
 * A key-value pair that can be compared (and thus also sorted) by its value. Values are sorted in reversed order.
 */
public class ComparablePair<K,V extends Comparable<V>> implements Comparable<ComparablePair<K,V>> {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		ComparablePair other = (ComparablePair) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

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
