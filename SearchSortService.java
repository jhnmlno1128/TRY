package brgy.service;

import brgy.ds.DynArray;
import brgy.ds.HashTable;
import brgy.ds.MergeSort;
import brgy.model.Resident;
import brgy.storage.DataStore;

import java.util.Comparator;

/** Searching (name / ID / purok via hash tables) and sorting (merge sort on arrays). */
public class SearchSortService {

    private final DataStore store;
    private final ResidentService residents;

    public SearchSortService(DataStore store, ResidentService residents) {
        this.store = store;
        this.residents = residents;
    }

    // ---------- search ----------

    /** Partial, case-insensitive match; every word typed must appear in the name. Results sorted by name. */
    public Resident[] searchByName(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return new Resident[0];
        String[] words = q.split("[\\s,]+");
        DynArray<Resident> out = new DynArray<Resident>();
        for (Resident r : residents.activeResidents()) {
            String name = (r.getFullName() + " " + r.getLastName() + " " + r.getFirstName()).toLowerCase();
            boolean all = true;
            for (String w : words) if (!name.contains(w)) { all = false; break; }
            if (all) out.add(r);
        }
        return sortByName(out.toArray(new Resident[0]));
    }

    /** O(1) hash lookup. Returns null when not found or not yet approved. */
    public Resident searchById(String id) {
        Resident r = residents.getById(id);
        return (r != null && r.isApproved()) ? r : null;
    }

    /** O(1) hash lookup of the purok's inner table. Results sorted by name. */
    public Resident[] searchByPurok(String purok) {
        HashTable<Resident> bucket = store.residentsByPurok.get(DataStore.purokKey(purok));
        if (bucket == null) return new Resident[0];
        DynArray<Resident> out = new DynArray<Resident>();
        for (Resident r : bucket.values(new Resident[0])) if (r.isApproved()) out.add(r);
        return sortByName(out.toArray(new Resident[0]));
    }

    /** Every purok in use (as typed by staff), alphabetical. */
    public String[] listPuroks() {
        HashTable<Boolean> seen = new HashTable<Boolean>();
        DynArray<String> out = new DynArray<String>();
        for (Resident r : residents.activeResidents()) {
            String k = DataStore.purokKey(r.getPurok());
            if (!seen.containsKey(k)) { seen.put(k, true); out.add(r.getPurok()); }
        }
        return MergeSort.sort(out.toArray(new String[0]), new Comparator<String>() {
            public int compare(String a, String b) { return a.compareToIgnoreCase(b); }
        });
    }

    // ---------- sort (stable merge sort) ----------

    /** Youngest first = later birthdate first. */
    private static final Comparator<Resident> YOUNGEST_FIRST = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) { return b.getBirthdate().compareTo(a.getBirthdate()); }
    };
    private static final Comparator<Resident> OLDEST_FIRST = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) { return a.getBirthdate().compareTo(b.getBirthdate()); }
    };
    private static final Comparator<Resident> BY_SEX = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) { return a.getSex().compareTo(b.getSex()); }
    };
    private static final Comparator<Resident> BY_NAME = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) {
            int c = a.getLastName().compareToIgnoreCase(b.getLastName());
            return c != 0 ? c : a.getFirstName().compareToIgnoreCase(b.getFirstName());
        }
    };
    private static final Comparator<Resident> BY_CLASS = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) { return Integer.compare(classRank(a), classRank(b)); }
    };

    /** 0 = Senior Citizen, 1 = SK member, 2 = everyone else. */
    public static int classRank(Resident r) {
        if (r.isSeniorCitizen()) return 0;
        if (r.isSKMember()) return 1;
        return 2;
    }

    public Resident[] sortByAge(Resident[] list, boolean youngestFirst) {
        return MergeSort.sort(list, youngestFirst ? YOUNGEST_FIRST : OLDEST_FIRST);
    }

    /** Sex first (Male, Female), names alphabetical inside each. */
    public Resident[] sortBySex(Resident[] list) {
        return MergeSort.sort(MergeSort.sort(list, BY_NAME), BY_SEX);
    }

    /** Sex first (Male, Female), then youngest to oldest. */
    public Resident[] sortByAgeAndSex(Resident[] list) {
        return MergeSort.sort(MergeSort.sort(list, YOUNGEST_FIRST), BY_SEX);
    }

    public Resident[] sortByName(Resident[] list) { return MergeSort.sort(list, BY_NAME); }

    /** Senior Citizens first, then SK members, then everyone else; oldest first inside each group. */
    public Resident[] sortByClassification(Resident[] list) {
        return MergeSort.sort(MergeSort.sort(list, OLDEST_FIRST), BY_CLASS);
    }
}
