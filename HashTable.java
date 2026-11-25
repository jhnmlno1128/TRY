package brgy.ds;

import java.lang.reflect.Array;

/**
 * Hand-written hash table - the core structure of the whole system.
 *
 *  - Keys are Strings (Resident ID, username, request ID, ...).
 *  - Hash function : polynomial rolling hash  h = (h * 31 + character) mod capacity
 *  - Collisions    : separate chaining (every bucket holds a chain of entries)
 *  - Capacity      : always a prime number; when the load factor passes 0.75 the table
 *                    is rehashed into the next prime that is at least twice as big.
 */
public class HashTable<V> {

    private static class Entry<V> {
        final String key;
        V value;
        Entry<V> next;
        Entry(String key, V value, Entry<V> next) { this.key = key; this.value = value; this.next = next; }
    }

    public static final int INITIAL_CAPACITY = 17;
    public static final double MAX_LOAD_FACTOR = 0.75;

    private Entry<V>[] buckets;
    private int size;
    private int rehashCount;

    public HashTable() {
        buckets = newBuckets(INITIAL_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    private Entry<V>[] newBuckets(int capacity) {
        return (Entry<V>[]) new Entry[capacity];
    }

    /** The hash function: maps a key to a bucket index in [0, capacity). */
    static int hash(String key, int capacity) {
        long h = 0;
        for (int i = 0; i < key.length(); i++) h = (h * 31 + key.charAt(i)) % capacity;
        return (int) h;
    }

    // ---------- core operations ----------

    /** Inserts or overwrites. Returns the previous value (or null). */
    public V put(String key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        int idx = hash(key, buckets.length);
        for (Entry<V> e = buckets[idx]; e != null; e = e.next) {
            if (e.key.equals(key)) { V old = e.value; e.value = value; return old; }
        }
        buckets[idx] = new Entry<V>(key, value, buckets[idx]);   // new entry goes to the head of the chain
        size++;
        if ((double) size / buckets.length > MAX_LOAD_FACTOR) rehash();
        return null;
    }

    public V get(String key) {
        if (key == null) return null;
        for (Entry<V> e = buckets[hash(key, buckets.length)]; e != null; e = e.next)
            if (e.key.equals(key)) return e.value;
        return null;
    }

    public boolean containsKey(String key) {
        if (key == null) return false;
        for (Entry<V> e = buckets[hash(key, buckets.length)]; e != null; e = e.next)
            if (e.key.equals(key)) return true;
        return false;
    }

    /** Removes a key (works for the head, middle or tail of a chain). Returns the removed value or null. */
    public V remove(String key) {
        if (key == null) return null;
        int idx = hash(key, buckets.length);
        Entry<V> prev = null;
        for (Entry<V> e = buckets[idx]; e != null; prev = e, e = e.next) {
            if (e.key.equals(key)) {
                if (prev == null) buckets[idx] = e.next; else prev.next = e.next;
                size--;
                return e.value;
            }
        }
        return null;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public void clear() {
        buckets = newBuckets(INITIAL_CAPACITY);
        size = 0;
    }

    // ---------- iteration (unordered - sort the result if order matters) ----------

    public String[] keys() {
        String[] out = new String[size];
        int i = 0;
        for (Entry<V> head : buckets)
            for (Entry<V> e = head; e != null; e = e.next) out[i++] = e.key;
        return out;
    }

    /** Pass an empty array of the wanted type, e.g. {@code table.values(new Resident[0])}. */
    @SuppressWarnings("unchecked")
    public V[] values(V[] empty) {
        V[] out = (V[]) Array.newInstance(empty.getClass().getComponentType(), size);
        int i = 0;
        for (Entry<V> head : buckets)
            for (Entry<V> e = head; e != null; e = e.next) out[i++] = e.value;
        return out;
    }

    // ---------- growth ----------

    private void rehash() {
        Entry<V>[] old = buckets;
        Entry<V>[] bigger = newBuckets(nextPrime(old.length * 2));
        for (Entry<V> head : old) {
            for (Entry<V> e = head; e != null; ) {
                Entry<V> next = e.next;
                int idx = hash(e.key, bigger.length);
                e.next = bigger[idx];
                bigger[idx] = e;
                e = next;
            }
        }
        buckets = bigger;
        rehashCount++;
    }

    static int nextPrime(int n) {
        int p = Math.max(2, n);
        while (!isPrime(p)) p++;
        return p;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; (long) i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    // ---------- statistics (useful for explaining / testing the table) ----------

    public int getCapacity() { return buckets.length; }
    public double getLoadFactor() { return (double) size / buckets.length; }
    public int getRehashCount() { return rehashCount; }

    /** Length of the longest chain = worst-case number of collisions in one bucket. */
    public int getLongestChain() {
        int max = 0;
        for (Entry<V> head : buckets) {
            int n = 0;
            for (Entry<V> e = head; e != null; e = e.next) n++;
            max = Math.max(max, n);
        }
        return max;
    }
}
