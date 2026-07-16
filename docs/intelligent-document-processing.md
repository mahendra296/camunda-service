# Intelligent Document Processing (IDP) with Camunda 8

## Overview

Intelligent Document Processing (IDP) is a solution that automates the extraction, validation, and processing of information from documents such as PDFs, scanned images, invoices, contracts, loan documents, and identity documents.

Camunda 8 is **not an OCR or AI engine**. Instead, it acts as the **workflow orchestrator**, coordinating document processing, AI extraction, validation, human review, and downstream business processes.

The actual document understanding is performed by external IDP services such as:

- AWS Textract
- Google Document AI
- Azure AI Document Intelligence
- ABBYY Vantage
- OpenAI Vision
- Tesseract OCR

Camunda coordinates the complete business process while workers integrate with these services.

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