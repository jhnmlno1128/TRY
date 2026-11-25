package brgy.ds;

import java.lang.reflect.Array;

/** Tiny growable array, used only to collect results before returning them as a plain array. */
public class DynArray<T> {

    private Object[] items = new Object[8];
    private int size;

    public void add(T item) {
        if (size == items.length) {
            Object[] bigger = new Object[items.length * 2];
            for (int i = 0; i < size; i++) bigger[i] = items[i];
            items = bigger;
        }
        items[size++] = item;
    }

    public int size() { return size; }

    @SuppressWarnings("unchecked")
    public T get(int i) { return (T) items[i]; }

    /** Pass an empty array of the wanted type, e.g. {@code list.toArray(new Resident[0])}. */
    @SuppressWarnings("unchecked")
    public T[] toArray(T[] empty) {
        T[] out = (T[]) Array.newInstance(empty.getClass().getComponentType(), size);
        for (int i = 0; i < size; i++) out[i] = (T) items[i];
        return out;
    }
}
