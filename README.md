# Barangay Resident Information System (BRIS)

Java backend for the IC217 Data Structures project. The GUI (Figma) is added later; everything the screens need is in **one class: `brgy.BarangaySystem`**. A console app (`ConsoleApp`) is included so you can use the whole system right now.

## Run

| Command | What it does |
|---|---|
| `build.bat` | compiles `src` into `out` |
| `start-mysql.bat` | starts XAMPP's MySQL (or start it from the XAMPP Control Panel) |
| `run.bat` | builds, then starts the console app (records are saved in MySQL) |
| `test.bat` | builds, then runs 92 automated checks on a separate `bris_test` database (dropped afterwards) |

**MySQL must be running before `run.bat` / `test.bat`.**

The scripts look for a JDK in `JAVA_HOME`, then `%USERPROFILE%\.jdks`, then PATH. (The `java` on this PC's PATH crashes; the JDK in `.jdks` works.)

**Default admin login:** `admin` / `admin123` (change it with `changePassword`).
Residents sign up themselves, then the admin approves them (menu 4 -> 1).

## Data structures (all hand-written, in `brgy.ds`)

Every collection in the system is stored in a hand-written hash table - there are no lists or queues.

- **Keys:** Strings (Resident ID, username, request ID, ...).
- **Hash function:** polynomial rolling hash, `h = (h * 31 + character) mod capacity`.
- **Collisions:** separate chaining - each bucket holds a chain of entries (new entries go to the head).
- **Capacity:** always a prime number, starting at 17. When the load factor passes 0.75 the table **rehashes** into the next prime that is at least twice as big.
- **Operations:** `put` (insert / overwrite), `get`, `containsKey`, `remove` (head, middle or tail of a chain), `keys()`, `values()`. Average O(1).

| Table (in `DataStore`) | Key | Used for |
|---|---|---|
| `residents` | Resident ID | all resident records, O(1) search by ID |
| `accounts` | username | all accounts, O(1) login lookup |
| `requests` | Request ID | all document requests |
| `issuanceLog` | Log ID | issued-document log |
| `residentsByPurok` | purok -> inner table | O(1) search by purok |
| `accountByResidentId` | Resident ID | find a resident's login account |
| `pendingRegistrations`, `pendingRequests` | username / Request ID | items waiting for admin review |

Duplicate detection also uses hash tables: residents are grouped into inner tables by birthdate and by name, so only people in the same group are compared.

Other helpers in `brgy.ds`:
- `MergeSort` - stable merge sort on arrays. Hash tables have no order, so every sorted result (age, sex, SK/Senior, name, ID order, oldest-first requests) is produced by it.
- `StringSimilarity` - Levenshtein distance to flag near-duplicate names.
- `DynArray` - small growable array used only to collect results.

Lists returned by `BarangaySystem` are plain Java arrays (`Resident[]`, `DocumentRequest[]`, ...), which Swing/JavaFX tables and lists accept directly.

## Features -> requirements

- **Login / Logout** - both roles; wrong credentials give an error; residents blocked until approved; logout returns to login.
- **Account registration** - all fields + valid ID upload (copied to `data/uploads`); Resident ID auto-generated (`BRIS-2026-0001`).
- **Records management** - add / view / edit / delete, with validation and purok assignment.
- **Search** - name (partial), Resident ID, purok.  **Sort** - age, sex, age + sex, SK/Senior classification.
- **Duplicate detection** - exact (same name + birthdate), possible (similar name, same birthdate / same name, different birthdate); staff confirms and removes one.
- **Document requests** - Barangay Clearance, Certificate of Residency, Indigency Certificate; admin approves (release date) or rejects (reason); approved ones generate a printable HTML in `data/documents` and an issuance log entry. Unregistered residents cannot request.
- **My Profile** - residents can edit only contact number and address.
- **Reports** - total residents, population by age and sex, SK list, Senior list, issuance log.

SK = ages 15-30, Senior Citizen = 60 and above.

## Connecting your Figma GUI (Swing / JavaFX)

```java
BarangaySystem sys = new BarangaySystem();          // once, shared by all screens

// Login screen button
try {
    Account a = sys.login(usernameField.getText(), passwordField.getText());
    if (a.getRole() == Role.ADMIN) showAdminDashboard(); else showResidentDashboard();
} catch (ValidationException e) {
    showErrorDialog(e.getMessage());                // messages are already user-friendly
}

// Fill a table
Resident[] rows = sys.getAllResidents();   // plain array - loop over it to fill your table

// Logout button
sys.logout();  showLoginScreen();
```

Method groups in `BarangaySystem`: login/logout, `registerResident`, records (`addResident`, `updateResident`, `deleteResident`, `getAllResidents`), search/sort, duplicates, `getPendingRegistrations` / `approveRegistration`, requests (`getPendingRequests`, `approveRequest`, `rejectRequest`, `getDocumentFile`), reports, and the resident side (`getMyProfile`, `updateMyContactInfo`, `submitRequest`, `getMyPendingRequests` / `Approved` / `Rejected`). Every change is saved automatically.

Set the names printed on certificates with `sys.setBarangayInfo(barangay, municipality, province, captain)`.

## Database (MySQL)

MySQL is the permanent storage; the hash tables are the working copy in memory.

1. **Startup:** `new BarangaySystem()` connects, creates the `bris` database and its tables if they don't exist, then loads every row into the hash tables.
2. **While running:** searching, sorting and lookups use the hash tables (O(1) by ID, username, purok).
3. **Every change** (add, edit, delete, approve, request...) is written back to MySQL in one transaction (`INSERT ... ON DUPLICATE KEY UPDATE`, plus `DELETE` for removed rows).

Connection settings are in `db.properties` (XAMPP defaults: `localhost:3306`, user `root`, empty password, database `bris`). If MySQL isn't running you get a readable error: *"Cannot connect to MySQL ... Start MySQL in the XAMPP Control Panel"*.

| Table | Primary key | Holds |
|---|---|---|
| `residents` | `resident_id` | personal info, purok, `approved` flag |
| `accounts` | `username` | salted password hash, role, status, linked resident |
| `requests` | `request_id` | document requests, status, release date, reject reason |
| `issuance_log` | `log_id` | log of issued documents |

`schema.sql` is a copy of the schema for phpMyAdmin. The JDBC driver is `lib\mysql-connector-j-8.4.0.jar` (already on the classpath in the `.bat` files - add `lib\*` to your IDE's libraries too).

To look at the data: open phpMyAdmin -> database `bris`, or run `mysql.exe -u root -e "SELECT * FROM bris.residents"`.

## Files

Records live in MySQL. The `data/` folder only holds the files: `uploads/` (valid IDs residents upload) and `documents/` (printable certificates).
