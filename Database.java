package brgy.storage;

import brgy.ds.DynArray;
import brgy.model.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** MySQL access (JDBC). The system loads everything into hash tables at startup and writes changes back here. */
public class Database {

    private final DbConfig config;
    private Connection conn;

    /** Connects (creating the database and tables on first run). */
    public Database(DbConfig config) {
        this.config = config;
        try {
            // 1) make sure the database exists
            try (Connection c = DriverManager.getConnection(config.serverUrl(), config.user, config.password);
                 Statement st = c.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + config.database
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
            // 2) connect to it and create the tables
            conn = DriverManager.getConnection(config.databaseUrl(), config.user, config.password);
            createTables();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect to MySQL at " + config.host + ":" + config.port
                    + " (user '" + config.user + "'). Start MySQL in the XAMPP Control Panel or run start-mysql.bat, "
                    + "and check db.properties.\nDetails: " + e.getMessage(), e);
        }
    }

    public DbConfig getConfig() { return config; }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) { }
        conn = null;
    }

    /** Used by the self-test to throw away its temporary database. */
    public static void dropDatabase(DbConfig config) {
        try (Connection c = DriverManager.getConnection(config.serverUrl(), config.user, config.password);
             Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + config.database + "`");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not drop database " + config.database + ": " + e.getMessage(), e);
        }
    }

    // ---------- schema ----------

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS residents ("
                    + "resident_id VARCHAR(20) NOT NULL PRIMARY KEY, "
                    + "first_name VARCHAR(80) NOT NULL, "
                    + "middle_name VARCHAR(80) NOT NULL DEFAULT '', "
                    + "last_name VARCHAR(80) NOT NULL, "
                    + "birthdate DATE NOT NULL, "
                    + "sex VARCHAR(10) NOT NULL, "
                    + "civil_status VARCHAR(15) NOT NULL, "
                    + "address VARCHAR(255) NOT NULL, "
                    + "contact_number VARCHAR(20) NOT NULL, "
                    + "purok VARCHAR(50) NOT NULL, "
                    + "approved BOOLEAN NOT NULL DEFAULT FALSE, "
                    + "INDEX idx_residents_purok (purok), "
                    + "INDEX idx_residents_name (last_name, first_name)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                    + "username VARCHAR(30) NOT NULL PRIMARY KEY, "
                    + "salt VARCHAR(64) NOT NULL, "
                    + "password_hash VARCHAR(128) NOT NULL, "
                    + "role VARCHAR(10) NOT NULL, "
                    + "status VARCHAR(10) NOT NULL, "
                    + "resident_id VARCHAR(20) NOT NULL DEFAULT '', "
                    + "valid_id_path VARCHAR(500) NOT NULL DEFAULT '', "
                    + "reject_reason VARCHAR(500) NOT NULL DEFAULT ''"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS requests ("
                    + "request_id VARCHAR(20) NOT NULL PRIMARY KEY, "
                    + "resident_id VARCHAR(20) NOT NULL, "
                    + "resident_name VARCHAR(255) NOT NULL, "
                    + "doc_type VARCHAR(30) NOT NULL, "
                    + "purpose VARCHAR(500) NOT NULL, "
                    + "date_requested DATE NOT NULL, "
                    + "status VARCHAR(10) NOT NULL, "
                    + "date_released DATE NULL, "
                    + "reject_reason VARCHAR(500) NOT NULL DEFAULT '', "
                    + "INDEX idx_requests_resident (resident_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS issuance_log ("
                    + "log_id VARCHAR(20) NOT NULL PRIMARY KEY, "
                    + "request_id VARCHAR(20) NOT NULL, "
                    + "resident_id VARCHAR(20) NOT NULL, "
                    + "resident_name VARCHAR(255) NOT NULL, "
                    + "doc_type VARCHAR(30) NOT NULL, "
                    + "date_issued DATE NOT NULL, "
                    + "issued_by VARCHAR(30) NOT NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    // ---------- load (SELECT -> model objects) ----------

    public Resident[] loadResidents() {
        DynArray<Resident> out = new DynArray<Resident>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM residents")) {
            while (rs.next()) {
                out.add(new Resident(rs.getString("resident_id"), rs.getString("first_name"),
                        rs.getString("middle_name"), rs.getString("last_name"),
                        rs.getDate("birthdate").toLocalDate(), Sex.valueOf(rs.getString("sex")),
                        CivilStatus.valueOf(rs.getString("civil_status")), rs.getString("address"),
                        rs.getString("contact_number"), rs.getString("purok"), rs.getBoolean("approved")));
            }
        } catch (SQLException e) { throw fail("load residents", e); }
        return out.toArray(new Resident[0]);
    }

    public Account[] loadAccounts() {
        DynArray<Account> out = new DynArray<Account>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM accounts")) {
            while (rs.next()) {
                out.add(new Account(rs.getString("username"), rs.getString("salt"), rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role")), AccountStatus.valueOf(rs.getString("status")),
                        rs.getString("resident_id"), rs.getString("valid_id_path"), rs.getString("reject_reason")));
            }
        } catch (SQLException e) { throw fail("load accounts", e); }
        return out.toArray(new Account[0]);
    }

    public DocumentRequest[] loadRequests() {
        DynArray<DocumentRequest> out = new DynArray<DocumentRequest>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM requests")) {
            while (rs.next()) {
                Date released = rs.getDate("date_released");
                out.add(new DocumentRequest(rs.getString("request_id"), rs.getString("resident_id"),
                        rs.getString("resident_name"), DocumentType.valueOf(rs.getString("doc_type")),
                        rs.getString("purpose"), rs.getDate("date_requested").toLocalDate(),
                        RequestStatus.valueOf(rs.getString("status")),
                        released == null ? null : released.toLocalDate(), rs.getString("reject_reason")));
            }
        } catch (SQLException e) { throw fail("load requests", e); }
        return out.toArray(new DocumentRequest[0]);
    }

    public IssuanceLogEntry[] loadIssuanceLog() {
        DynArray<IssuanceLogEntry> out = new DynArray<IssuanceLogEntry>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM issuance_log")) {
            while (rs.next()) {
                out.add(new IssuanceLogEntry(rs.getString("log_id"), rs.getString("request_id"),
                        rs.getString("resident_id"), rs.getString("resident_name"),
                        DocumentType.valueOf(rs.getString("doc_type")), rs.getDate("date_issued").toLocalDate(),
                        rs.getString("issued_by")));
            }
        } catch (SQLException e) { throw fail("load issuance log", e); }
        return out.toArray(new IssuanceLogEntry[0]);
    }

    // ---------- write-back (one transaction) ----------

    /** Upserts every row currently in the hash tables and deletes the rows removed since the last sync. */
    public void sync(Resident[] residents, Account[] accounts, DocumentRequest[] requests, IssuanceLogEntry[] log,
                     String[] deletedResidents, String[] deletedAccounts, String[] deletedRequests) {
        try {
            conn.setAutoCommit(false);
            try {
                deleteKeys("DELETE FROM residents WHERE resident_id = ?", deletedResidents);
                deleteKeys("DELETE FROM accounts WHERE username = ?", deletedAccounts);
                deleteKeys("DELETE FROM requests WHERE request_id = ?", deletedRequests);
                upsertResidents(residents);
                upsertAccounts(accounts);
                upsertRequests(requests);
                upsertLog(log);
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) { }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) { throw fail("save changes", e); }
    }

    private void deleteKeys(String sql, String[] keys) throws SQLException {
        if (keys.length == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String k : keys) { ps.setString(1, k); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    private void upsertResidents(Resident[] rows) throws SQLException {
        if (rows.length == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO residents (resident_id, first_name, middle_name, last_name, birthdate, sex, civil_status, "
                + "address, contact_number, purok, approved) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE first_name=VALUES(first_name), middle_name=VALUES(middle_name), "
                + "last_name=VALUES(last_name), birthdate=VALUES(birthdate), sex=VALUES(sex), "
                + "civil_status=VALUES(civil_status), address=VALUES(address), contact_number=VALUES(contact_number), "
                + "purok=VALUES(purok), approved=VALUES(approved)")) {
            for (Resident r : rows) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getFirstName());
                ps.setString(3, r.getMiddleName());
                ps.setString(4, r.getLastName());
                ps.setDate(5, Date.valueOf(r.getBirthdate()));
                ps.setString(6, r.getSex().name());
                ps.setString(7, r.getCivilStatus().name());
                ps.setString(8, r.getAddress());
                ps.setString(9, r.getContactNumber());
                ps.setString(10, r.getPurok());
                ps.setBoolean(11, r.isApproved());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertAccounts(Account[] rows) throws SQLException {
        if (rows.length == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO accounts (username, salt, password_hash, role, status, resident_id, valid_id_path, "
                + "reject_reason) VALUES (?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE salt=VALUES(salt), password_hash=VALUES(password_hash), role=VALUES(role), "
                + "status=VALUES(status), resident_id=VALUES(resident_id), valid_id_path=VALUES(valid_id_path), "
                + "reject_reason=VALUES(reject_reason)")) {
            for (Account a : rows) {
                ps.setString(1, a.getUsername());
                ps.setString(2, a.getSalt());
                ps.setString(3, a.getPasswordHash());
                ps.setString(4, a.getRole().name());
                ps.setString(5, a.getStatus().name());
                ps.setString(6, a.getResidentId());
                ps.setString(7, a.getValidIdPath());
                ps.setString(8, a.getRejectReason());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertRequests(DocumentRequest[] rows) throws SQLException {
        if (rows.length == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO requests (request_id, resident_id, resident_name, doc_type, purpose, date_requested, "
                + "status, date_released, reject_reason) VALUES (?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE resident_id=VALUES(resident_id), resident_name=VALUES(resident_name), "
                + "doc_type=VALUES(doc_type), purpose=VALUES(purpose), date_requested=VALUES(date_requested), "
                + "status=VALUES(status), date_released=VALUES(date_released), reject_reason=VALUES(reject_reason)")) {
            for (DocumentRequest q : rows) {
                ps.setString(1, q.getRequestId());
                ps.setString(2, q.getResidentId());
                ps.setString(3, q.getResidentName());
                ps.setString(4, q.getType().name());
                ps.setString(5, q.getPurpose());
                ps.setDate(6, Date.valueOf(q.getDateRequested()));
                ps.setString(7, q.getStatus().name());
                if (q.getDateReleased() == null) ps.setNull(8, java.sql.Types.DATE);
                else ps.setDate(8, Date.valueOf(q.getDateReleased()));
                ps.setString(9, q.getRejectReason());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertLog(IssuanceLogEntry[] rows) throws SQLException {
        if (rows.length == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO issuance_log (log_id, request_id, resident_id, resident_name, doc_type, date_issued, "
                + "issued_by) VALUES (?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE request_id=VALUES(request_id), resident_id=VALUES(resident_id), "
                + "resident_name=VALUES(resident_name), doc_type=VALUES(doc_type), date_issued=VALUES(date_issued), "
                + "issued_by=VALUES(issued_by)")) {
            for (IssuanceLogEntry e : rows) {
                ps.setString(1, e.getLogId());
                ps.setString(2, e.getRequestId());
                ps.setString(3, e.getResidentId());
                ps.setString(4, e.getResidentName());
                ps.setString(5, e.getType().name());
                ps.setDate(6, Date.valueOf(e.getDateIssued()));
                ps.setString(7, e.getIssuedBy());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static IllegalStateException fail(String what, SQLException e) {
        return new IllegalStateException("Database error while trying to " + what + ": " + e.getMessage(), e);
    }
}
