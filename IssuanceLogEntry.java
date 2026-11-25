package brgy.model;

import java.time.LocalDate;

public class IssuanceLogEntry {
    private final String logId;
    private final String requestId;
    private final String residentId;
    private final String residentName;
    private final DocumentType type;
    private final LocalDate dateIssued;
    private final String issuedBy;

    public IssuanceLogEntry(String logId, String requestId, String residentId, String residentName,
                            DocumentType type, LocalDate dateIssued, String issuedBy) {
        this.logId = logId;
        this.requestId = requestId;
        this.residentId = residentId;
        this.residentName = residentName;
        this.type = type;
        this.dateIssued = dateIssued;
        this.issuedBy = issuedBy;
    }

    public String getLogId() { return logId; }
    public String getRequestId() { return requestId; }
    public String getResidentId() { return residentId; }
    public String getResidentName() { return residentName; }
    public DocumentType getType() { return type; }
    public LocalDate getDateIssued() { return dateIssued; }
    public String getIssuedBy() { return issuedBy; }

    @Override
    public String toString() {
        return logId + " | " + dateIssued + " | " + residentName + " | " + type + " | by " + issuedBy;
    }
}
