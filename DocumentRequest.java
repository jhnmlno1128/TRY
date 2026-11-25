package brgy.model;

import java.time.LocalDate;

public class DocumentRequest {
    private String requestId;
    private String residentId;
    private String residentName;
    private DocumentType type;
    private String purpose;
    private LocalDate dateRequested;
    private RequestStatus status;
    private LocalDate dateReleased;   // null until approved
    private String rejectReason;

    public DocumentRequest(String requestId, String residentId, String residentName, DocumentType type,
                           String purpose, LocalDate dateRequested, RequestStatus status,
                           LocalDate dateReleased, String rejectReason) {
        this.requestId = requestId;
        this.residentId = residentId;
        this.residentName = residentName;
        this.type = type;
        this.purpose = purpose;
        this.dateRequested = dateRequested;
        this.status = status;
        this.dateReleased = dateReleased;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    public String getRequestId() { return requestId; }
    public String getResidentId() { return residentId; }
    public void setResidentId(String id) { this.residentId = id; }
    public String getResidentName() { return residentName; }
    public void setResidentName(String n) { this.residentName = n; }
    public DocumentType getType() { return type; }
    public String getPurpose() { return purpose; }
    public LocalDate getDateRequested() { return dateRequested; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus s) { this.status = s; }
    public LocalDate getDateReleased() { return dateReleased; }
    public void setDateReleased(LocalDate d) { this.dateReleased = d; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String r) { this.rejectReason = r == null ? "" : r; }

    @Override
    public String toString() {
        return requestId + " | " + residentName + " | " + type + " | " + status
                + (dateReleased != null ? " | release " + dateReleased : "");
    }
}
