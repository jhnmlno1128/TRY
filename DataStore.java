package brgy.storage;

import brgy.ds.HashTable;
import brgy.ds.MergeSort;
import brgy.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * The in-memory database: EVERY collection is a {@link HashTable}.
 * MySQL ({@link Database}) is the permanent storage: everything is loaded into the hash tables when the
 * system starts, searches and lookups use the hash tables, and every change is written back to MySQL.
 *
 *  residents            key = Resident ID (upper case)     master records + O(1) search by ID
 *  accounts             key = username (lower case)        master records + O(1) login lookup
 *  requests             key = Request ID (upper case)      master records
 *  issuanceLog          key = Log ID                       master records
 *  residentsByPurok     key = purok -> inner table of residents   O(1) purok search
 *  accountByResidentId  key = Resident ID                  find the login account of a resident
 *  pendingRequests      key = Request ID                   requests waiting for review
 *  pendingRegistrations key = username (lower case)        sign-ups waiting for approval
 *  deleted*             keys removed since the last save   tells MySQL which rows to delete
 */
public class DataStore {

    private final Path filesDir;
    private final Database db;

    public final HashTable<Resident> residents = new HashTable<Resident>();
    public final HashTable<Account> accounts = new HashTable<Account>();
    public final HashTable<DocumentRequest> requests = new HashTable<DocumentRequest>();
    public final HashTable<IssuanceLogEntry> issuanceLog = new HashTable<IssuanceLogEntry>();

    public final HashTable<HashTable<Resident>> residentsByPurok = new HashTable<HashTable<Resident>>();
    public final HashTable<Account> accountByResidentId = new HashTable<Account>();
    public final HashTable<DocumentRequest> pendingRequests = new HashTable<DocumentRequest>();
    public final HashTable<Account> pendingRegistrations = new HashTable<Account>();

    private final HashTable<Boolean> deletedResidents = new HashTable<Boolean>();
    private final HashTable<Boolean> deletedAccounts = new HashTable<Boolean>();
    private final HashTable<Boolean> deletedRequests = new HashTable<Boolean>();

    /** @param filesDir folder for uploaded IDs and generated certificates (the records live in MySQL) */
    public DataStore(Path filesDir, Database db) {
        this.filesDir = filesDir;
        this.db = db;
        try {
            Files.createDirectories(filesDir);
            Files.createDirectories(getUploadsDir());
            Files.createDirectories(getDocumentsDir());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data folder: " + filesDir, e);
        }
        load();
    }

    public Path getDir() { return filesDir; }
    public Path getUploadsDir() { return filesDir.resolve("uploads"); }
    public Path getDocumentsDir() { return filesDir.resolve("documents"); }
    public Database getDatabase() { return db; }

    // ---------- keys ----------

    public static String key(String id) { return id == null ? "" : id.trim().toUpperCase(); }
    public static String userKey(String username) { return username == null ? "" : username.trim().toLowerCase(); }
    public static String purokKey(String purok) { return purok == null ? "" : purok.trim().toLowerCase(); }

    /** Orders IDs like BRIS-2026-0002 before BRIS-2026-0010 (shorter first, then alphabetical). */
    public static int idCompare(String a, String b) {
        if (a.length() != b.length()) return a.length() < b.length() ? -1 : 1;
        return a.compareTo(b);
    }

    // ---------- put / remove helpers (keep keys and the delete list consistent) ----------

    public void putResident(Resident r) { residents.put(key(r.getId()), r); deletedResidents.remove(key(r.getId())); }
    public void removeResident(Resident r) { residents.remove(key(r.getId())); deletedResidents.put(key(r.getId()), true); }
    public void putAccount(Account a) { accounts.put(userKey(a.getUsername()), a); deletedAccounts.remove(userKey(a.getUsername())); }
    public void removeAccount(Account a) { accounts.remove(userKey(a.getUsername())); deletedAccounts.put(userKey(a.getUsername()), true); }
    public void putRequest(DocumentRequest q) { requests.put(key(q.getRequestId()), q); deletedRequests.remove(key(q.getRequestId())); }
    public void removeRequest(DocumentRequest q) { requests.remove(key(q.getRequestId())); deletedRequests.put(key(q.getRequestId()), true); }
    public void putLog(IssuanceLogEntry e) { issuanceLog.put(key(e.getLogId()), e); }

    // ---------- sorted snapshots (hash tables are unordered; ID order = order of creation) ----------

    public Resident[] residentsSorted() {
        return MergeSort.sort(residents.values(new Resident[0]), new Comparator<Resident>() {
            public int compare(Resident a, Resident b) { return idCompare(a.getId(), b.getId()); }
        });
    }

