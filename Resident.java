package brgy.model;

import java.time.LocalDate;
import java.time.Period;

public class Resident {

    public static final int SK_MIN_AGE = 15;
    public static final int SK_MAX_AGE = 30;
    public static final int SENIOR_MIN_AGE = 60;

    private String id;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate birthdate;
    private Sex sex;
    private CivilStatus civilStatus;
    private String address;
    private String contactNumber;
    private String purok;
    /** false while a self-registered resident is waiting for admin approval. Walk-in records are true. */
    private boolean approved;

    public Resident() {}

    public Resident(String id, String firstName, String middleName, String lastName, LocalDate birthdate,
                    Sex sex, CivilStatus civilStatus, String address, String contactNumber, String purok,
                    boolean approved) {
        this.id = id;
        this.firstName = firstName;
        this.middleName = middleName == null ? "" : middleName;
        this.lastName = lastName;
        this.birthdate = birthdate;
        this.sex = sex;
        this.civilStatus = civilStatus;
        this.address = address;
        this.contactNumber = contactNumber;
        this.purok = purok;
        this.approved = approved;
    }

    public String getFullName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (middleName != null && !middleName.trim().isEmpty()) sb.append(' ').append(middleName);
        sb.append(' ').append(lastName);
        return sb.toString();
    }

    public int getAge() { return getAge(LocalDate.now()); }

    public int getAge(LocalDate on) { return Period.between(birthdate, on).getYears(); }

    public boolean isSKMember() {
        int a = getAge();
        return a >= SK_MIN_AGE && a <= SK_MAX_AGE;
    }

    public boolean isSeniorCitizen() { return getAge() >= SENIOR_MIN_AGE; }

    /** "Senior Citizen", "SK Member" or "Regular". */
    public String getClassification() {
        if (isSeniorCitizen()) return "Senior Citizen";
        if (isSKMember()) return "SK Member";
        return "Regular";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String v) { this.middleName = v == null ? "" : v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate v) { this.birthdate = v; }
    public Sex getSex() { return sex; }
    public void setSex(Sex v) { this.sex = v; }
    public CivilStatus getCivilStatus() { return civilStatus; }
    public void setCivilStatus(CivilStatus v) { this.civilStatus = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String v) { this.contactNumber = v; }
    public String getPurok() { return purok; }
    public void setPurok(String v) { this.purok = v; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean v) { this.approved = v; }

    @Override
    public String toString() {
        return id + " | " + getFullName() + " | " + getAge() + " | " + sex + " | " + purok;
    }
}
