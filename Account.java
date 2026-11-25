package brgy.model;

public class Account {
    private String username;
    private String salt;
    private String passwordHash;
    private Role role;
    private AccountStatus status;
    /** Links a RESIDENT account to its Resident record (empty for ADMIN). */
    private String residentId;
    private String validIdPath;
    private String rejectReason;

    public Account() {}

    public Account(String username, String salt, String passwordHash, Role role, AccountStatus status,
                   String residentId, String validIdPath, String rejectReason) {
        this.username = username;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.residentId = residentId == null ? "" : residentId;
        this.validIdPath = validIdPath == null ? "" : validIdPath;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    public String getUsername() { return username; }
    public String getSalt() { return salt; }
    public String getPasswordHash() { return passwordHash; }
    public void setPassword(String salt, String hash) { this.salt = salt; this.passwordHash = hash; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId == null ? "" : residentId; }
    public String getValidIdPath() { return validIdPath; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String r) { this.rejectReason = r == null ? "" : r; }
}
