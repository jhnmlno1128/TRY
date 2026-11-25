package brgy.model;

public enum CivilStatus {
    SINGLE("Single"), MARRIED("Married"), WIDOWED("Widowed"), SEPARATED("Separated"), DIVORCED("Divorced");

    private final String label;
    CivilStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    @Override public String toString() { return label; }
}
