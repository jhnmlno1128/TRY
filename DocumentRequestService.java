package brgy.service;

import brgy.ds.DynArray;
import brgy.model.*;
import brgy.storage.DataStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/** Document request submission, review (approve / reject), printing and the issuance log. */
public class DocumentRequestService {

    private final DataStore store;
    private final ResidentService residents;
    private final DocumentGenerator generator;

    public DocumentRequestService(DataStore store, ResidentService residents, DocumentGenerator generator) {
        this.store = store;
        this.residents = residents;
        this.generator = generator;
    }

    /** Keeps free text within the database column size. */
    private static String limit(String s) { return s.length() > 500 ? s.substring(0, 500) : s; }

    public DocumentRequest submit(String residentId, DocumentType type, String purpose) {
        Resident r = residents.getById(residentId);
        if (r == null || !r.isApproved())
            throw new ValidationException("Request denied: the resident is not registered in the database.");
        if (type == null) throw new ValidationException("Please choose the type of document needed.");
        if (purpose == null || purpose.trim().isEmpty()) throw new ValidationException("Purpose of request is required.");
        if (purpose.trim().length() > 500) throw new ValidationException("Purpose can be at most 500 characters.");
        for (DocumentRequest q : store.pendingRequests.values(new DocumentRequest[0]))
            if (q.getResidentId().equalsIgnoreCase(r.getId()) && q.getType() == type)
                throw new ValidationException("You already have a pending request for a " + type.getLabel() + ".");
        DocumentRequest q = new DocumentRequest(store.nextRequestId(), r.getId(), r.getFullName(), type,
                purpose.trim(), LocalDate.now(), RequestStatus.PENDING, null, "");
        store.putRequest(q);
        store.save();
        return q;
    }

    /** Pending requests, oldest first (request IDs are sequential). */
    public DocumentRequest[] pending() { return store.pendingRequestsSorted(); }

    public DocumentRequest[] all() { return store.requestsSorted(); }

    public DocumentRequest[] byResident(String residentId) {
        DynArray<DocumentRequest> out = new DynArray<DocumentRequest>();
        for (DocumentRequest q : store.requestsSorted()) if (q.getResidentId().equalsIgnoreCase(residentId)) out.add(q);
        return out.toArray(new DocumentRequest[0]);
    }

    public DocumentRequest[] byResident(String residentId, RequestStatus status) {
        DynArray<DocumentRequest> out = new DynArray<DocumentRequest>();
        for (DocumentRequest q : byResident(residentId)) if (q.getStatus() == status) out.add(q);
        return out.toArray(new DocumentRequest[0]);
    }

    /** O(1) hash lookup. */
    public DocumentRequest getById(String id) {
        return id == null ? null : store.requests.get(DataStore.key(id));
    }

    private DocumentRequest requirePending(String id) {
        DocumentRequest q = getById(id);
        if (q == null) throw new ValidationException("Request '" + id + "' was not found.");
        if (q.getStatus() != RequestStatus.PENDING)
            throw new ValidationException("This request was already " + q.getStatus().name().toLowerCase() + ".");
        return q;
    }

    /** Approves, sets the release date (defaults to today), generates the document and logs the issuance. */
    public DocumentRequest approve(String requestId, LocalDate releaseDate, String issuedBy) {
        DocumentRequest q = requirePending(requestId);
        Resident r = residents.getById(q.getResidentId());
        if (r == null || !r.isApproved())
            throw new ValidationException("Cannot approve: the resident is not registered in the database.");
        LocalDate release = releaseDate == null ? LocalDate.now() : releaseDate;
        if (release.isBefore(q.getDateRequested()))
            throw new ValidationException("Release date cannot be earlier than the date requested.");
        q.setStatus(RequestStatus.APPROVED);
        q.setDateReleased(release);
        q.setRejectReason("");
        generator.generate(q, r, issuedBy, store.getDocumentsDir());
        store.putLog(new IssuanceLogEntry(store.nextLogId(), q.getRequestId(), r.getId(), r.getFullName(),
                q.getType(), release, issuedBy));
        store.save();
        return q;
    }

    public DocumentRequest reject(String requestId, String reason) {
        DocumentRequest q = requirePending(requestId);
        q.setStatus(RequestStatus.REJECTED);
        q.setRejectReason(reason == null || reason.trim().isEmpty() ? "No reason provided." : limit(reason.trim()));
        store.save();
        return q;
    }

    /** Path of the printable document for an approved request (re-created if the file was deleted). */
    public Path getDocumentFile(String requestId, String issuedBy) {
        DocumentRequest q = getById(requestId);
        if (q == null || q.getStatus() != RequestStatus.APPROVED)
            throw new ValidationException("Only approved requests have a printable document.");
        Path file = store.getDocumentsDir().resolve(q.getRequestId() + ".html");
        if (!Files.exists(file)) {
            Resident r = residents.require(q.getResidentId());
            generator.generate(q, r, issuedBy, store.getDocumentsDir());
        }
        return file;
    }

    /** Issuance log, oldest first. */
    public IssuanceLogEntry[] issuanceLog() { return store.logSorted(); }
}
