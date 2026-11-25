package brgy.ds;

import java.lang.reflect.Array;
import java.util.Comparator;

/**
 * Hand-written stable merge sort on arrays - O(n log n).
 * Hash tables have no order, so every sorted result (by age, sex, name, ID ...) comes from here.
 * Returns a NEW sorted array; the input array is untouched.
 */
public final class MergeSort {

    private MergeSort() {}

    @SuppressWarnings("unchecked")
    public static <T> T[] sort(T[] input, Comparator<? super T> cmp) {
        Class<?> type = input.getClass().getComponentType();
        T[] a = (T[]) Array.newInstance(type, input.length);
        T[] tmp = (T[]) Array.newInstance(type, input.length);
        for (int i = 0; i < input.length; i++) a[i] = input[i];
        mergeSort(a, tmp, 0, a.length - 1, cmp);
        return a;
    }

    private static <T> void mergeSort(T[] a, T[] tmp, int lo, int hi, Comparator<? super T> cmp) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, cmp);
        mergeSort(a, tmp, mid + 1, hi, cmp);
        int i = lo, j = mid + 1, k = lo;
        while (i <= mid && j <= hi) {
            if (cmp.compare(a[i], a[j]) <= 0) tmp[k++] = a[i++];   // <= keeps the sort stable
            else tmp[k++] = a[j++];
        }
        while (i <= mid) tmp[k++] = a[i++];
        while (j <= hi) tmp[k++] = a[j++];
        for (k = lo; k <= hi; k++) a[k] = tmp[k];
    }
}
