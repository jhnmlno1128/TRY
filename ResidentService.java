package brgy.service;

import brgy.ds.DynArray;
import brgy.model.*;
import brgy.storage.DataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.regex.Pattern;

/** Records management (CRUD), resident sign-up and registration approval - all on hash tables. */
public class ResidentService {

    private static final Pattern CONTACT = Pattern.compile("^\\+?\\d{10,13}$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{4,20}$");

    private final DataStore store;

    public ResidentService(DataStore store) { this.store = store; }

    // ---------- validation ----------

    /** Keeps free text within the database column size. */
    private static String limit(String s) { return s.length() > 500 ? s.substring(0, 500) : s; }

    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }

    private void validate(String first, String middle, String last, LocalDate birth, Sex sex, CivilStatus civil,
                          String address, String contact, String purok) {
        if (clean(first).isEmpty()) throw new ValidationException("First name is required.");
        if (clean(last).isEmpty()) throw new ValidationException("Last name is required.");
        if (clean(first).length() > 80 || clean(last).length() > 80 || clean(middle).length() > 80)
            throw new ValidationException("Names can be at most 80 characters.");
        if (clean(address).length() > 255) throw new ValidationException("Address can be at most 255 characters.");
        if (clean(purok).length() > 50) throw new ValidationException("Purok/Zone can be at most 50 characters.");
        if (birth == null) throw new ValidationException("Birthdate is required.");
        if (birth.isAfter(LocalDate.now())) throw new ValidationException("Birthdate cannot be in the future.");
        if (birth.isBefore(LocalDate.now().minusYears(130))) throw new ValidationException("Birthdate is not realistic.");
        if (sex == null) throw new ValidationException("Sex is required.");
        if (civil == null) throw new ValidationException("Civil status is required.");
        if (clean(address).isEmpty()) throw new ValidationException("Address is required.");
        validateContact(contact);
        if (clean(purok).isEmpty()) throw new ValidationException("Purok/Zone is required.");
    }

    public static void validateContact(String contact) {
        if (clean(contact).isEmpty()) throw new ValidationException("Contact number is required.");
        if (!CONTACT.matcher(clean(contact).replace("-", "").replace(" ", "")).matches())
            throw new ValidationException("Contact number must be 10-13 digits (e.g. 09171234567).");
    }

    private static String normContact(String c) { return clean(c).replace("-", "").replace(" ", ""); }

    // ---------- CRUD ----------

    /** Walk-in record encoded by staff (already approved, no login account). */
    public Resident addResident(String first, String middle, String last, LocalDate birth, Sex sex,
                                CivilStatus civil, String address, String contact, String purok) {
        validate(first, middle, last, birth, sex, civil, address, contact, purok);
        Resident r = new Resident(store.nextResidentId(), clean(first), clean(middle), clean(last), birth, sex, civil,
                clean(address), normContact(contact), clean(purok), true);
        store.putResident(r);
        store.save();
        return r;
    }

    public Resident updateResident(String id, String first, String middle, String last, LocalDate birth, Sex sex,
                                   CivilStatus civil, String address, String contact, String purok) {
        Resident r = require(id);
        validate(first, middle, last, birth, sex, civil, address, contact, purok);
        r.setFirstName(clean(first));
        r.setMiddleName(clean(middle));
        r.setLastName(clean(last));
        r.setBirthdate(birth);
        r.setSex(sex);
        r.setCivilStatus(civil);
        r.setAddress(clean(address));
        r.setContactNumber(normContact(contact));
        r.setPurok(clean(purok));
        store.save();
        return r;
    }

    /** Deletes the resident plus their login account and document requests. */
    public void deleteResident(String id) {
        Resident r = require(id);
        store.removeResident(r);
        Account a = getAccountOf(r.getId());
        if (a != null) store.removeAccount(a);
        for (DocumentRequest q : store.requests.values(new DocumentRequest[0]))
            if (q.getResidentId().equalsIgnoreCase(r.getId())) store.removeRequest(q);
        store.save();
    }

    /** O(1) hash lookup. */
    public Resident getById(String id) {
        return id == null ? null : store.residents.get(DataStore.key(id));
    }

    public Resident require(String id) {
        Resident r = getById(id);
        if (r == null) throw new ValidationException("Resident '" + id + "' was not found.");
        return r;
    }

    /** Every verified resident, in ID order (excludes sign-ups still waiting for approval). */
    public Resident[] activeResidents() {
        DynArray<Resident> out = new DynArray<Resident>();
        for (Resident r : store.residentsSorted()) if (r.isApproved()) out.add(r);
        return out.toArray(new Resident[0]);
    }

    /** O(1) hash lookup of the login account that belongs to a resident (null for walk-ins). */
    public Account getAccountOf(String residentId) {
        return store.accountByResidentId.get(DataStore.key(residentId));
    }

    // ---------- resident self-service ----------

    /** Resident edits only the fields they are allowed to change. */
    public Resident updateOwnContactInfo(String residentId, String contact, String address) {
        Resident r = require(residentId);
        validateContact(contact);
        if (clean(address).isEmpty()) throw new ValidationException("Address is required.");
        r.setContactNumber(normContact(contact));
        r.setAddress(clean(address));
        store.save();
        return r;
    }

    // ---------- sign up / registration approval ----------

    public Resident registerResident(String first, String middle, String last, LocalDate birth, Sex sex,
                                     CivilStatus civil, String address, String contact, String purok,
                                     String username, String password, Path validIdFile) {
        validate(first, middle, last, birth, sex, civil, address, contact, purok);
        String user = clean(username);
        if (!USERNAME.matcher(user).matches())
            throw new ValidationException("Username must be 4-20 characters (letters, numbers, _ or .).");
        if (store.accounts.containsKey(DataStore.userKey(user)))
            throw new ValidationException("That username is already taken.");
        if (password == null || password.length() < 6)
            throw new ValidationException("Password must be at least 6 characters.");
        if (validIdFile == null || !Files.isRegularFile(validIdFile))
            throw new ValidationException("Please upload a valid ID or supporting document.");

        String copied;
        try {
            Path target = store.getUploadsDir().resolve(user + "_" + validIdFile.getFileName().toString());
            Files.copy(validIdFile, target, StandardCopyOption.REPLACE_EXISTING);
            copied = target.toString();
        } catch (IOException e) {
            throw new ValidationException("Could not save the uploaded ID: " + e.getMessage());
        }

        Resident r = new Resident(store.nextResidentId(), clean(first), clean(middle), clean(last), birth, sex, civil,
                clean(address), normContact(contact), clean(purok), false);
        store.putResident(r);
        String salt = PasswordUtil.newSalt();
        store.putAccount(new Account(user, salt, PasswordUtil.hash(salt, password), Role.RESIDENT,
                AccountStatus.PENDING, r.getId(), copied, ""));
        store.save();
        return r;
    }

    /** Sign-ups waiting for the admin, oldest first. */
    public Account[] pendingRegistrations() { return store.pendingRegistrationsSorted(); }

    public void approveRegistration(String username) {
        Account a = pendingAccount(username);
        Resident r = require(a.getResidentId());
        r.setApproved(true);
        a.setStatus(AccountStatus.APPROVED);
        a.setRejectReason("");
        store.save();
    }

    /** Rejects the sign-up; the unverified resident record is discarded, the account keeps the reason. */
    public void rejectRegistration(String username, String reason) {
        Account a = pendingAccount(username);
        Resident r = getById(a.getResidentId());
        if (r != null) store.removeResident(r);
        a.setStatus(AccountStatus.REJECTED);
        a.setRejectReason(reason == null || reason.trim().isEmpty() ? "No reason provided." : limit(reason.trim()));
        a.setResidentId("");
        store.save();
    }

    private Account pendingAccount(String username) {
        Account a = username == null ? null : store.accounts.get(DataStore.userKey(username));
        if (a == null || a.getRole() != Role.RESIDENT) throw new ValidationException("Registration not found.");
        if (a.getStatus() != AccountStatus.PENDING)
            throw new ValidationException("This registration was already " + a.getStatus().name().toLowerCase() + ".");
        return a;
    }
}
