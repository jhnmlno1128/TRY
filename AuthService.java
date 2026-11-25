package brgy.service;

import brgy.model.*;
import brgy.storage.DataStore;

/** Login / logout and session handling for both ADMIN and RESIDENT users (O(1) username lookup). */
public class AuthService {

    public static final String DEFAULT_ADMIN_USER = "admin";
    public static final String DEFAULT_ADMIN_PASS = "admin123";

    private final DataStore store;
    private Account current;

    public AuthService(DataStore store) {
        this.store = store;
        seedAdmin();
    }

    private void seedAdmin() {
        for (Account a : store.accounts.values(new Account[0])) if (a.getRole() == Role.ADMIN) return;
        String salt = PasswordUtil.newSalt();
        store.putAccount(new Account(DEFAULT_ADMIN_USER, salt, PasswordUtil.hash(salt, DEFAULT_ADMIN_PASS),
                Role.ADMIN, AccountStatus.APPROVED, "", "", ""));
        store.save();
    }

    public Account login(String username, String password) {
        Account a = (username == null || password == null) ? null : store.accounts.get(DataStore.userKey(username));
        if (a == null || !PasswordUtil.matches(a.getSalt(), a.getPasswordHash(), password))
            throw new ValidationException("Invalid username or password.");
        if (a.getRole() == Role.RESIDENT) {
            if (a.getStatus() == AccountStatus.PENDING)
                throw new ValidationException("Your account is still waiting for barangay approval.");
            if (a.getStatus() == AccountStatus.REJECTED)
                throw new ValidationException("Your registration was rejected. Reason: " + a.getRejectReason());
        }
        current = a;
        return a;
    }

    /** Clears the session; the GUI should then show the login screen. */
    public void logout() { current = null; }

    public Account getCurrent() { return current; }
    public boolean isLoggedIn() { return current != null; }

    public void requireAdmin() {
        if (current == null || current.getRole() != Role.ADMIN)
            throw new ValidationException("Administrator access required. Please log in.");
    }

    public void requireResident() {
        if (current == null || current.getRole() != Role.RESIDENT)
            throw new ValidationException("Resident access required. Please log in.");
    }

    public void changePassword(String oldPass, String newPass) {
        if (current == null) throw new ValidationException("Please log in first.");
        if (!PasswordUtil.matches(current.getSalt(), current.getPasswordHash(), oldPass))
            throw new ValidationException("Current password is incorrect.");
        if (newPass == null || newPass.length() < 6)
            throw new ValidationException("New password must be at least 6 characters.");
        String salt = PasswordUtil.newSalt();
        current.setPassword(salt, PasswordUtil.hash(salt, newPass));
        store.save();
    }
}