    public Account[] accountsSorted() {
        return MergeSort.sort(accounts.values(new Account[0]), new Comparator<Account>() {
            public int compare(Account a, Account b) { return a.getUsername().compareToIgnoreCase(b.getUsername()); }
        });
    }

    public DocumentRequest[] requestsSorted() { return sortRequests(requests.values(new DocumentRequest[0])); }
    public DocumentRequest[] pendingRequestsSorted() { return sortRequests(pendingRequests.values(new DocumentRequest[0])); }

    private DocumentRequest[] sortRequests(DocumentRequest[] arr) {
        return MergeSort.sort(arr, new Comparator<DocumentRequest>() {
            public int compare(DocumentRequest a, DocumentRequest b) { return idCompare(a.getRequestId(), b.getRequestId()); }
        });
    }

    public IssuanceLogEntry[] logSorted() {
        return MergeSort.sort(issuanceLog.values(new IssuanceLogEntry[0]), new Comparator<IssuanceLogEntry>() {
            public int compare(IssuanceLogEntry a, IssuanceLogEntry b) { return idCompare(a.getLogId(), b.getLogId()); }
        });
    }

    /** Sign-ups waiting for approval, oldest first (Resident IDs are sequential). */
    public Account[] pendingRegistrationsSorted() {
        return MergeSort.sort(pendingRegistrations.values(new Account[0]), new Comparator<Account>() {
            public int compare(Account a, Account b) { return idCompare(a.getResidentId(), b.getResidentId()); }
        });
    }

    /** Rebuilds the secondary hash tables (purok index, account-by-resident, pending tables). */
    public void reindex() {
        residentsByPurok.clear();
        accountByResidentId.clear();
        pendingRequests.clear();
        pendingRegistrations.clear();

        for (Resident r : residents.values(new Resident[0])) {
            String k = purokKey(r.getPurok());
            HashTable<Resident> bucket = residentsByPurok.get(k);
            if (bucket == null) { bucket = new HashTable<Resident>(); residentsByPurok.put(k, bucket); }
            bucket.put(key(r.getId()), r);
        }
        for (Account a : accounts.values(new Account[0])) {
            if (!a.getResidentId().isEmpty()) accountByResidentId.put(key(a.getResidentId()), a);
            if (a.getRole() == Role.RESIDENT && a.getStatus() == AccountStatus.PENDING)
                pendingRegistrations.put(userKey(a.getUsername()), a);
        }
        for (DocumentRequest q : requests.values(new DocumentRequest[0]))
            if (q.getStatus() == RequestStatus.PENDING) pendingRequests.put(key(q.getRequestId()), q);
    }

    // ---------- ID generation ----------

    public String nextResidentId() {
        String prefix = "BRIS-" + java.time.LocalDate.now().getYear() + "-";
        int max = 0;
        for (String id : residents.keys()) max = Math.max(max, seqOf(id, prefix));
        return prefix + String.format("%04d", max + 1);
    }

    public String nextRequestId() {
        int max = 0;
        for (String id : requests.keys()) max = Math.max(max, seqOf(id, "REQ-"));
        return "REQ-" + String.format("%05d", max + 1);
    }

    public String nextLogId() {
        int max = 0;
        for (String id : issuanceLog.keys()) max = Math.max(max, seqOf(id, "LOG-"));
        return "LOG-" + String.format("%05d", max + 1);
    }

    private int seqOf(String id, String prefix) {
        if (id == null || !id.toUpperCase().startsWith(prefix)) return 0;
        try { return Integer.parseInt(id.substring(prefix.length())); }
        catch (NumberFormatException e) { return 0; }
    }

    // ---------- persistence (MySQL) ----------

    /** Refreshes the indexes and writes every change to MySQL in one transaction. */
    public void save() {
        reindex();
        db.sync(residents.values(new Resident[0]), accounts.values(new Account[0]),
                requests.values(new DocumentRequest[0]), issuanceLog.values(new IssuanceLogEntry[0]),
                deletedResidents.keys(), deletedAccounts.keys(), deletedRequests.keys());
        deletedResidents.clear();
        deletedAccounts.clear();
        deletedRequests.clear();
    }

    /** Loads every row from MySQL into the hash tables. */
    private void load() {
        for (Resident r : db.loadResidents()) putResident(r);
        for (Account a : db.loadAccounts()) putAccount(a);
        for (DocumentRequest q : db.loadRequests()) putRequest(q);
        for (IssuanceLogEntry e : db.loadIssuanceLog()) putLog(e);
        reindex();
    }
}
