package brgy;

import brgy.model.*;
import brgy.service.*;
import brgy.service.DuplicateService.DuplicatePair;
import brgy.service.ReportService.AgeSexRow;
import brgy.storage.DataStore;
import brgy.storage.Database;
import brgy.storage.DbConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * THE ONLY CLASS A GUI NEEDS. Create one instance, call these methods from your buttons/screens.
 * Every method that breaks a rule throws {@link ValidationException} - show getMessage() to the user.
 * Admin-only and resident-only methods check the logged-in user themselves.
 */
public class BarangaySystem {

    private final DataStore store;
    private final AuthService auth;
    private final ResidentService residents;
    private final SearchSortService searchSort;
    private final DuplicateService duplicates;
    private final DocumentGenerator generator;
    private final DocumentRequestService requests;
    private final ReportService reports;

    /**
     * @param filesDir folder for uploaded IDs and printable certificates
     * @param config   MySQL connection settings (see db.properties)
     * @throws IllegalStateException with a readable message when MySQL cannot be reached
     */
    public BarangaySystem(Path filesDir, DbConfig config) {
        store = new DataStore(filesDir, new Database(config));
        auth = new AuthService(store);
        residents = new ResidentService(store);
        searchSort = new SearchSortService(store, residents);
        duplicates = new DuplicateService(store, residents);
        generator = new DocumentGenerator();
        requests = new DocumentRequestService(store, residents, generator);
        reports = new ReportService(residents, searchSort, requests);
    }

    /** Files in .\data, MySQL settings from .\db.properties (XAMPP defaults if the file is missing). */
    public BarangaySystem() { this(Paths.get("data"), DbConfig.load(Paths.get("db.properties"))); }

    /** Closes the MySQL connection (call when the program exits). */
    public void close() { store.getDatabase().close(); }

    // =====================================================================
    // LOGIN / LOGOUT (both roles)
    // =====================================================================

    /** Returns the account; check getRole() to open the Admin or Resident dashboard. */
    public Account login(String username, String password) { return auth.login(username, password); }

    /** After this, show the login screen. */
    public void logout() { auth.logout(); }

    public Account getCurrentUser() { return auth.getCurrent(); }
    public boolean isLoggedIn() { return auth.isLoggedIn(); }
    public void changePassword(String oldPassword, String newPassword) { auth.changePassword(oldPassword, newPassword); }

    /** Sets the names printed on certificates. */
    public void setBarangayInfo(String barangay, String municipality, String province, String captain) {
        generator.setBarangayInfo(barangay, municipality, province, captain);
    }

    // =====================================================================
    // RESIDENT SIGN UP (no login needed)
    // =====================================================================

    /** Creates the resident record (with auto-generated ID) and a PENDING account awaiting admin approval. */
    public Resident registerResident(String firstName, String middleName, String lastName, LocalDate birthdate,
                                     Sex sex, CivilStatus civilStatus, String address, String contactNumber,
                                     String purok, String username, String password, Path validIdFile) {
        return residents.registerResident(firstName, middleName, lastName, birthdate, sex, civilStatus, address,
                contactNumber, purok, username, password, validIdFile);
    }

    // =====================================================================
    // ADMIN: RECORDS MANAGEMENT
    // =====================================================================

    public Resident addResident(String firstName, String middleName, String lastName, LocalDate birthdate, Sex sex,
                                CivilStatus civilStatus, String address, String contactNumber, String purok) {
        auth.requireAdmin();
        return residents.addResident(firstName, middleName, lastName, birthdate, sex, civilStatus, address,
                contactNumber, purok);
    }

    public Resident updateResident(String residentId, String firstName, String middleName, String lastName,
                                   LocalDate birthdate, Sex sex, CivilStatus civilStatus, String address,
                                   String contactNumber, String purok) {
        auth.requireAdmin();
        return residents.updateResident(residentId, firstName, middleName, lastName, birthdate, sex, civilStatus,
                address, contactNumber, purok);
    }

    public void deleteResident(String residentId) { auth.requireAdmin(); residents.deleteResident(residentId); }

    public Resident getResident(String residentId) { auth.requireAdmin(); return residents.require(residentId); }

    /** All verified residents. */
    public Resident[] getAllResidents() { auth.requireAdmin(); return residents.activeResidents(); }

    // =====================================================================
    // ADMIN: SEARCH / FILTER / SORT
    // =====================================================================

    public Resident[] searchByName(String query) { auth.requireAdmin(); return searchSort.searchByName(query); }

    /** Returns null when no such resident. */
    public Resident searchByResidentId(String id) { auth.requireAdmin(); return searchSort.searchById(id); }

    public Resident[] searchByPurok(String purok) { auth.requireAdmin(); return searchSort.searchByPurok(purok); }

    public String[] listPuroks() { auth.requireAdmin(); return searchSort.listPuroks(); }

    /** Sorts the given list (pass the result of any search / getAllResidents). */
    public Resident[] sortByAge(Resident[] list, boolean youngestFirst) {
        auth.requireAdmin(); return searchSort.sortByAge(list, youngestFirst);
    }
    public Resident[] sortBySex(Resident[] list) { auth.requireAdmin(); return searchSort.sortBySex(list); }
    public Resident[] sortByAgeAndSex(Resident[] list) { auth.requireAdmin(); return searchSort.sortByAgeAndSex(list); }
    public Resident[] sortByName(Resident[] list) { auth.requireAdmin(); return searchSort.sortByName(list); }
    /** Senior Citizens first, then SK members, then the rest. */
    public Resident[] sortByClassification(Resident[] list) {
        auth.requireAdmin(); return searchSort.sortByClassification(list);
    }

