package brgy.service;

import brgy.ds.DynArray;
import brgy.model.IssuanceLogEntry;
import brgy.model.Resident;
import brgy.model.Sex;

import java.time.LocalDate;

/** Report data (for GUI tables) plus ready-made text versions (for printing / console). */
public class ReportService {

    /** One row of the age-and-sex population table. */
    public static class AgeSexRow {
        public final String bracket;
        public int male;
        public int female;
        AgeSexRow(String bracket) { this.bracket = bracket; }
        public int total() { return male + female; }
    }

    private static final String[] BRACKETS = {"0-14", "15-24", "25-39", "40-59", "60 and above"};
    private static final int[] UPPER = {14, 24, 39, 59, Integer.MAX_VALUE};

    private final ResidentService residents;
    private final SearchSortService sorter;
    private final DocumentRequestService requests;

    public ReportService(ResidentService residents, SearchSortService sorter, DocumentRequestService requests) {
        this.residents = residents;
        this.sorter = sorter;
        this.requests = requests;
    }

    public int totalRegisteredResidents() { return residents.activeResidents().length; }

    public AgeSexRow[] populationByAgeAndSex() {
        AgeSexRow[] rows = new AgeSexRow[BRACKETS.length];
        for (int i = 0; i < rows.length; i++) rows[i] = new AgeSexRow(BRACKETS[i]);
        for (Resident r : residents.activeResidents()) {
            int age = r.getAge();
            for (int i = 0; i < rows.length; i++) {
                if (age <= UPPER[i]) {
                    if (r.getSex() == Sex.MALE) rows[i].male++; else rows[i].female++;
                    break;
                }
            }
        }
        return rows;
    }

    public int countBySex(Sex sex) {
        int n = 0;
        for (Resident r : residents.activeResidents()) if (r.getSex() == sex) n++;
        return n;
    }

    public Resident[] skMembers() {
        DynArray<Resident> out = new DynArray<Resident>();
        for (Resident r : residents.activeResidents()) if (r.isSKMember()) out.add(r);
        return sorter.sortByName(out.toArray(new Resident[0]));
    }

    public Resident[] seniorCitizens() {
        DynArray<Resident> out = new DynArray<Resident>();
        for (Resident r : residents.activeResidents()) if (r.isSeniorCitizen()) out.add(r);
        return sorter.sortByName(out.toArray(new Resident[0]));
    }

    public IssuanceLogEntry[] documentIssuanceLog() { return requests.issuanceLog(); }

    // ---------- text versions ----------

    private static String line() { return "------------------------------------------------------------------------\n"; }

    public String textTotalResidents() {
        return "TOTAL REGISTERED RESIDENTS: " + totalRegisteredResidents() + "\n"
                + "  Male: " + countBySex(Sex.MALE) + "   Female: " + countBySex(Sex.FEMALE) + "\n";
    }

    public String textPopulationByAgeAndSex() {
        StringBuilder sb = new StringBuilder("POPULATION COUNT BY AGE AND SEX\n").append(line());
        sb.append(String.format("%-16s %8s %8s %8s%n", "Age Bracket", "Male", "Female", "Total"));
        int m = 0, f = 0;
        for (AgeSexRow r : populationByAgeAndSex()) {
            sb.append(String.format("%-16s %8d %8d %8d%n", r.bracket, r.male, r.female, r.total()));
            m += r.male; f += r.female;
        }
        sb.append(line()).append(String.format("%-16s %8d %8d %8d%n", "TOTAL", m, f, m + f));
        return sb.toString();
    }

    public String textResidentList(String title, Resident[] list) {
        StringBuilder sb = new StringBuilder(title).append(" (").append(list.length).append(")\n").append(line());
        sb.append(String.format("%-15s %-28s %4s %-7s %-10s%n", "Resident ID", "Name", "Age", "Sex", "Purok"));
        for (Resident r : list)
            sb.append(String.format("%-15s %-28s %4d %-7s %-10s%n", r.getId(), r.getFullName(), r.getAge(),
                    r.getSex().getLabel(), r.getPurok()));
        if (list.length == 0) sb.append("(none)\n");
        return sb.toString();
    }

    public String textSkMembers() { return textResidentList("LIST OF SK MEMBERS", skMembers()); }
    public String textSeniorCitizens() { return textResidentList("LIST OF SENIOR CITIZENS", seniorCitizens()); }

    public String textIssuanceLog() {
        IssuanceLogEntry[] log = documentIssuanceLog();
        StringBuilder sb = new StringBuilder("DOCUMENT ISSUANCE LOG (").append(log.length).append(")\n").append(line());
        sb.append(String.format("%-10s %-11s %-24s %-24s %-10s%n", "Log No", "Date", "Resident", "Document", "By"));
        for (IssuanceLogEntry e : log)
            sb.append(String.format("%-10s %-11s %-24s %-24s %-10s%n", e.getLogId(), e.getDateIssued(),
                    e.getResidentName(), e.getType().getLabel(), e.getIssuedBy()));
        if (log.length == 0) sb.append("(no documents issued yet)\n");
        return sb.toString();
    }

    public String textFullReport() {
        return "BARANGAY RESIDENT INFORMATION SYSTEM - REPORT as of " + LocalDate.now() + "\n\n"
                + textTotalResidents() + "\n" + textPopulationByAgeAndSex() + "\n"
                + textSkMembers() + "\n" + textSeniorCitizens() + "\n" + textIssuanceLog();
    }
}
