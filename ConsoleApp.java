package brgy;

import brgy.model.*;
import brgy.service.DuplicateService.DuplicatePair;
import brgy.service.ValidationException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/** Text-menu stand-in for the future GUI. Every action goes through the BarangaySystem facade. */
public class ConsoleApp {

    private final BarangaySystem sys;
    private final Scanner in = new Scanner(System.in);

    public ConsoleApp(BarangaySystem sys) { this.sys = sys; }

    public static void main(String[] args) {
        BarangaySystem sys;
        try {
            sys = new BarangaySystem();
        } catch (IllegalStateException e) {
            System.out.println("Could not start: " + e.getMessage());
            return;
        }
        new ConsoleApp(sys).start();
        sys.close();
    }

    // ---------- helpers ----------

    private String ask(String label) {
        System.out.print(label + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "";
    }

    private int menuChoice() {
        String s = ask("Choose");
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    private LocalDate askDate(String label) {
        while (true) {
            try { return LocalDate.parse(ask(label + " (yyyy-mm-dd)")); }
            catch (DateTimeParseException e) { System.out.println("  Invalid date format."); }
        }
    }

    private Sex askSex() {
        while (true) {
            String s = ask("Sex (M/F)").toUpperCase();
            if (s.startsWith("M")) return Sex.MALE;
            if (s.startsWith("F")) return Sex.FEMALE;
        }
    }

    private CivilStatus askCivil() {
        CivilStatus[] all = CivilStatus.values();
        for (int i = 0; i < all.length; i++) System.out.println("  " + (i + 1) + ". " + all[i]);
        while (true) {
            try {
                int n = Integer.parseInt(ask("Civil status number"));
                if (n >= 1 && n <= all.length) return all[n - 1];
            } catch (NumberFormatException ignored) { }
        }
    }

    private void printResidents(Resident[] list) {
        System.out.printf("%-15s %-28s %4s %-7s %-10s %-14s%n", "Resident ID", "Name", "Age", "Sex", "Purok", "Class");
        for (Resident r : list)
            System.out.printf("%-15s %-28s %4d %-7s %-10s %-14s%n", r.getId(), r.getFullName(), r.getAge(),
                    r.getSex(), r.getPurok(), r.getClassification());
        System.out.println("(" + list.length + " record/s)");
    }

    private void printRequests(DocumentRequest[] list) {
        for (DocumentRequest q : list) {
            System.out.println("  " + q.getRequestId() + " | " + q.getResidentName() + " | " + q.getType()
                    + " | " + q.getPurpose() + " | requested " + q.getDateRequested() + " | " + q.getStatus()
                    + (q.getDateReleased() != null ? " | release " + q.getDateReleased() : "")
                    + (q.getStatus() == RequestStatus.REJECTED ? " | reason: " + q.getRejectReason() : ""));
        }
        if (list.length == 0) System.out.println("  (none)");
    }

    // ---------- main loop ----------

    public void start() {
        System.out.println("=== BARANGAY RESIDENT INFORMATION SYSTEM ===");
        while (true) {
            System.out.println("\n1. Login\n2. Resident Sign Up\n0. Exit");
            int c = menuChoice();
            try {
                if (c == 0) { System.out.println("Goodbye."); return; }
                if (c == 1) {
                    Account a = sys.login(ask("Username"), ask("Password"));
                    if (a.getRole() == Role.ADMIN) adminMenu(); else residentMenu();
                    sys.logout();
                    System.out.println("Logged out. Returning to the login screen.");
                } else if (c == 2) signUp();
            } catch (ValidationException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private void signUp() {
        System.out.println("-- Account Registration --");
        String first = ask("First name"), middle = ask("Middle name (optional)"), last = ask("Last name");
        LocalDate birth = askDate("Birthdate");
        Sex sex = askSex();
        CivilStatus civil = askCivil();
        String address = ask("Address"), contact = ask("Contact number"), purok = ask("Purok/Zone");
        String user = ask("Username"), pass = ask("Password (min 6)");
        Path id = Paths.get(ask("Path to valid ID image/file"));
        Resident r = sys.registerResident(first, middle, last, birth, sex, civil, address, contact, purok, user, pass, id);
        System.out.println("Registered! Your Resident ID is " + r.getId() + ". Please wait for the admin to approve your account.");
    }

    // ---------- ADMIN ----------

    private void adminMenu() {
        while (true) {
            System.out.println("\n--- ADMIN DASHBOARD ---");
            System.out.println("Residents: " + sys.reportTotalResidents() + " | Pending sign-ups: "
                    + sys.getPendingRegistrations().length + " | Pending requests: " + sys.getPendingRequests().length);
            System.out.println("1. Records Management\n2. Search & Sorting\n3. Duplicate Detection\n"
                    + "4. Document Requests & Registrations\n5. Reports\n0. Logout");
            int c = menuChoice();
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: recordsMenu(); break;
                    case 2: searchMenu(); break;
                    case 3: duplicateMenu(); break;
                    case 4: requestMenu(); break;
                    case 5: reportMenu(); break;
                    default: System.out.println("  Invalid choice.");
                }
            } catch (ValidationException e) { System.out.println("  ! " + e.getMessage()); }
        }
    }

    private void recordsMenu() {
        System.out.println("\n[Records] 1.List all 2.Add 3.View 4.Edit 5.Delete 0.Back");
        switch (menuChoice()) {
            case 1: printResidents(sys.sortByName(sys.getAllResidents())); break;
            case 2: {
                String first = ask("First name"), middle = ask("Middle name (optional)"), last = ask("Last name");
                LocalDate birth = askDate("Birthdate");
                Sex sex = askSex();
                CivilStatus civil = askCivil();
                Resident r = sys.addResident(first, middle, last, birth, sex, civil, ask("Address"),
                        ask("Contact number"), ask("Purok/Zone"));
                System.out.println("Added. Resident ID: " + r.getId());
                DuplicatePair[] d = sys.findDuplicatesOf(r.getId());
                if (d.length > 0) { System.out.println("  WARNING - possible duplicates:"); for (DuplicatePair p : d) System.out.println("  " + p); }
                break;
            }
            case 3: {
                Resident r = sys.getResident(ask("Resident ID"));
                System.out.println(r.getId() + "\nName: " + r.getFullName() + "\nBirthdate: " + r.getBirthdate()
                        + " (age " + r.getAge() + ")\nSex: " + r.getSex() + "\nCivil status: " + r.getCivilStatus()
                        + "\nAddress: " + r.getAddress() + "\nContact: " + r.getContactNumber()
                        + "\nPurok: " + r.getPurok() + "\nClassification: " + r.getClassification());
                break;
            }
            case 4: {
                Resident r = sys.getResident(ask("Resident ID to edit"));
                System.out.println("Re-enter all fields (current: " + r + ")");
                String first = ask("First name"), middle = ask("Middle name (optional)"), last = ask("Last name");
                LocalDate birth = askDate("Birthdate");
                Sex sex = askSex();
                CivilStatus civil = askCivil();
                sys.updateResident(r.getId(), first, middle, last, birth, sex, civil, ask("Address"),
                        ask("Contact number"), ask("Purok/Zone"));
                System.out.println("Updated.");
                break;
            }
            case 5: {
                Resident r = sys.getResident(ask("Resident ID to delete"));
                if (ask("Delete " + r.getFullName() + " and their account/requests? (yes/no)").equalsIgnoreCase("yes")) {
                    sys.deleteResident(r.getId());
                    System.out.println("Deleted.");
                }
                break;
            }
            default: break;
        }
    }

    private void searchMenu() {
        System.out.println("\n[Search & Sort] 1.By name 2.By Resident ID 3.By purok 4.Sort by age 5.Sort by sex\n"
                + "                6.Sort by age & sex 7.Sort by SK/Senior classification 0.Back");
        switch (menuChoice()) {
            case 1: printResidents(sys.searchByName(ask("Name"))); break;
            case 2: {
                Resident r = sys.searchByResidentId(ask("Resident ID"));
                Resident[] one = (r == null) ? new Resident[0] : new Resident[]{r};
                printResidents(one);
                break;
            }
            case 3: printResidents(sys.searchByPurok(ask("Purok"))); break;
            case 4: printResidents(sys.sortByAge(sys.getAllResidents(), !ask("Oldest first? (y/n)").equalsIgnoreCase("y"))); break;
            case 5: printResidents(sys.sortBySex(sys.getAllResidents())); break;
            case 6: printResidents(sys.sortByAgeAndSex(sys.getAllResidents())); break;
            case 7: printResidents(sys.sortByClassification(sys.getAllResidents())); break;
            default: break;
        }
    }

    private void duplicateMenu() {
        DuplicatePair[] pairs = sys.findDuplicates();
        if (pairs.length == 0) { System.out.println("No duplicates detected."); return; }
        System.out.println("Flagged for review:");
        for (DuplicatePair p : pairs) System.out.println("  " + p);
        if (ask("Remove one of them? (yes/no)").equalsIgnoreCase("yes")) {
            String keep = ask("Resident ID to KEEP"), remove = ask("Resident ID to REMOVE");
            sys.removeDuplicate(keep, remove);
            System.out.println("Duplicate removed.");
        }
    }

    private void requestMenu() {
        System.out.println("\n[Requests] 1.Pending sign-ups 2.Pending document requests 3.All requests 4.Print document 0.Back");
        switch (menuChoice()) {
            case 1: {
                Account[] pend = sys.getPendingRegistrations();
                if (pend.length == 0) { System.out.println("  (none)"); return; }
                for (Account a : pend) {
                    Resident r = sys.getResidentOfAccount(a);
                    System.out.println("  " + a.getUsername() + " | " + (r == null ? "?" : r.getFullName() + ", " + r.getBirthdate()
                            + ", " + r.getAddress() + ", " + r.getPurok()) + " | ID file: " + a.getValidIdPath());
                }
                String u = ask("Username to process (blank = back)");
                if (u.isEmpty()) return;
                if (ask("Approve? (y = approve, n = reject)").equalsIgnoreCase("y")) {
                    sys.approveRegistration(u);
                    System.out.println("Account approved.");
                } else {
                    sys.rejectRegistration(u, ask("Reason"));
                    System.out.println("Registration rejected.");
                }
                break;
            }
            case 2: {
                DocumentRequest[] pend = sys.getPendingRequests();
                printRequests(pend);
                if (pend.length == 0) return;
                String id = ask("Request ID to process (blank = back)");
                if (id.isEmpty()) return;
                if (ask("Approve? (y = approve, n = reject)").equalsIgnoreCase("y")) {
                    String rel = ask("Release date yyyy-mm-dd (blank = today)");
                    sys.approveRequest(id, rel.isEmpty() ? null : LocalDate.parse(rel));
                    System.out.println("Approved. Document: " + sys.getDocumentFile(id).toAbsolutePath());
                } else {
                    sys.rejectRequest(id, ask("Reason"));
                    System.out.println("Rejected.");
                }
                break;
            }
            case 3: printRequests(sys.getAllRequests()); break;
            case 4: System.out.println("Open in a browser and print: " + sys.getDocumentFile(ask("Request ID")).toAbsolutePath()); break;
            default: break;
        }
    }

    private void reportMenu() {
        System.out.println("\n[Reports] 1.Total residents 2.Population by age & sex 3.SK members 4.Senior citizens 5.Issuance log 6.Everything 0.Back");
        switch (menuChoice()) {
            case 1: System.out.println(sys.textTotalResidents()); break;
            case 2: System.out.println(sys.textPopulationByAgeAndSex()); break;
            case 3: System.out.println(sys.textSkMembers()); break;
            case 4: System.out.println(sys.textSeniorCitizens()); break;
            case 5: System.out.println(sys.textIssuanceLog()); break;
            case 6: System.out.println(sys.textFullReport()); break;
            default: break;
        }
    }

    // ---------- RESIDENT ----------

    private void residentMenu() {
        while (true) {
            Resident me = sys.getMyProfile();
            System.out.println("\n--- WELCOME, " + me.getFullName() + " (" + me.getId() + ") ---");
            System.out.println("1. My Profile\n2. Request a Document\n3. Request Status\n0. Logout");
            int c = menuChoice();
            if (c == 0) return;
            try {
                switch (c) {
                    case 1: {
                        System.out.println("Name: " + me.getFullName() + "  (locked)\nBirthdate: " + me.getBirthdate()
                                + "  (locked)\nSex: " + me.getSex() + "\nCivil status: " + me.getCivilStatus() + "  (locked)\n"
                                + "Address: " + me.getAddress() + "\nContact: " + me.getContactNumber()
                                + "\nPurok/Zone: " + me.getPurok());
                        if (ask("Update contact number / address? (y/n)").equalsIgnoreCase("y")) {
                            sys.updateMyContactInfo(ask("New contact number"), ask("New address"));
                            System.out.println("Profile updated.");
                        }
                        break;
                    }
                    case 2: {
                        DocumentType[] types = DocumentType.values();
                        for (int i = 0; i < types.length; i++) System.out.println("  " + (i + 1) + ". " + types[i]);
                        int n = Integer.parseInt(ask("Document number"));
                        if (n < 1 || n > types.length) { System.out.println("  Invalid choice."); break; }
                        DocumentRequest q = sys.submitRequest(types[n - 1], ask("Purpose of request"));
                        System.out.println("Submitted. Request No: " + q.getRequestId() + " (Pending). Fees are paid at the barangay hall.");
                        break;
                    }
                    case 3:
                        System.out.println("PENDING:");  printRequests(sys.getMyPendingRequests());
                        System.out.println("APPROVED:"); printRequests(sys.getMyApprovedRequests());
                        System.out.println("REJECTED:"); printRequests(sys.getMyRejectedRequests());
                        break;
                    default: System.out.println("  Invalid choice.");
                }
            } catch (ValidationException e) { System.out.println("  ! " + e.getMessage()); }
            catch (NumberFormatException e) { System.out.println("  ! Please enter a number."); }
        }
    }
}
