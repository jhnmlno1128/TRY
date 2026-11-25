package brgy.service;

import brgy.ds.DynArray;
import brgy.ds.HashTable;
import brgy.ds.MergeSort;
import brgy.ds.StringSimilarity;
import brgy.model.Account;
import brgy.model.DocumentRequest;
import brgy.model.Resident;
import brgy.storage.DataStore;

import java.util.Comparator;

/** Detects duplicate resident entries (hash-table grouping + Levenshtein) and removes confirmed ones. */
public class DuplicateService {

    public static final double SIMILARITY_THRESHOLD = 0.85;

    /** A flagged pair of records for staff review. */
    public static class DuplicatePair {
        public final Resident first;
        public final Resident second;
        /** true = same name AND same birthdate; false = "possible match" needing human judgement. */
        public final boolean exact;
        public final String reason;

        DuplicatePair(Resident first, Resident second, boolean exact, String reason) {
            this.first = first;
            this.second = second;
            this.exact = exact;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return (exact ? "[EXACT] " : "[POSSIBLE] ") + first.getId() + " " + first.getFullName()
                    + "  <->  " + second.getId() + " " + second.getFullName() + "  (" + reason + ")";
        }
    }

    private final DataStore store;
    private final ResidentService residents;

    public DuplicateService(DataStore store, ResidentService residents) {
        this.store = store;
        this.residents = residents;
    }

    static String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private static double nameSimilarity(Resident a, Resident b) {
        double full = StringSimilarity.similarity(normalize(a.getFullName()), normalize(b.getFullName()));
        double firstLast = StringSimilarity.similarity(
                normalize(a.getFirstName() + " " + a.getLastName()),
                normalize(b.getFirstName() + " " + b.getLastName()));
        return Math.max(full, firstLast);
    }

    private static String pairKey(Resident a, Resident b) {
        return DataStore.idCompare(a.getId(), b.getId()) < 0 ? a.getId() + "#" + b.getId() : b.getId() + "#" + a.getId();
    }

    private static final Comparator<Resident> BY_ID = new Comparator<Resident>() {
        public int compare(Resident a, Resident b) { return DataStore.idCompare(a.getId(), b.getId()); }
    };

    /** Puts every resident into the group (inner hash table) stored under the given key. */
    private static void group(HashTable<HashTable<Resident>> groups, String key, Resident r) {
        HashTable<Resident> bucket = groups.get(key);
        if (bucket == null) { bucket = new HashTable<Resident>(); groups.put(key, bucket); }
        bucket.put(DataStore.key(r.getId()), r);
    }

    private static Resident[] members(HashTable<Resident> bucket) {
        return MergeSort.sort(bucket.values(new Resident[0]), BY_ID);
    }

    /** Scans all records (including sign-ups awaiting approval) and returns flagged pairs. */
    public DuplicatePair[] findDuplicates() {
        DynArray<DuplicatePair> out = new DynArray<DuplicatePair>();
        HashTable<Boolean> seen = new HashTable<Boolean>();
        Resident[] all = store.residentsSorted();

        // 1) hash-group by birthdate, compare names inside each group
        HashTable<HashTable<Resident>> byBirth = new HashTable<HashTable<Resident>>();
        for (Resident r : all) group(byBirth, r.getBirthdate().toString(), r);
        for (String k : MergeSort.sort(byBirth.keys(), String.CASE_INSENSITIVE_ORDER)) {
            Resident[] arr = members(byBirth.get(k));
            for (int i = 0; i < arr.length; i++) {
                for (int j = i + 1; j < arr.length; j++) {
                    Resident a = arr[i], b = arr[j];
                    if (normalize(a.getFullName()).equals(normalize(b.getFullName()))) {
                        out.add(new DuplicatePair(a, b, true, "same name and birthdate"));
                        seen.put(pairKey(a, b), true);
                    } else {
                        double sim = nameSimilarity(a, b);
                        if (sim >= SIMILARITY_THRESHOLD) {
                            out.add(new DuplicatePair(a, b, false,
                                    "same birthdate, name " + Math.round(sim * 100) + "% similar"));
                            seen.put(pairKey(a, b), true);
                        }
                    }
                }
            }
        }

        // 2) hash-group by exact name, flag the ones with different birthdates
        HashTable<HashTable<Resident>> byName = new HashTable<HashTable<Resident>>();
        for (Resident r : all) group(byName, normalize(r.getFullName()), r);
        for (String k : MergeSort.sort(byName.keys(), String.CASE_INSENSITIVE_ORDER)) {
            Resident[] arr = members(byName.get(k));
            for (int i = 0; i < arr.length; i++) {
                for (int j = i + 1; j < arr.length; j++) {
                    if (seen.containsKey(pairKey(arr[i], arr[j]))) continue;
                    out.add(new DuplicatePair(arr[i], arr[j], false, "same name, different birthdate"));
                    seen.put(pairKey(arr[i], arr[j]), true);
                }
            }
        }
        return out.toArray(new DuplicatePair[0]);
    }

    /** Pairs involving one specific resident (handy right after adding or approving a record). */
    public DuplicatePair[] findDuplicatesOf(String residentId) {
        DynArray<DuplicatePair> out = new DynArray<DuplicatePair>();
        for (DuplicatePair p : findDuplicates())
            if (p.first.getId().equalsIgnoreCase(residentId) || p.second.getId().equalsIgnoreCase(residentId)) out.add(p);
        return out.toArray(new DuplicatePair[0]);
    }

    /**
     * Staff confirmed the two records are the same person: keep one, delete the other.
     * Requests and the login account of the removed record are moved to the kept record.
     */
    public void removeDuplicate(String keepId, String removeId) {
        if (keepId == null || removeId == null || keepId.equalsIgnoreCase(removeId))
            throw new ValidationException("Choose two different records.");
        Resident keep = residents.require(keepId);
        Resident remove = residents.require(removeId);

        Account removedAcct = residents.getAccountOf(remove.getId());
        if (removedAcct != null) {
            if (residents.getAccountOf(keep.getId()) == null) removedAcct.setResidentId(keep.getId());
            else store.removeAccount(removedAcct);
        }
        for (DocumentRequest q : store.requests.values(new DocumentRequest[0])) {
            if (q.getResidentId().equalsIgnoreCase(remove.getId())) {
                q.setResidentId(keep.getId());
                q.setResidentName(keep.getFullName());
            }
        }
        store.removeResident(remove);
        store.save();
    }
}
