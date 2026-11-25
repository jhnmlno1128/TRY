package brgy.model;

public enum DocumentType {
    BARANGAY_CLEARANCE("Barangay Clearance"),
    CERTIFICATE_OF_RESIDENCY("Certificate of Residency"),
    CERTIFICATE_OF_INDIGENCY("Indigency Certificate");

    private final String label;
    DocumentType(String label) { this.label = label; }
    public String getLabel() { return label; }
    @Override public String toString() { return label; }
}
