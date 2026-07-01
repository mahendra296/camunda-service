# Intelligent Document Processing (IDP) with Camunda 8

## Overview

Intelligent Document Processing (IDP) is a solution that automates the extraction, validation, and processing of information from documents such as PDFs, scanned images, invoices, contracts, loan documents, and identity documents.

Camunda 8's orchestration layer (Zeebe) does not itself do OCR/AI — but as of the 8.7+
Connectors bundle, Camunda **ships a native, out-of-the-box IDP Extraction connector**
(`camunda/connectors-bundle`, module `connector-idp-extraction`) that talks directly to a
hyperscaler's document/AI services (AWS Textract + Bedrock, Azure AI Document Intelligence +
AI Foundry, Google Document AI + Vertex AI, or any OpenAI-compatible endpoint). No custom
Java worker is required for the extraction step — see
[Implementation in this repository](#implementation-in-this-repository) below.

The actual document understanding is still performed by an external, cloud AI/OCR provider —
Camunda coordinates the workflow and calls that provider through the connector:

- AWS Textract / Bedrock
- Google Document AI / Vertex AI
- Azure AI Document Intelligence / AI Foundry
- ABBYY Vantage
- Any OpenAI-compatible `/chat/completions` endpoint

---

## Implementation in this repository

`loan-document-idp-process.bpmn` is a **single-document, template-selection harness**, not a
parallel comparison anymore. `gw_parallel_split`/`gw_parallel_join` are **exclusive gateways**
(despite the `gw_parallel_*` element IDs — a naming leftover from an earlier parallel-gateway
design that hasn't been renamed) — exactly **one** of the 3 branches runs per process instance,
selected by the `exportType` process variable:

| `exportType` | Branch taken | Extraction task |
|---|---|---|
| `0` | Structured | `Activity_0wvd79i` (name: "Extract Structured") |
| `1` | Unstructured | `Activity_1mydyaq` (name: "Extract Unstructured") |
| anything else (default flow) | Unstructured + Image | `task_extract_image` |

The earlier single-branch pipeline (`task_evaluate_confidence` → `task_review_extracted_data` →
`task_register_loan`) is still not wired into this process; those elements/workers/DMN remain
orphaned, not deleted — see [the note at the end of this section](#orphaned-elements).

**Only one document path is sent per request, regardless of branch.** `LoanDocumentUploadRequest`
still declares 3 path fields (`documentPath`, `unstructuredDocumentPath`,
`unstructuredImageDocumentPath`), but `LoanDocumentService.buildVariables()` only forwards
`documentPath` as a process variable — the other two are accepted by the API but never read.
All 3 `idp.store-document` tasks map `=documentPath` → `documentPath` regardless of which branch
they're in, so whichever single file you send is the one the selected branch stores and extracts.
This is a real inconsistency worth cleaning up (either drop the two unused DTO fields, or start
forwarding all 3 and have each branch read its own) — flagged here rather than silently
"fixed", since the correct direction depends on whether you still want per-branch files later.
The two unused fields are still `@NotBlank`-validated, though, so requests currently have to send
placeholder values for them or the API rejects the request with a 400 before it ever reaches the
process.

All 3 branches share:

| | |
|---|---|
| Job type (extraction) | `io.camunda:idp-extraction-connector-template:1` |
| Job type (store) | `idp.store-document` (`StoreLoanDocumentWorker` — completes with a `document` variable, not `loanDocument`; each branch's task renames it via `zeebe:output` to `structuredLoanDocument` / `unstructuredLoanDocument` / `imageLoanDocument`) |
| Job type (print) | `idp.print-extraction-result` (`PrintExtractionResultWorker` — one job type, reused per branch, disambiguated by `templateLabel`) |
| Runtime | `connectors` service (`camunda/connectors-bundle`) |
| Text extraction (OCR) | AWS Textract (`baseRequest.extractionEngineType=AWS_TEXTRACT`), document staged from Camunda's document store to the `IDP_AWS_BUCKET_NAME` S3 bucket |
| Secrets required | `IDP_AWS_ACCESSKEY`, `IDP_AWS_SECRETKEY`, `IDP_AWS_BUCKET_NAME`, `IDP_AWS_REGION` in `connector-secrets.txt` (prefixed `CONNECTORS_SECRET`, see below) |

**The exact taxonomy/fields per branch are in active flux** (they're being iterated on directly
in Web Modeler's IDP app and change frequently), so this doc intentionally does not pin an exact
field list — check each task's `input.taxonomyItems` (Unstructured/Image) or
`input.includedFields` (Structured) in the BPMN for what's currently configured. Two things to
watch for whenever those fields change:

- **`resultExpression` does not update itself when the taxonomy changes.** Right now Structured
  and Unstructured both use a raw passthrough (`={extractedDataResponseStructured:
  response.extractedFields}` / same for Unstructured) so they always reflect whatever fields the
  connector actually returns — safe against taxonomy drift. **Image's `resultExpression` was not
  updated the same way** — it's still hardcoded to `response.extractedFields.loanAmount`, a field
  that isn't in its current taxonomy (`userName`, `dateOfBirth`, `email`,
  `isSignatureUploaded`) — so `extractedDataResponseImage` will currently always be `null`. Fix
  it to `={extractedDataResponseImage: response.extractedFields}` (matching the other two) or to
  whatever specific fields actually matter, once decided.
- **No branch currently computes a `confidence` score.** All 3 `resultExpression`s were
  simplified to raw passthrough — there's no non-null-field-count calculation anymore. If a
  confidence-gated review step gets reattached later, that calculation needs to be added back in.

Non-obvious things learned the hard way while wiring this up, worth knowing before touching this
task again:

1. **STRUCTURED mode (Amazon Textract Forms) does not work on freeform "Label: value" text
   documents.** It returns zero extracted fields against that shape, because Textract's Forms
   detector is a spatial/visual form-field detector for scanned/boxed forms, not a text parser.
   UNSTRUCTURED mode (an LLM reading raw text against taxonomy prompts) is the correct method for
   that document shape; STRUCTURED mode is the right choice only for genuinely boxed/labeled
   forms, matched via `input.includedFields` literal label text (not `taxonomyItems` prompts).
2. **`extractionType` and `extractionEngineType` are two different axes, easy to conflate.**
   `baseRequest.extractionEngineType` picks the OCR/text-extraction backend (`AWS_TEXTRACT`,
   `GCP_DOCUMENT_AI`, etc. — how raw text gets out of the document). `input.extractionType`
   picks how fields get derived from that text: `"STRUCTURED"` matches literal label text via
   `input.includedFields`/`input.renameMappings` and ignores `taxonomyItems`/`converseData`
   entirely (no LLM step); `"UNSTRUCTURED"` hands the raw text to an LLM (`converseData.modelId`)
   which maps it onto `taxonomyItems` prompts.
3. **Applying/reconfiguring an element template from Web Modeler's IDP app resets unfilled
   fields to a broken self-referencing FEEL expression** (e.g. `= input.extractionType`,
   evaluating a process variable literally named `input` that doesn't exist) — and this happens
   **every time** the template is reapplied or its config panel is touched again, not just the
   first time. As of this writing, both `Activity_1mydyaq` (Unstructured) and `task_extract_image`
   have `input.extractionType` reset to this broken placeholder (should be a literal
   `="UNSTRUCTURED"`) — check and fix it after every Web Modeler edit before deploying, don't
   assume a previous fix stuck.
4. **`resultExpression`'s FEEL scope only ever exposes `response`** (the connector's own output)
   — no other process variables, however they're named or however many `zeebe:input` mappings try
   to smuggle them in as job-local variables. A process variable that needs to end up alongside
   the extraction result has to be read from its own top-level variable downstream instead — it
   can't be nested into `extractedDataResponse*` via `resultExpression`. If a value genuinely
   needs to live inside the connector's output object, use a regular `zeebe:output` mapping on the
   task instead (that one does have full process-variable scope) — just know Web Modeler's own IDP
   configuration panel only ever writes `resultExpression`, so a `zeebe:output` addition gets
   silently dropped the next time the task is reconfigured through that UI rather than by
   hand-editing the BPMN.

### Orphaned elements

Removing the single-branch tail from `loan-document-idp-process.bpmn` left these Java/DMN files
unused but **not deleted** (nothing else in the codebase references them, confirmed by search):
`RegisterLoanFromDocumentWorker.java` (`idp.register-loan`), `idp-confidence-rules.dmn`, and the
`UserTaskService`/`UserTaskController` methods that complete the old `task_review_extracted_data`
user task. They're left in place in case the loan-registration tail gets reattached to this or
another process later — delete them explicitly if that's not the plan.

Secret naming: this connectors-bundle build's `EnvironmentSecretProvider` only exposes env vars
prefixed `CONNECTORS_SECRET` (no separator) as connector secrets. `{{secrets.IDP_AWS_ACCESSKEY}}`
in the BPMN resolves against the env var `CONNECTORS_SECRETIDP_AWS_ACCESSKEY`, not
`IDP_AWS_ACCESSKEY` — see the comment in `connector-secrets.txt`.

This is the pattern to follow for any other document type in this codebase: point a service
task's `zeebe:taskDefinition` at the connector job type (get the exact string from the
connector's own `Starting job worker: ... with type ...` startup log line — it's the element
template ID, not the Java class name), map the document + taxonomy via `zeebe:input`, and shape
the final process variable with `resultExpression` using only fields available on `response` —
if you need to mix in other process variables, read them from their own top-level variable
downstream instead of trying to nest them into the connector's result object.

---

# High-Level Architecture

```text
                User Uploads PDF
                       |
                       v
             Camunda 8 Process Starts
                       |
                       v
            Store document (S3, Blob, etc.)
                       |
                       v
         Service Task -> OCR/IDP Service
                       |
      ------------------------------------------
      |                |                |
 Google Document AI  AWS Textract  Azure Document AI
 ABBYY              OpenAI Vision  Tesseract OCR
      ------------------------------------------
                       |
               Extracted JSON
                       |
                       v
             Validate Extracted Data
                       |
            Confidence >= Threshold?
                 /               \
               Yes               No
                |                 |
                |           Human Review
                |                 |
                -------+-----------
                       |
                       v
             Continue Business Flow
                       |
                       v
              Register Loan / Save Data
```

---

# Real-Life Example

Suppose a borrower uploads a loan package containing:

- Borrower Name
- Loan Number
- Property Address
- Loan Amount
- Closing Date

Instead of entering all information manually, the system automatically extracts these values using AI.

---

# Camunda Workflow

```text
Start
   |
Upload Document
   |
Store PDF
   |
OCR Service Task
   |
Extract Fields
   |
Validate
   |
Confidence >= 90% ?
   |
+----------+----------+
|                     |
Yes                   No
|                     |
Continue        Human Review
|                     |
+----------+----------+
           |
Register Loan
           |
End
```

---

# Step 1 – Upload Document

The frontend uploads a PDF.

Example process variables:

```json
{
  "documentId": "abc123",
  "fileName": "loan.pdf"
}
```

The document should be stored in object storage such as:

- Amazon S3
- Azure Blob Storage
- Google Cloud Storage
- Local File Storage

Instead of storing the PDF inside Camunda, store only its reference.

Example:

```json
{
  "documentUrl": "s3://loan-documents/loan.pdf"
}
```

---

# Step 2 – OCR / IDP Service Task

> This section describes the **generic pattern** for wiring OCR/IDP into a BPMN process with a
> hand-written job worker. In this repository, `task_extract_document` instead uses Camunda's
> real **out-of-the-box IDP Extraction connector** directly — no custom worker — see
> [Implementation in this repository](#implementation-in-this-repository) above. Use the
> pattern below only when the out-of-the-box connector doesn't cover your provider/use case.

Create a Service Task in BPMN.

Job Type

```
ocr-document
```

A Camunda Job Worker subscribes to this job.

Example (.NET):

```csharp
client.NewWorker()
      .JobType("ocr-document")
```

Worker responsibilities:

1. Download the PDF
2. Send it to an OCR/AI service
3. Wait for processing
4. Receive structured JSON
5. Complete the Camunda job

Example response:

```json
{
  "borrowerName": "John Smith",
  "loanAmount": 350000,
  "address": "New York",
  "confidence": 0.96
}
```

---

# Step 3 – Store Extracted Data

The worker completes the job and returns process variables.

```json
{
  "borrowerName": "John Smith",
  "loanAmount": 350000,
  "address": "New York",
  "confidence": 0.96
}
```

Camunda stores these values as process variables for use in later tasks.

---

# Step 4 – Validate Confidence

An Exclusive Gateway checks whether the extracted information is reliable.

Expression:

```
= confidence >= 0.9
```

If confidence is high:

```
Continue Process
```

Otherwise:

```
Human Review
```

---

# Step 5 – Human Review

A User Task appears in Camunda Tasklist.

The reviewer sees:

```
Borrower Name
John Smith

Loan Amount
350000

Address
123 Main Street
```

If necessary, the reviewer edits incorrect values before completing the task.

---

# Step 6 – Register Loan

Another Service Task registers the loan.

Job Type

```
register-loan
```

The worker calls the business API.

Example:

```
POST /loan/register
```

Request:

```json
{
    "borrower": "John Smith",
    "loanAmount": 350000
}
```

After successful registration, the process continues.

---

# BPMN Example

```text
(Start)
    |
Upload Document
    |
OCR Service Task
    |
Extract Data
    |
Exclusive Gateway
    |
    +------------------------+
    |                        |
Confidence OK?         Low Confidence
    |                        |
Register Loan         Human Review
    |                        |
    +-----------+------------+
                |
               End
```

---

# Popular IDP Providers

| Provider | Features |
|----------|----------|
| AWS Textract | OCR, forms, tables, handwriting |
| Google Document AI | Invoices, IDs, contracts, custom processors |
| Azure AI Document Intelligence | Forms, tables, layouts, custom models |
| ABBYY Vantage | Enterprise document processing |
| OpenAI Vision | AI-powered structured extraction from documents |
| Tesseract OCR | Open-source OCR engine |

---

# Camunda Components

| Component | Responsibility |
|------------|----------------|
| BPMN Process | Orchestrates the workflow |
| Service Task | Calls external OCR/AI services |
| Job Worker | Integrates with OCR APIs |
| Gateway | Makes routing decisions |
| User Task | Human validation |
| Process Variables | Stores extracted data |

---

# Typical Process Variables

```json
{
  "documentId": "123",
  "documentUrl": "s3://loan.pdf",
  "borrowerName": "John Smith",
  "loanNumber": "LN12345",
  "loanAmount": 350000,
  "propertyAddress": "123 Main St",
  "confidence": 0.96,
  "reviewRequired": false
}
```

---

# Complete Processing Flow

```text
User
 |
 | Upload PDF
 |
 v
Camunda Process
 |
 v
Store Document
 |
 v
OCR Worker
 |
 v
Download PDF
 |
 v
Call IDP Service
 |
 v
Extract JSON
 |
 v
Return Variables
 |
 v
Gateway
 |
 +------------------------+
 |                        |
High Confidence      Low Confidence
 |                        |
Register Loan      Human Review
 |                        |
 +-----------+------------+
             |
            End
```

---

# Worker Responsibilities

The OCR worker should:

- Download the document
- Call the OCR/AI API
- Parse the response
- Return structured variables
- Handle retries
- Report failures appropriately

The worker should **not** implement business workflow logic. That remains in Camunda.

---

# Best Practices

- Store only document references (URLs or IDs) in Camunda variables.
- Store original PDFs in object storage.
- Keep AI/OCR processing inside external workers.
- Use confidence scores to determine whether human review is required.
- Validate extracted values using business rules.
- Make workers idempotent to safely handle retries.
- Store extracted JSON for auditing and troubleshooting.
- Use BPMN error events for business errors and retries/incidents for transient technical failures.

---

# Applying IDP to Loan Registration

For a loan registration process:

1. Borrower uploads a loan package.
2. Camunda starts the workflow.
3. The PDF is stored in cloud storage.
4. An OCR worker sends the document to an IDP provider.
5. The provider extracts borrower information, loan number, property details, and financial values.
6. Camunda evaluates the confidence score.
7. If confidence is low, a human reviews and corrects the extracted data.
8. After validation, Camunda invokes the loan registration service.
9. The process continues with downstream integrations such as document generation, notifications, or reporting.

This architecture keeps Camunda focused on orchestration while allowing OCR and AI providers to be replaced or upgraded without changing the BPMN process.