    // =====================================================================
    // ADMIN: DUPLICATE DETECTION
    // =====================================================================

    public DuplicatePair[] findDuplicates() { auth.requireAdmin(); return duplicates.findDuplicates(); }
    public DuplicatePair[] findDuplicatesOf(String residentId) { auth.requireAdmin(); return duplicates.findDuplicatesOf(residentId); }
    /** Staff confirmed these are the same person: keeps one record, removes the other. */
    public void removeDuplicate(String keepResidentId, String removeResidentId) {
        auth.requireAdmin(); duplicates.removeDuplicate(keepResidentId, removeResidentId);
    }

    // =====================================================================
    // ADMIN: ACCOUNT REGISTRATION APPROVAL + DOCUMENT REQUESTS
    // =====================================================================

    public Account[] getPendingRegistrations() { auth.requireAdmin(); return residents.pendingRegistrations(); }
    /** The resident record belonging to a pending account (to show their details next to the ID upload). */
    public Resident getResidentOfAccount(Account account) { auth.requireAdmin(); return residents.getById(account.getResidentId()); }
    public void approveRegistration(String username) { auth.requireAdmin(); residents.approveRegistration(username); }
    public void rejectRegistration(String username, String reason) { auth.requireAdmin(); residents.rejectRegistration(username, reason); }

    public DocumentRequest[] getPendingRequests() { auth.requireAdmin(); return requests.pending(); }
    public DocumentRequest[] getAllRequests() { auth.requireAdmin(); return requests.all(); }

    /** releaseDate may be null (= today). Generates the printable document and logs the issuance. */
    public DocumentRequest approveRequest(String requestId, LocalDate releaseDate) {
        auth.requireAdmin();
        return requests.approve(requestId, releaseDate, auth.getCurrent().getUsername());
    }

    public DocumentRequest rejectRequest(String requestId, String reason) { auth.requireAdmin(); return requests.reject(requestId, reason); }

    /** Printable HTML file of an approved request - open in a browser / print. */
    public Path getDocumentFile(String requestId) {
        auth.requireAdmin();
        return requests.getDocumentFile(requestId, auth.getCurrent().getUsername());
    }

    // =====================================================================
    // ADMIN: REPORTS
    // =====================================================================

    public int reportTotalResidents() { auth.requireAdmin(); return reports.totalRegisteredResidents(); }
    public AgeSexRow[] reportPopulationByAgeAndSex() { auth.requireAdmin(); return reports.populationByAgeAndSex(); }
    public Resident[] reportSkMembers() { auth.requireAdmin(); return reports.skMembers(); }
    public Resident[] reportSeniorCitizens() { auth.requireAdmin(); return reports.seniorCitizens(); }
    public IssuanceLogEntry[] reportIssuanceLog() { auth.requireAdmin(); return reports.documentIssuanceLog(); }
    /** Formatted text versions for printing. */
    public String textTotalResidents() { auth.requireAdmin(); return reports.textTotalResidents(); }
    public String textPopulationByAgeAndSex() { auth.requireAdmin(); return reports.textPopulationByAgeAndSex(); }
    public String textSkMembers() { auth.requireAdmin(); return reports.textSkMembers(); }
    public String textSeniorCitizens() { auth.requireAdmin(); return reports.textSeniorCitizens(); }
    public String textIssuanceLog() { auth.requireAdmin(); return reports.textIssuanceLog(); }
    public String textFullReport() { auth.requireAdmin(); return reports.textFullReport(); }

    // =====================================================================
    // RESIDENT: MY PROFILE / DOCUMENT REQUEST / REQUEST STATUS
    // =====================================================================

    public Resident getMyProfile() {
        auth.requireResident();
        return residents.require(auth.getCurrent().getResidentId());
    }

    /** Residents may only change contact number and address; name, birthdate, civil status are locked. */
    public Resident updateMyContactInfo(String contactNumber, String address) {
        auth.requireResident();
        return residents.updateOwnContactInfo(auth.getCurrent().getResidentId(), contactNumber, address);
    }

    public DocumentRequest submitRequest(DocumentType type, String purpose) {
        auth.requireResident();
        return requests.submit(auth.getCurrent().getResidentId(), type, purpose);
    }

    public DocumentRequest[] getMyPendingRequests() {
        auth.requireResident();
        return requests.byResident(auth.getCurrent().getResidentId(), RequestStatus.PENDING);
    }

    /** Each has getDateReleased(). */
    public DocumentRequest[] getMyApprovedRequests() {
        auth.requireResident();
        return requests.byResident(auth.getCurrent().getResidentId(), RequestStatus.APPROVED);
    }

    /** Each has getRejectReason(). */
    public DocumentRequest[] getMyRejectedRequests() {
        auth.requireResident();
        return requests.byResident(auth.getCurrent().getResidentId(), RequestStatus.REJECTED);
    }
}
