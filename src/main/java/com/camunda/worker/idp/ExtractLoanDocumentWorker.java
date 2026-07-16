package com.camunda.worker.idp;

import com.camunda.dto.ExtractedLoanDataResponse;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fetches the document content back from Camunda 8's native Document Store ({@code
 * CamundaClient.newDocumentContentGetRequest}) and extracts real text from it — Apache PDFBox for
 * {@code .pdf}, Apache POI for {@code .docx}, plain read for {@code .txt} — then a regex-based
 * field parser looks for {@code Label: value} lines (Borrower Name, Loan Amount, Property
 * Address, Closing Date).
 *
 * <p>This stands in for a real external IDP/OCR provider call (AWS Textract, Google Document AI,
 * Azure AI Document Intelligence, ABBYY Vantage, OpenAI Vision, Tesseract OCR) — swap {@link
 * #extractText} for an HTTP call to the chosen provider; the BPMN process and downstream workers
 * only depend on the {@code extractedDataResponse} shape.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code extractedDataResponse} (com.camunda.dto.ExtractedLoanDataResponse) — borrowerName,
 *       loanNumber, propertyAddress, loanAmount, closingDate, confidence, extractedAt
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ExtractLoanDocumentWorker {

    private static final Pattern BORROWER_NAME_PATTERN =
            Pattern.compile("(?im)^\\s*Borrower(?:'s)?\\s*Name\\s*[:\\-]\\s*(.+)$");
    private static final Pattern LOAN_AMOUNT_PATTERN =
            Pattern.compile("(?im)^\\s*Loan\\s*Amount\\s*[:\\-]\\s*\\$?([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern PROPERTY_ADDRESS_PATTERN =
            Pattern.compile("(?im)^\\s*Property\\s*Address\\s*[:\\-]\\s*(.+)$");
    private static final Pattern CLOSING_DATE_PATTERN = Pattern.compile("(?im)^\\s*Closing\\s*Date\\s*[:\\-]\\s*(.+)$");

    private final CamundaClient camundaClient;

    @JobWorker(type = "idp.extract-document", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String documentId,
            @Variable String loanNumber,
            @Variable Map<String, Object> loanDocument,
            @Variable(optional = true) Double simulatedConfidence) {

        var camundaDocumentId = (String) loanDocument.get("documentId");
        var storeId = (String) loanDocument.get("storeId");
        @SuppressWarnings("unchecked")
        var metadata = (Map<String, Object>) loanDocument.get("metadata");
        var fileName = metadata != null ? (String) metadata.get("fileName") : "";

        log.info(
                "[IDP][ExtractDocument] type={} key={} documentId={} camundaDocumentId={} fileName={}",
                job.getType(),
                job.getKey(),
                documentId,
                camundaDocumentId,
                fileName);

        try {
            byte[] bytes;
            try (InputStream content = camundaClient
                    .newDocumentContentGetRequest(camundaDocumentId)
                    .storeId(storeId)
                    .send()
                    .join()) {
                bytes = content.readAllBytes();
            }

            var text = extractText(fileName, bytes);
            var response = parseFields(loanNumber, text, simulatedConfidence);

            log.info(
                    "[IDP][ExtractDocument] documentId={} borrower={} confidence={}",
                    documentId,
                    response.getBorrowerName(),
                    response.getConfidence());

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("extractedDataResponse", response))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[IDP][ExtractDocument] Failed documentId={}", documentId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private String extractText(String fileName, byte[] bytes) throws IOException {
        var lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";

        if (lower.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (lower.endsWith(".docx")) {
            try (var in = new ByteArrayInputStream(bytes);
                    var document = new XWPFDocument(in);
                    var extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private ExtractedLoanDataResponse parseFields(String loanNumber, String text, Double simulatedConfidence) {
        var borrowerName = firstMatch(BORROWER_NAME_PATTERN, text);
        var loanAmountRaw = firstMatch(LOAN_AMOUNT_PATTERN, text);
        var propertyAddress = firstMatch(PROPERTY_ADDRESS_PATTERN, text);
        var closingDate = firstMatch(CLOSING_DATE_PATTERN, text);

        var matchedCount = (int) Stream.of(borrowerName, loanAmountRaw, propertyAddress, closingDate)
                .filter(Objects::nonNull)
                .count();

        var confidence = simulatedConfidence != null ? simulatedConfidence : confidenceFor(matchedCount);
        var loanAmount = loanAmountRaw != null ? Double.parseDouble(loanAmountRaw.replace(",", "")) : 0.0;

        return ExtractedLoanDataResponse.builder()
                .borrowerName(borrowerName != null ? borrowerName.trim() : "UNKNOWN")
                .loanNumber(loanNumber)
                .propertyAddress(propertyAddress != null ? propertyAddress.trim() : "UNKNOWN")
                .loanAmount(loanAmount)
                .closingDate(closingDate != null ? closingDate.trim() : "UNKNOWN")
                .confidence(confidence)
                .extractedAt(System.currentTimeMillis())
                .build();
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private double confidenceFor(int matchedFieldCount) {
        return switch (matchedFieldCount) {
            case 4 -> 0.97;
            case 3 -> 0.80;
            case 2 -> 0.55;
            case 1 -> 0.35;
            default -> 0.15;
        };
    }
}
