package brgy.service;

import brgy.model.DocumentRequest;
import brgy.model.Resident;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Builds the print-ready certificate (HTML - open it in a browser and press Ctrl+P). */
public class DocumentGenerator {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    // Change these (or call BarangaySystem.setBarangayInfo) to match the real barangay.
    private String barangay = "Barangay Sample";
    private String municipality = "Sample City";
    private String province = "Sample Province";
    private String captain = "Hon. Punong Barangay";

    public void setBarangayInfo(String barangay, String municipality, String province, String captain) {
        this.barangay = barangay;
        this.municipality = municipality;
        this.province = province;
        this.captain = captain;
    }

    public String buildText(DocumentRequest q, Resident r) {
        String name = r.getFullName().toUpperCase();
        String who = name + ", " + r.getAge() + " years old, " + r.getCivilStatus().getLabel().toLowerCase()
                + ", a resident of " + r.getAddress() + ", " + r.getPurok();
        String purpose = q.getPurpose();
        switch (q.getType()) {
            case BARANGAY_CLEARANCE:
                return "This is to certify that " + who + ", is known to this office to be of good moral character "
                        + "and has no derogatory record on file in this barangay.\n\n"
                        + "This clearance is issued upon request of the above-named person for " + purpose + ".";
            case CERTIFICATE_OF_RESIDENCY:
                return "This is to certify that " + who + ", is a bona fide resident of this barangay.\n\n"
                        + "This certification is issued upon request of the above-named person for " + purpose + ".";
            default:
                return "This is to certify that " + who + ", belongs to an indigent family in this barangay "
                        + "and is in need of assistance.\n\n"
                        + "This certification is issued upon request of the above-named person for " + purpose + ".";
        }
    }

    /** Writes REQ-xxxxx.html into the documents folder and returns its path. */
    public Path generate(DocumentRequest q, Resident r, String issuedBy, Path documentsDir) {
        String body = esc(buildText(q, r)).replace("\n\n", "</p><p>");
        String released = q.getDateReleased().format(LONG_DATE);
        String html = "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>" + esc(q.getType().getLabel())
                + " - " + esc(q.getRequestId()) + "</title>\n<style>"
                + "body{font-family:'Times New Roman',serif;max-width:700px;margin:40px auto;line-height:1.7;color:#000}"
                + ".hdr{text-align:center}.hdr p{margin:0}.title{text-align:center;font-size:24px;font-weight:bold;"
                + "letter-spacing:2px;margin:36px 0 24px;text-decoration:underline}"
                + ".sig{margin-top:70px;text-align:right}.foot{margin-top:50px;font-size:12px;color:#444}"
                + "@media print{body{margin:0}}</style></head><body>\n"
                + "<div class=\"hdr\"><p>Republic of the Philippines</p><p>Province of " + esc(province) + "</p>"
                + "<p>" + esc(municipality) + "</p><p><b>" + esc(barangay).toUpperCase() + "</b></p>"
                + "<p>OFFICE OF THE PUNONG BARANGAY</p></div>\n"
                + "<div class=\"title\">" + esc(q.getType().getLabel()).toUpperCase() + "</div>\n"
                + "<p>TO WHOM IT MAY CONCERN:</p>\n<p>" + body + "</p>\n"
                + "<p>Issued this " + released + " at " + esc(barangay) + ", " + esc(municipality) + ".</p>\n"
                + "<div class=\"sig\"><b>" + esc(captain) + "</b><br>Punong Barangay</div>\n"
                + "<div class=\"foot\">Request No: " + esc(q.getRequestId()) + " &nbsp;|&nbsp; Resident ID: "
                + esc(r.getId()) + " &nbsp;|&nbsp; Processed by: " + esc(issuedBy)
                + "<br>Fees (if any) are settled at the barangay hall.</div>\n</body></html>\n";
        try {
            Path file = documentsDir.resolve(q.getRequestId() + ".html");
            Files.write(file, html.getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException e) {
            throw new ValidationException("Could not create the document file: " + e.getMessage());
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
