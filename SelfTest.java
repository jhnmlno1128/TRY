package brgy;

import brgy.ds.*;
import brgy.model.*;
import brgy.service.DuplicateService.DuplicatePair;
import brgy.service.ReportService.AgeSexRow;
import brgy.service.ValidationException;
import brgy.storage.Database;
import brgy.storage.DbConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;

/** Assertion-based checks of every feature. Uses a temporary data folder, so real data is never touched. */
public class SelfTest {

    private static int passed = 0, failed = 0;
    private static final LocalDate TODAY = LocalDate.now();

    private static void check(boolean cond, String name) {
        if (cond) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    private static void expectError(Runnable r, String name) {
        try { r.run(); failed++; System.out.println("  FAIL  " + name + " (no error thrown)"); }
        catch (ValidationException e) { passed++; System.out.println("  PASS  " + name + "  -> \"" + e.getMessage() + "\""); }
    }

    private static String names(Resident[] list) {
        StringBuilder sb = new StringBuilder();
        for (Resident r : list) sb.append(r.getFirstName()).append(',');
        return sb.toString();
    }

    private static Resident add(BarangaySystem s, String first, String last, int age, Sex sex, String purok) {
        return s.addResident(first, "", last, TODAY.minusYears(age), sex, CivilStatus.SINGLE,
                "Sample Street", "09171234567", purok);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("bris_test_");
        Path idFile = Files.createTempFile("valid_id", ".jpg");
        Files.write(idFile, new byte[]{1, 2, 3});

        System.out.println("== Data structures ==");
        testDataStructures();

        // the tests use their own database, so the real "bris" data is never touched
        DbConfig config = DbConfig.load(java.nio.file.Paths.get("db.properties")).withDatabase("bris_test");
        try {
            Database.dropDatabase(config);
        } catch (IllegalStateException e) {
            System.out.println("Cannot run the tests: " + e.getMessage());
            System.exit(2);
        }
        BarangaySystem s = new BarangaySystem(dir, config);

        System.out.println("== Login / access control ==");
        expectError(() -> s.login("admin", "wrong"), "wrong password is rejected");
        expectError(() -> s.login("nobody", "x"), "unknown user is rejected");
        expectError(() -> s.getAllResidents(), "admin features locked before login");
        expectError(() -> s.getMyProfile(), "resident features locked before login");
        s.login("admin", "admin123");
        check(s.getCurrentUser().getRole() == Role.ADMIN, "admin login works");
        s.logout();
        check(!s.isLoggedIn(), "logout clears the session");

        System.out.println("== Resident sign-up and approval ==");
        expectError(() -> s.registerResident("Maria", "", "Santos", TODAY.minusYears(25), Sex.FEMALE,
                CivilStatus.SINGLE, "Rizal St", "abc", "Purok 1", "maria01", "secret1", idFile), "bad contact number rejected");
        expectError(() -> s.registerResident("Maria", "", "Santos", TODAY.plusDays(1), Sex.FEMALE,
                CivilStatus.SINGLE, "Rizal St", "09171234567", "Purok 1", "maria01", "secret1", idFile), "future birthdate rejected");
        expectError(() -> s.registerResident("Maria", "", "Santos", TODAY.minusYears(25), Sex.FEMALE,
                CivilStatus.SINGLE, "Rizal St", "09171234567", "Purok 1", "maria01", "secret1", null), "missing valid ID rejected");
        Resident maria = s.registerResident("Maria", "", "Santos", TODAY.minusYears(25), Sex.FEMALE,
                CivilStatus.SINGLE, "Rizal St", "09171234567", "Purok 1", "maria01", "secret1", idFile);
        check(maria.getId().matches("BRIS-\\d{4}-0001"), "Resident ID auto-generated: " + maria.getId());
        expectError(() -> s.registerResident("Mario", "", "Santos", TODAY.minusYears(25), Sex.MALE,
                CivilStatus.SINGLE, "Rizal St", "09171234567", "Purok 1", "MARIA01", "secret1", idFile), "duplicate username rejected");
        expectError(() -> s.login("maria01", "secret1"), "login blocked while pending approval");

        s.login("admin", "admin123");
        check(s.getPendingRegistrations().length == 1, "registration waiting in admin queue");
        check(s.reportTotalResidents() == 0, "unapproved sign-up not counted as resident");
        s.approveRegistration("maria01");
        check(s.getPendingRegistrations().length == 0, "queue empty after approval");
        check(s.reportTotalResidents() == 1, "approved resident is counted");
        s.logout();

        System.out.println("== Rejected registration ==");
        s.registerResident("Fake", "", "Person", TODAY.minusYears(40), Sex.MALE, CivilStatus.SINGLE,
                "Nowhere", "09171234567", "Purok 9", "fake99", "secret1", idFile);
        s.login("admin", "admin123");
        s.rejectRegistration("fake99", "ID is blurry");
        s.logout();
        try { s.login("fake99", "secret1"); check(false, "rejected user cannot log in"); }
        catch (ValidationException e) { check(e.getMessage().contains("ID is blurry"), "rejected user sees the reason"); }

        System.out.println("== Resident profile ==");
        s.login("maria01", "secret1");
        check(s.getMyProfile().getFullName().equals("Maria Santos"), "resident sees own profile");
        s.updateMyContactInfo("09998887777", "New Address 5");
        check(s.getMyProfile().getAddress().equals("New Address 5"), "address editable");
        check(s.getMyProfile().getContactNumber().equals("09998887777"), "contact editable");
        expectError(() -> s.updateMyContactInfo("12", "x"), "invalid contact on edit rejected");
        expectError(() -> s.getAllResidents(), "resident cannot use admin features");

        System.out.println("== Document requests ==");
        DocumentRequest q1 = s.submitRequest(DocumentType.BARANGAY_CLEARANCE, "Employment");
        check(q1.getStatus() == RequestStatus.PENDING, "request starts as Pending");
        expectError(() -> s.submitRequest(DocumentType.BARANGAY_CLEARANCE, "Again"), "duplicate pending request blocked");
        expectError(() -> s.submitRequest(DocumentType.CERTIFICATE_OF_RESIDENCY, "  "), "purpose is required");
        DocumentRequest q2 = s.submitRequest(DocumentType.CERTIFICATE_OF_INDIGENCY, "Scholarship");
        check(s.getMyPendingRequests().length == 2, "resident sees 2 pending");
        s.logout();

        s.login("admin", "admin123");
        check(s.getPendingRequests()[0].getRequestId().equals(q1.getRequestId()), "pending queue is FIFO");
        s.approveRequest(q1.getRequestId(), null);
        Path doc = s.getDocumentFile(q1.getRequestId());
        check(Files.exists(doc), "printable document generated: " + doc.getFileName());
        check(new String(Files.readAllBytes(doc), "UTF-8").contains("MARIA SANTOS"), "document contains resident name");
        check(s.reportIssuanceLog().length == 1, "issuance log has 1 entry");
        expectError(() -> s.approveRequest(q1.getRequestId(), null), "cannot approve twice");
        s.rejectRequest(q2.getRequestId(), "Not eligible");
        expectError(() -> s.getDocumentFile(q2.getRequestId()), "rejected request has no document");
        s.logout();

        s.login("maria01", "secret1");
        check(s.getMyApprovedRequests().length == 1 && s.getMyApprovedRequests()[0].getDateReleased() != null,
                "approved request shows release date");
        check(s.getMyRejectedRequests()[0].getRejectReason().equals("Not eligible"), "rejected request shows reason");
        check(s.getMyPendingRequests().length == 0, "nothing pending any more");
        s.logout();

        System.out.println("== Records management / search / sort ==");
        s.login("admin", "admin123");
        Resident juan = add(s, "Juan", "Dela Cruz", 14, Sex.MALE, "Purok 1");
        add(s, "Ana", "Reyes", 15, Sex.FEMALE, "Purok 2");
        add(s, "Pedro", "Lim", 30, Sex.MALE, "Purok 1");
        add(s, "Rosa", "Tan", 31, Sex.FEMALE, "Purok 3");
        add(s, "Lola", "Gomez", 59, Sex.FEMALE, "Purok 2");
        Resident lolo = add(s, "Lolo", "Cruz", 60, Sex.MALE, "Purok 2");
        check(s.getAllResidents().length == 7, "7 residents in the database");
        expectError(() -> s.addResident("", "", "X", TODAY.minusYears(5), Sex.MALE, CivilStatus.SINGLE, "a", "09171234567", "P1"),
                "first name is required");

        check(s.searchByName("cruz").length == 2, "search by name (partial, case-insensitive)");
        check(s.searchByName("JUAN dela").length == 1, "search by several words");
        check(s.searchByResidentId(juan.getId()) == juan, "search by Resident ID");
        check(s.searchByResidentId("nope") == null, "unknown ID returns null");
        check(s.searchByPurok("purok 1").length == 3, "search by purok");
        check(s.searchByPurok("Purok 99").length == 0, "unknown purok is empty");

        Resident[] all = s.getAllResidents();
        check(names(s.sortByAge(all, true)).startsWith("Juan,Ana,"), "sort by age youngest first");
        check(names(s.sortByAge(all, false)).startsWith("Lolo,Lola,"), "sort by age oldest first");
        check(names(s.sortByAgeAndSex(all)).equals("Juan,Pedro,Lolo,Ana,Maria,Rosa,Lola,"), "sort by sex then age: " + names(s.sortByAgeAndSex(all)));
        check(names(s.sortBySex(all)).startsWith("Juan,Lolo,Pedro,") || names(s.sortBySex(all)).startsWith("Lolo,"), "sort by sex");
        check(names(s.sortByClassification(all)).equals("Lolo,Pedro,Maria,Ana,Lola,Rosa,Juan,"),
                "sort by Senior/SK classification: " + names(s.sortByClassification(all)));

        System.out.println("== Reports / SK and Senior boundaries ==");
        check(s.reportSkMembers().length == 3, "SK members = ages 15-30 (Ana 15, Maria 25, Pedro 30)");
        check(s.reportSeniorCitizens().length == 1 && s.reportSeniorCitizens()[0] == lolo, "Senior = age 60+ only");
        int male = 0, female = 0;
        for (AgeSexRow r : s.reportPopulationByAgeAndSex()) { male += r.male; female += r.female; }
        check(male == 3 && female == 4, "population by sex M=3 F=4");
        check(s.textFullReport().contains("TOTAL REGISTERED RESIDENTS: 7"), "full text report renders");

        System.out.println("== Duplicate detection ==");
        Resident dupExact = s.addResident("Juan", "", "Dela Cruz", juan.getBirthdate(), Sex.MALE, CivilStatus.SINGLE,
                "Other St", "09171234567", "Purok 1");
        Resident dupFuzzy = s.addResident("Pedro", "", "Limm", TODAY.minusYears(30), Sex.MALE, CivilStatus.SINGLE,
                "Other St", "09171234567", "Purok 1");
        Resident dupName = s.addResident("Rosa", "", "Tan", TODAY.minusYears(40), Sex.FEMALE, CivilStatus.SINGLE,
                "Other St", "09171234567", "Purok 3");
        int exact = 0, possible = 0;
        for (DuplicatePair p : s.findDuplicates()) { if (p.exact) exact++; else possible++; }
        check(exact == 1, "1 exact duplicate detected (got " + exact + ")");
        check(possible == 2, "2 possible matches flagged for review (got " + possible + ")");
        check(s.findDuplicatesOf(dupExact.getId()).length == 1, "findDuplicatesOf works");
        s.removeDuplicate(juan.getId(), dupExact.getId());
        s.deleteResident(dupFuzzy.getId());
        s.deleteResident(dupName.getId());
        check(s.findDuplicates().length == 0, "no duplicates left after removal");
        expectError(() -> s.removeDuplicate(juan.getId(), juan.getId()), "cannot merge a record with itself");
        check(s.getAllResidents().length == 7, "back to 7 residents");

        System.out.println("== Update / delete ==");
        s.updateResident(lolo.getId(), "Lolo", "", "Cruz", lolo.getBirthdate(), Sex.MALE, CivilStatus.WIDOWED,
                "Updated Address", "09170000000", "Purok 4");
        check(s.getResident(lolo.getId()).getCivilStatus() == CivilStatus.WIDOWED, "update saved");
        check(s.searchByPurok("Purok 4").length == 1 && s.searchByPurok("Purok 2").length == 2, "purok index follows the edit");
        s.deleteResident(lolo.getId());
        check(s.searchByResidentId(lolo.getId()) == null, "deleted resident is gone");
        s.logout();

        System.out.println("== MySQL persistence (restart) ==");
        s.close();
        BarangaySystem again = new BarangaySystem(dir, config);
        again.login("admin", "admin123");
        check(again.getAllResidents().length == 6, "residents reloaded from MySQL into the hash tables");
        check(again.reportIssuanceLog().length == 1, "issuance log reloaded");
        check(again.getAllRequests().length == 2, "requests reloaded");
        check(again.searchByResidentId(lolo.getId()) == null && again.searchByPurok("Purok 4").length == 0,
                "deleted resident is gone from MySQL");
        check(again.findDuplicates().length == 0, "removed duplicates stay removed");
        check(again.searchByName("maria santos").length == 1, "search works on reloaded data");
        again.logout();
        again.login("maria01", "secret1");
        check(again.getMyProfile().getAddress().equals("New Address 5"), "resident account + edits survived restart");
        check(again.getMyApprovedRequests().length == 1, "approved request survived restart");
        again.logout();
        try { again.login("fake99", "secret1"); check(false, "rejected registration survived restart"); }
        catch (ValidationException e) { check(e.getMessage().contains("ID is blurry"), "rejected registration + reason survived restart"); }
        again.login("admin", "admin123");
        Resident newcomer = add(again, "Nena", "Aquino", 40, Sex.FEMALE, "Purok 5");
        check(newcomer.getId().endsWith("0007"), "new IDs continue after reload: " + newcomer.getId());
        again.logout();

        System.out.println("== Rows really are in MySQL ==");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(config.databaseUrl(), config.user, config.password);
             java.sql.Statement st = c.createStatement()) {
            java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM residents");
            rs.next();
            check(rs.getInt(1) == 7, "residents table holds 7 rows (6 + Nena)");
            rs = st.executeQuery("SELECT password_hash FROM accounts WHERE username = 'maria01'");
            rs.next();
            check(rs.getString(1).length() == 64 && !rs.getString(1).contains("secret1"), "passwords are stored as hashes");
            rs = st.executeQuery("SELECT COUNT(*) FROM requests WHERE status = 'APPROVED' AND date_released IS NOT NULL");
            rs.next();
            check(rs.getInt(1) == 1, "approved request row has a release date");
        }
        again.close();
        Database.dropDatabase(config);

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Same formula as HashTable.hash() for capacity 17 - used to build keys that collide on purpose. */
    private static int hash17(String key) {
        long h = 0;
        for (int i = 0; i < key.length(); i++) h = (h * 31 + key.charAt(i)) % 17;
        return (int) h;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; (long) i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    private static HashTable<Integer> tableWith(String[] keys) {
        HashTable<Integer> t = new HashTable<Integer>();
        for (int i = 0; i < keys.length; i++) t.put(keys[i], i);
        return t;
    }

    private static void testDataStructures() {
        // three keys that land in the same bucket of a 17-slot table
        String[] col = new String[3];
        int found = 0;
        for (int i = 0; i < 500 && found < 3; i++) {
            String k = "key" + i;
            if (hash17(k) == 3) col[found++] = k;
        }
        check(found == 3, "found 3 colliding keys for the test");

        HashTable<Integer> t = tableWith(col);
        check(t.getCapacity() == 17 && t.getLongestChain() == 3, "collisions share one bucket (chain of 3)");
        check(t.get(col[0]) == 0 && t.get(col[1]) == 1 && t.get(col[2]) == 2, "every key in a chain is found");

        t = tableWith(col);
        t.remove(col[1]);   // middle of the chain (chain order is col2 -> col1 -> col0)
        check(t.get(col[1]) == null && t.get(col[0]) == 0 && t.get(col[2]) == 2 && t.size() == 2, "remove from the middle of a chain");
        t = tableWith(col);
        t.remove(col[2]);   // head of the chain
        check(t.get(col[2]) == null && t.get(col[0]) == 0 && t.get(col[1]) == 1 && t.getLongestChain() == 2, "remove from the head of a chain");
        t = tableWith(col);
        t.remove(col[0]);   // tail of the chain
        check(t.get(col[0]) == null && t.get(col[1]) == 1 && t.get(col[2]) == 2 && t.size() == 2, "remove from the tail of a chain");

        HashTable<Integer> o = new HashTable<Integer>();
        Integer first = o.put("x", 1);
        Integer old = o.put("x", 2);
        check(first == null && old == 1 && o.get("x") == 2 && o.size() == 1, "put on an existing key overwrites it");
        check(o.get("missing") == null && !o.containsKey("missing") && o.get(null) == null && o.remove("missing") == null,
                "missing keys are handled");

        HashTable<Integer> big = new HashTable<Integer>();
        for (int i = 0; i < 1000; i++) big.put("k" + i, i);
        boolean ok = big.size() == 1000;
        for (int i = 0; i < 1000; i++) ok &= big.get("k" + i) != null && big.get("k" + i) == i;
        check(ok, "1000 keys all found after rehashing");
        check(big.getRehashCount() > 0 && isPrime(big.getCapacity()) && big.getCapacity() > 17, "table rehashed to a bigger prime capacity ("
                + big.getCapacity() + ")");
        check(big.getLoadFactor() <= HashTable.MAX_LOAD_FACTOR, "load factor stays at or below 0.75");

        String[] keys = big.keys();
        Integer[] vals = big.values(new Integer[0]);
        long sum = 0;
        for (Integer v : vals) sum += v;
        check(keys.length == 1000 && vals.length == 1000 && sum == 999L * 1000 / 2, "keys() and values() return every entry");
        for (int i = 0; i < 1000; i += 2) big.remove("k" + i);
        check(big.size() == 500 && big.get("k1") == 1 && big.get("k2") == null, "removing half the keys keeps the rest");
        big.clear();
        check(big.isEmpty() && big.getCapacity() == 17, "clear() resets the table");

        DynArray<String> da = new DynArray<String>();
        for (int i = 0; i < 100; i++) da.add("s" + i);
        String[] arr = da.toArray(new String[0]);
        check(arr.length == 100 && arr[99].equals("s99"), "dynamic array grows");

        int[][] pairs = {{2, 1}, {1, 2}, {2, 3}, {1, 4}};
        int[][] sorted = MergeSort.sort(pairs, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) { return Integer.compare(a[0], b[0]); }
        });
        StringBuilder sb = new StringBuilder();
        for (int[] p : sorted) sb.append(p[0]).append(p[1]).append(' ');
        check(sb.toString().trim().equals("12 14 21 23") && pairs[0][1] == 1, "merge sort is correct, stable, and leaves the input alone");

        check(StringSimilarity.levenshtein("kitten", "sitting") == 3, "Levenshtein distance");
        check(StringSimilarity.similarity("pedro lim", "pedro limm") > 0.85, "string similarity");
    }
}
