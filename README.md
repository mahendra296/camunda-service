# Camunda Service — Order Management, Airtel Loan, Loan Risk Assessment, Loan Application Form & Multi-Instance Demo

A Spring Boot + Camunda 8 service that orchestrates multiple business processes using BPMN 2.0 workflows and Zeebe job workers: the complete **order lifecycle**, the **Airtel loan origination** flow, a **loan risk assessment** process featuring a Business Rule Task, a **loan application form** process demonstrating Camunda Form components, and a **multi-instance demo** that explains loop, sequential, and parallel iteration patterns.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Complete Process Flow](#complete-process-flow)
4. [Job Workers Reference](#job-workers-reference)
5. [REST API Endpoints](#rest-api-endpoints)
   - [Order Management API](#order-management-api)
   - [Airtel Loan API](#airtel-loan-api)
   - [Loan Risk Assessment API](#loan-risk-assessment-api)
   - [Loan Application Form API](#loan-application-form-api)
   - [Multi-Instance Demo API](#multi-instance-demo-api)

---

## Architecture Overview

```
Client (REST)
     │
     ▼
OrderProcessController / UserTaskController   ← REST layer
     │
     ▼
OrderProcessService / UserTaskService         ← Business logic
     │
     ▼
CamundaClient / JobClient (Zeebe)             ← Starts instances, publishes messages, completes jobs
     │
     ▼
BPMN Engine (Zeebe)                           ← Executes order-management-process.bpmn
     │
     ▼
Job Workers (@JobWorker)                      ← Pick up service tasks, execute logic
```

Each **service task** in the BPMN has a `type` (job type). Zeebe creates a job with that type. The matching `@JobWorker` bean picks it up, processes it, and completes or fails the job.

**User tasks** are job-based (`io.camunda.zeebe:userTask`). When active, `UserTaskInterceptorWorker` stores the job key as a process variable (e.g. `task_approve_shipment_jobKey`) so it can be retrieved from Camunda Operate and used to complete the task via the REST API.

---

## Project Structure

```
src/main/java/com/camunda/
├── controller/
│   ├── OrderProcessController.java         ← Order & message endpoints
│   ├── UserTaskController.java             ← User task completion endpoints
│   ├── AirtelLoanController.java           ← Airtel loan initiation endpoint
│   ├── LoanRiskAssessmentController.java   ← POST /api/loans/assess
│   ├── LoanApplicationFormController.java  ← POST /api/loan-forms/start, /{taskKey}/submit
│   └── DemoProcessController.java          ← POST /api/demo/start
├── service/
│   ├── OrderProcessService.java            ← Process instance & message logic
│   ├── UserTaskService.java                ← Job completion via JobClient
│   ├── AirtelLoanService.java              ← Starts airtel-loan-capbpm-process
│   ├── LoanRiskAssessmentService.java      ← Starts loan-risk-assessment-process
│   ├── LoanApplicationFormService.java     ← Starts loan-application-form-process
│   └── DemoProcessService.java             ← Starts multi-instance-demo-process
├── worker/
│   ├── UserTaskInterceptorWorker.java      ← io.camunda.zeebe:userTask — stores jobKey as variable
│   ├── ValidateOrderWorker.java            ← order.validate
│   ├── CheckInventoryWorker.java           ← order.check-inventory
│   ├── ReserveInventoryWorker.java         ← order.reserve-inventory
│   ├── ProcessPaymentWorker.java           ← order.process-payment
│   ├── HandlePaymentFailureWorker.java     ← order.handle-payment-failure
│   ├── NotifyPaymentFailedWorker.java      ← order.notify-payment-failed
│   ├── SendRejectionWorker.java            ← order.send-rejection
│   ├── NotifyBackorderWorker.java          ← order.notify-backorder
│   ├── SendConfirmationWorker.java         ← order.send-confirmation
│   ├── PrepareShipmentWorker.java          ← order.prepare-shipment
│   ├── AssignCarrierWorker.java            ← shipment.assign-carrier
│   ├── GenerateShippingLabelWorker.java    ← shipment.generate-label
│   ├── PickPackWorker.java                 ← shipment.pick-pack
│   ├── DispatchCarrierWorker.java          ← shipment.dispatch
│   ├── SendDeliveryConfirmationWorker.java ← order.send-delivery-confirmation
│   ├── ProcessCancellationWorker.java      ← order.process-cancellation
│   ├── HandleSlaBreachWorker.java          ← order.handle-sla-breach
│   ├── ReshipOrderWorker.java              ← order.reship
│   ├── ProcessRefundWorker.java            ← order.process-refund
│   ├── form/
│   │   ├── ValidateApplicationDataWorker.java  ← form.validate-application-data
│   │   ├── SubmitForReviewWorker.java           ← form.submit-for-review
│   │   └── NotifyIncompleteWorker.java          ← form.notify-incomplete
│   ├── loan/
│   │   ├── ValidateLoanApplicationWorker.java  ← loan.validate-application
│   │   ├── SendRejectionNotificationWorker.java ← loan.send-rejection-notification
│   │   ├── AutoApproveLoanWorker.java           ← loan.auto-approve
│   │   └── AutoRejectLoanWorker.java            ← loan.auto-reject
│   │   (risk evaluation → Zeebe DMN engine, no worker needed)
│   └── demo/
│       ├── PrepareDataWorker.java          ← demo.prepareData
│       ├── ProcessLoopWorker.java          ← demo.processLoop
│       ├── ProcessSequentialWorker.java    ← demo.processSequential
│       ├── ProcessParallelWorker.java      ← demo.processParallel
│       └── CollectResultsWorker.java       ← demo.collectResults
├── dto/
│   ├── OrderRequest.java
│   ├── OrderItemDto.java
│   ├── MessageRequest.java
│   ├── StartOrderResponse.java
│   ├── AirtelLoanRequest.java
│   ├── AirtelLoanResponse.java
│   ├── AirtelKycCallbackRequest.java
│   ├── AirtelLoanSubmitRequest.java
│   ├── LoanApplicationRequest.java
│   ├── LoanAssessmentResponse.java
│   └── StartDemoResponse.java
└── exceptions/
    ├── GlobalExceptionHandler.java
    ├── OrderNotFoundException.java
    └── OrderConflictException.java
src/main/resources/workflow/
├── order-management-process.bpmn
├── airtel-loan-process.bpmn
├── loan-risk-assessment-process.bpmn
├── loan-risk-rules.dmn
├── loan-application-form-process.bpmn
├── loan-application-form.form
└── multi-instance-demo-process.bpmn
```

---

## Complete Process Flow

```
[Start: Order Received]
        │
        ▼
[Validate Order]  ── order.validate ──► ValidateOrderWorker
        │
   <Order Valid?>
   ├── NO  ──► [Send Rejection]  ── order.send-rejection ──► SendRejectionWorker
   │                │
   │                ▼
   │         [END: Order Rejected]
   │
   └── YES ──► [Check Inventory]  ── order.check-inventory ──► CheckInventoryWorker
                    │
               <In Stock?>
               ├── NO  ──► [Notify Backorder]  ── order.notify-backorder ──► NotifyBackorderWorker
               │                   │
               │           [Wait PT1M Timer] ──► back to CheckInventory (loop)
               │
               └── YES ──► [Reserve Inventory]  ── order.reserve-inventory ──► ReserveInventoryWorker
                                   │
                           [Process Payment]  ── order.process-payment ──► ProcessPaymentWorker
                                   │
                    ┌──────────────┴─────────────────────────────┐
                    │ Boundary Error: PAYMENT_FAILED              │
                    ▼                                             ▼
          [Handle Payment Failure]                    <Payment Successful?>
          order.handle-payment-failure                ├── DECLINED ──► [Notify Payment Declined]
          ──► HandlePaymentFailureWorker              │                  order.notify-payment-failed
                    │                                 │                  ──► NotifyPaymentFailedWorker
                    ▼                                 │                         │
           [END: Payment Error]                       │                  [END: Payment Declined]
                                                      │
                                                      └── SUCCESS
                                                             │
                                              ┌─────────────┴──────────────┐
                                              ▼                            ▼
                                    [Prepare Shipment]         [Send Order Confirmation]
                                    order.prepare-shipment     order.send-confirmation
                                              │                            │
                                              └─────────────┬──────────────┘
                                                            ▼
                                              [Event-Based Gateway: Await Event]
                                              ┌─────────────┼──────────────┐
                                              ▼             ▼              ▼
                                     [Customer         [SLA Breach    [ShipmentReady
                                      Cancels]          Timer 48h]     Message]
                                          │                  │              │
                                          ▼                  ▼              │
                                 [Approve Cancellation] [Escalate to        │
                                  User Task              Manager]           │
                                  assignee:support-agent  User Task         │
                                          │             assignee:manager    │
                                          ▼                  │              │
                                 [Process Cancellation]       └─────────────┤
                                  order.process-cancellation                │
                                          │                                 ▼
                                          ▼                      [Approve Shipment]
                                  [END: Cancelled]                User Task
                                                                  assignee:warehouse-supervisor
                                                                           │
                                                                           ▼
                                                    ┌─────────── [Shipment Subprocess] ──────────────────────────┐
                                                    │         (Multi-instance — one instance per product)         │
                                                    │  [Wait: ItemShipmentReady msg per orderId_productId]        │
                                                    │  [Notify Shipment Approval] order.notify-shipment-approval  │
                                                    │  [Approve Shipment]         User Task: warehouse-supervisor │
                                                    │  [Assign Carrier]           shipment.assign-carrier         │
                                                    │  [Generate Label]           shipment.generate-label         │
                                                    │  [Pick & Pack]              shipment.pick-pack              │
                                                    │  [Dispatch]                 shipment.dispatch               │
                                                    └────────────────────────────────────────────────────────────┘
                                                    │ Non-interrupting boundary: Shipping SLA 72h
                                                    │ ──► [Handle SLA Breach] order.handle-sla-breach
                                                    │              ▼
                                                    │     [END: SLA Notified]
                                                    │
                                                    ▼
                                          [Delivery Confirmed Msg]
                                                    │
                                          <Delivered Successfully?>
                                          ├── YES ──► [Send Delivery Confirmation]
                                          │            order.send-delivery-confirmation
                                          │                    │
                                          │                    ▼
                                          │           [END: Order Complete]
                                          │
                                          └── NO  ──► [Handle Delivery Issue]
                                                       User Task: support-agent
                                                               │
                                                       <Reship or Refund?>
                                                       ├── RESHIP ──► [Reship Order]
                                                       │               order.reship
                                                       │                    │
                                                       │               [END: Reshipped]
                                                       │
                                                       └── REFUND ──► [Initiate Refund]
                                                                       User Task: finance-agent
                                                                               │
                                                                       [Process Refund]
                                                                       order.process-refund
                                                                               │
                                                                       [END: Refunded]
```

---

### Loan Application Form Process Flow

```
[Start: Application Started]
        │
        ▼
[Fill Loan Application Form ☰]  ── User Task (assignee: applicant)
  Form fields: textfield, number, select, radio, datetime, textarea, checkbox
        │
        ▼
[Validate Application Data]  ── form.validate-application-data ──► ValidateApplicationDataWorker
        │
   <Form Valid?>
   ├── NO  ──► [Notify Incomplete Form]  ── form.notify-incomplete ──► NotifyIncompleteWorker
   │                     │
   │                     ▼
   │            [END: Form Incomplete]
   │
   └── YES ──► [Submit Application for Review]  ── form.submit-for-review ──► SubmitForReviewWorker
                         │
                         ▼
                [END: Application Submitted]
```

---

### Loan Risk Assessment Process Flow

```
[Start: Application Received]
        │
        ▼
[Validate Application]  ── loan.validate-application ──► ValidateLoanApplicationWorker
        │
   <Application Valid?>
   ├── NO  ──► [Send Rejection Notification]  ── loan.send-rejection-notification
   │                   │                         ──► SendRejectionNotificationWorker
   │                   ▼
   │            [END: Application Invalid]
   │
   └── YES ──► [Evaluate Risk Rules ⬡]  ── loan.evaluate-risk-rules ──► EvaluateRiskRulesWorker
                        │                  (Business Rule Task)
                        │   Rules: creditScore + debtToIncomeRatio → LOW / MEDIUM / HIGH
                        ▼
               <Risk Level?>
               ├── LOW    ──► [Auto Approve Loan]  ── loan.auto-approve ──► AutoApproveLoanWorker
               │                      │
               │                      ▼
               │               [END: Loan Approved]
               │
               ├── MEDIUM ──► [Manual Loan Review]  ── User Task (loan-officer)
               │                      │               task_manual_review_loan_jobKey
               │                      ▼
               │               [END: Review Pending]
               │
               └── HIGH   ──► [Auto Reject Loan]  ── loan.auto-reject ──► AutoRejectLoanWorker
                                      │
                                      ▼
                               [END: Loan Rejected]
```

| Decision point | Worker / Element | Outcome variable | Effect |
|---|---|---|---|
| Validate application | `ValidateLoanApplicationWorker` | `applicationValid` | `false` → send rejection notification; `true` → evaluate risk |
| Evaluate risk rules | `EvaluateRiskRulesWorker` (BRT) | `riskLevel` | `LOW` → auto-approve; `MEDIUM` → manual review; `HIGH` → auto-reject |

---

## Job Workers Reference

### Order Workers

| Job Type | Worker | Key Output Variables |
|---|---|---|
| `order.validate` | `ValidateOrderWorker` | `orderValid`, `totalAmount`, `validationError` |
| `order.check-inventory` | `CheckInventoryWorker` | `inStock`, `outOfStockItems` |
| `order.reserve-inventory` | `ReserveInventoryWorker` | `inventoryReservationId` |
| `order.process-payment` | `ProcessPaymentWorker` | `paymentStatus` (`SUCCESS`/`DECLINED`) — throws `PAYMENT_FAILED` BPMN error on exception |
| `order.handle-payment-failure` | `HandlePaymentFailureWorker` | — |
| `order.notify-payment-failed` | `NotifyPaymentFailedWorker` | — |
| `order.send-rejection` | `SendRejectionWorker` | — |
| `order.notify-backorder` | `NotifyBackorderWorker` | — |
| `order.send-confirmation` | `SendConfirmationWorker` | — |
| `order.prepare-shipment` | `PrepareShipmentWorker` | `trackingNumber`, `warehouseId`, `estimatedDelivery` |
| `order.send-delivery-confirmation` | `SendDeliveryConfirmationWorker` | — |
| `order.process-cancellation` | `ProcessCancellationWorker` | `refundId` |
| `order.handle-sla-breach` | `HandleSlaBreachWorker` | — |
| `order.reship` | `ReshipOrderWorker` | `reshipTrackingNumber` |
| `order.process-refund` | `ProcessRefundWorker` | `refundTransactionId` |

### Shipment Subprocess Workers (per product — multi-instance)

| Job Type | Worker | Key Output Variables |
|---|---|---|
| `order.notify-shipment-approval` | `NotifyShipmentApprovalWorker` | `shipmentApprovalNotifiedAt`, `approvalProductId` |
| `shipment.assign-carrier` | `AssignCarrierWorker` | `carrierId`, `carrierName`, `carrierCode`, `serviceLevel` |
| `shipment.generate-label` | `GenerateShippingLabelWorker` | `trackingNumber`, `labelId`, `labelUrl` |
| `shipment.pick-pack` | `PickPackWorker` | `packageId`, `itemCount` |
| `shipment.dispatch` | `DispatchCarrierWorker` | `dispatchReference`, `shipmentResult` → collected into `shipments[]` |

### User Task Worker

| Job Type | Worker | What it does |
|---|---|---|
| `io.camunda.zeebe:userTask` | `UserTaskInterceptorWorker` | Stores job key as `{elementId}_jobKey` process variable, then re-fails to keep task open |

**Stored variables:**

| User Task | Variable | Assignee |
|---|---|---|
| Approve Shipment | `task_approve_shipment_jobKey` | `warehouse-supervisor` |
| Approve Cancellation | `task_approve_cancellation_jobKey` | `support-agent` |
| Initiate Refund | `task_initiate_refund_jobKey` | `finance-agent` |
| Manual Loan Review | `task_manual_review_loan_jobKey` | `loan-officer` |

> Read the variable value from **Camunda Operate** (process instance → Variables tab) and use it as `{{taskKey}}` in the user task API calls below.

---

### Loan Application Form Workers

| Job Type | Worker | Key Output Variables |
|---|---|---|
| `form.validate-application-data` | `ValidateApplicationDataWorker` | `formValid`, `formValidationError`, `formValidatedAt` |
| `form.submit-for-review` | `SubmitForReviewWorker` | `submissionRef`, `estimatedProcessingDays`, `submittedAt` |
| `form.notify-incomplete` | `NotifyIncompleteWorker` | `incompleteNotificationSent` |

**Form Components in `loan-application-form.form`:**

| Component Type | Field | Key |
|---|---|---|
| `textfield` | Full Name | `applicantName` |
| `textfield` | National ID | `nationalId` |
| `textfield` (email pattern) | Email Address | `emailAddress` |
| `textfield` (phone pattern) | Phone Number | `phoneNumber` |
| `datetime` (date picker) | Date of Birth | `dateOfBirth` |
| `select` | Employment Type | `employmentType` |
| `number` | Gross Monthly Income | `monthlyIncome` |
| `number` | Requested Loan Amount | `requestedAmount` |
| `number` (min 1, max 60) | Loan Tenure (months) | `tenureMonths` |
| `radio` | Loan Purpose | `loanPurpose` |
| `textarea` | Additional Information | `additionalInfo` |
| `checkbox` (required) | Accept Terms | `termsAccepted` |
| `text` | Static instructions / headings | — |
| `group` | Section containers | — |
| `separator` | Visual dividers | — |

**User Task implementation: Camunda user task** (`<zeebe:userTask />`)

`UserTaskInterceptorWorker` does **not** run for Camunda user tasks. The task key is obtained via:
- **Camunda Tasklist UI** — find the task under the `applicant` assignee
- **REST API:** `GET http://localhost:8080/v2/user-tasks?processInstanceKey={key}`

| User Task | Assignee | Completion API |
|---|---|---|
| Fill Loan Application Form | `applicant` | `CamundaClient.newUserTaskCompleteCommand(userTaskKey)` |

---

### Loan Risk Assessment Workers

| Job Type / Binding | Element Type | Worker / Engine | Key Output Variables |
|---|---|---|---|
| `loan.validate-application` | Service Task | `ValidateLoanApplicationWorker` | `applicationValid`, `validationError`, `validatedAt` |
| DMN: `loan-risk-rules` | **Business Rule Task** | **Zeebe DMN engine** (no Java worker) | `riskLevel` (`LOW`/`MEDIUM`/`HIGH`), `riskScore` |
| `loan.send-rejection-notification` | Service Task | `SendRejectionNotificationWorker` | `rejectionNotificationSent` |
| `loan.auto-approve` | Service Task | `AutoApproveLoanWorker` | `loanApproved=true`, `approvalCode`, `approvedAmount`, `approvedAt` |
| `loan.auto-reject` | Service Task | `AutoRejectLoanWorker` | `loanApproved=false`, `rejectionReason`, `rejectedAt` |

> The **Business Rule Task** uses `zeebe:calledDecision` (not `zeebe:taskDefinition`). It calls the DMN decision table `loan-risk-rules.dmn` directly — **no Java worker is needed**. The DMN engine evaluates the rules and `zeebe:ioMapping` unpacks `riskLevel` and `riskScore` into top-level process variables.

**DMN Decision Table — `loan-risk-rules.dmn`** (hit policy: FIRST)

| # | Credit Score | DTI (requestedAmount ÷ annualIncome) | → riskLevel | → riskScore |
|---|---|---|---|---|
| 1 | [750..850] | [0..0.30] | `"LOW"` | 20 |
| 2 | >= 600 | <= 0.50 | `"MEDIUM"` | 55 |
| 3 | _(any)_ | _(any)_ | `"HIGH"` | 85 |

---

### Airtel Loan Workers

| Job Type | Worker | Key Output Variables |
|---|---|---|
| `capbpm.checkCustomerExists` | `CheckCustomerExistsWorker` | `customerExists`, `customerId`, `customerCheckedAt` |
| `capbpm.fetchKycFromAirtel` | `FetchKycFromAirtelWorker` | `kycRequested`, `kycRequestedAt` — **process then waits at `catch_kyc_response`** |
| `capbpm.storeKycData` | `StoreKycDataWorker` | `kycStored`, `customerId`, `kycStoredAt` |
| `capbpm.optInCustomer` | `OptInCustomerWorker` | — |
| `capbpm.notifyOptedIn` | `NotifyOptedInWorker` | — |
| `gnu.getCreditScore` | `GetCreditScoreWorker` | `creditScore`, `creditGrade`, `eligible` |
| `capbpm.sendEligibilityResult` | `SendEligibilityResultWorker` | — **process then waits at `catch_loan_application`** |
| `capbpm.storeLoanInfo` | `StoreLoanInfoWorker` | `loanId`, `loanStored`, `loanStoredAt` |
| `capbpm.sendSubmissionConfirmation` | `SendSubmissionConfirmationWorker` | — |
| `cbs.checkCustomer` | `CheckCustomerInCbsWorker` | `customerInCbs`, `cbsAccountNo` |
| `cbs.onboardCustomer` | `OnboardCustomerCbsWorker` | — |
| `cbs.processLoanDisbursement` | `ProcessLoanDisbursementWorker` | `disbursementRef`, `disbursedAmount`, `disbursementStatus` |
| `capbpm.notifyLoanApproved` | `NotifyLoanApprovedWorker` | — |

---

## REST API Endpoints

> **Postman collection variables:** `{{baseURL}}` = `http://localhost:8081` · `{{orderId}}` · `{{productId}}` · `{{taskKey}}` · `{{trackingNumber}}` · `{{msisdn}}` · `{{applicationId}}`

> No extra variables are needed for the Multi-Instance Demo — the endpoint takes no body.

---

## Order Management API

### Start Order — Happy Path

```bash
curl -X POST {{baseURL}}/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-123",
    "customerEmail": "john.doe@example.com",
    "customerName": "John Doe",
    "shippingAddress": "123 Main St, Springfield, IL 62701",
    "paymentMethod": "CREDIT_CARD",
    "paymentToken": "tok_visa_4242",
    "items": [
      { "productId": "PROD-001", "productName": "Wireless Headphones", "quantity": 1, "unitPrice": 199.99 },
      { "productId": "PROD-002", "productName": "USB-C Cable",         "quantity": 3, "unitPrice": 9.99  },
      { "productId": "PROD-003", "productName": "Laptop Stand",        "quantity": 1, "unitPrice": 49.99 }
    ]
  }'
```

---

### Start Order — Trigger Payment BPMN Error

> Blank `paymentToken` makes `ProcessPaymentWorker` throw a BPMN error (`PAYMENT_FAILED`), caught by the boundary event → `HandlePaymentFailureWorker` → `end_payment_error`.

```bash
curl -X POST {{baseURL}}/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-002",
    "customerId": "CUST-124",
    "customerEmail": "jane.doe@example.com",
    "customerName": "Jane Doe",
    "shippingAddress": "456 Oak Ave, Chicago, IL 60601",
    "paymentMethod": "DEBIT_CARD",
    "paymentToken": "",
    "items": [
      { "productId": "PROD-003", "productName": "Laptop Stand", "quantity": 1, "unitPrice": 49.99 }
    ]
  }'
```

---

### Start Order — Trigger Payment Declined

> `paymentToken` starting with `FAIL_` makes `ProcessPaymentWorker` complete with `paymentStatus=DECLINED` → `NotifyPaymentFailedWorker` → `end_payment_declined`.

```bash
curl -X POST {{baseURL}}/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-003",
    "customerId": "CUST-125",
    "customerEmail": "bob.smith@example.com",
    "customerName": "Bob Smith",
    "shippingAddress": "789 Pine Rd, Dallas, TX 75201",
    "paymentMethod": "CREDIT_CARD",
    "paymentToken": "FAIL_card_insufficient_funds",
    "items": [
      { "productId": "PROD-004", "productName": "Gaming Mouse", "quantity": 2, "unitPrice": 79.99 }
    ]
  }'
```

---

### Start Order — Trigger Order Validation Failure

> Empty `items` array causes `ValidateOrderWorker` to set `orderValid=false` → `SendRejectionWorker` → `end_order_rejected`.

```bash
curl -X POST {{baseURL}}/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-004",
    "customerId": "CUST-126",
    "customerEmail": "alice.brown@example.com",
    "customerName": "Alice Brown",
    "shippingAddress": "321 Elm St, Seattle, WA 98101",
    "paymentMethod": "CREDIT_CARD",
    "paymentToken": "tok_valid",
    "items": []
  }'
```

---

### Start Order — Trigger Out-of-Stock / Backorder

> `productId` starting with `OUT_` causes `CheckInventoryWorker` to set `inStock=false` → backorder loop (notifies customer, waits PT1M, retries inventory check).

```bash
curl -X POST {{baseURL}}/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-005",
    "customerId": "CUST-127",
    "customerEmail": "carol.jones@example.com",
    "customerName": "Carol Jones",
    "shippingAddress": "654 Maple Dr, Miami, FL 33101",
    "paymentMethod": "NET_BANKING",
    "paymentToken": "tok_netbank_9876",
    "items": [
      { "productId": "OUT_PROD-999", "productName": "Limited Edition Sneakers", "quantity": 1, "unitPrice": 249.99 }
    ]
  }'
```

---

### Cancel Order

> Process must be waiting at the Event-Based Gateway. Triggers `Approve Cancellation` user task.

```bash
curl -X POST "{{baseURL}}/api/orders/{{orderId}}/cancel?reason=Customer+changed+mind"
```

---

### Signal Shipment Ready — Two-Step Sequence

The shipment flow uses two separate messages:

**Step 1 — Order-level (unblocks the event-based gateway)**

> Process must be waiting at the Event-Based Gateway. This starts the Shipment Process subprocess for all products.

```bash
curl -X POST {{baseURL}}/api/orders/{{orderId}}/shipment-ready
```

**Step 2 — Per-product (unblocks each multi-instance subprocess instance)**

> The Shipment Process subprocess is now running. Send this once per product to let its individual subprocess instance proceed to notify/approve/dispatch. Triggers `Notify Shipment Approval` worker, then the `Approve Shipment` user task for the warehouse supervisor.

```bash
curl -X POST {{baseURL}}/api/orders/{{orderId}}/products/{{productId}}/shipment-ready \
  -H "Content-Type: application/json" \
  -d '{ "variables": { "note": "Packed and ready", "warehouseId": "WH-CENTRAL-01" } }'
```

Repeat Step 2 once for each product in the order (with the matching `productId`).

---

### Shipment Status Update (mid-transit, informational)

```bash
curl -X POST {{baseURL}}/api/orders/{{orderId}}/shipments/{{trackingNumber}}/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "IN_TRANSIT", "location": "Chicago Distribution Hub", "note": "Departed facility" }'
```

---

### Per-Product Delivery Confirmation

```bash
curl -X POST "{{baseURL}}/api/orders/{{orderId}}/shipments/{{trackingNumber}}/delivery?success=true&note=Delivered+to+front+door"
```

---

### Order-Level Delivery Confirmation

```bash
curl -X POST "{{baseURL}}/api/orders/{{orderId}}/delivery-confirmation?success=true&note=All+products+delivered"
```

---

### Approve Shipment

> Complete the `task_approve_shipment` user task (warehouse-supervisor). Use `task_approve_shipment_jobKey` from Operate as `{{taskKey}}`.

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/shipment-approval \
  -H "Content-Type: application/json" \
  -d '{ "approved": true, "note": "All items verified and packed" }'
```

---

### Approve Cancellation

> Complete the `task_approve_cancellation` user task (support-agent). Use `task_approve_cancellation_jobKey` from Operate as `{{taskKey}}`.

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/cancellation-approval \
  -H "Content-Type: application/json" \
  -d '{ "approved": true, "reason": "Customer confirmed via phone" }'
```

---

### Initiate Refund

> Complete the `task_initiate_refund` user task (finance-agent). Use `task_initiate_refund_jobKey` from Operate as `{{taskKey}}`.

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/refund-initiation \
  -H "Content-Type: application/json" \
  -d '{ "refundAmount": 199.99, "refundMethod": "CREDIT_CARD", "note": "Full refund approved" }'
```

---

## Airtel Loan API

> **Process ID:** `airtel-loan-capbpm-process`
> **Correlation key:** `msisdn` — used by all message publish and catch events throughout the BPMN flow.
> **Postman variable:** `{{msisdn}}` e.g. `254700123456`

---

### Step 1 — Airtel Initiates Loan Request

> Simulates customer dialing \*123# on Airtel. Publishes a **LoanRequest** Zeebe message which triggers the `capbpm_start` message start event, creating a new `airtel-loan-capbpm-process` instance.

```bash
curl -X POST {{baseURL}}/api/airtel/loans/apply \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "254700123456",
    "loanAmount": 5000.00,
    "tenureMonths": 6,
    "loanPurpose": "PERSONAL"
  }'
```

**Response:**

```json
{
  "msisdn": "254700123456",
  "processInstanceKey": 2251799813685251,
  "status": "INITIATED",
  "message": "Loan request sent to CapBPM — process will start via LoanRequest message"
}
```

---

### Step 1 (variant) — Business Loan

```bash
curl -X POST {{baseURL}}/api/airtel/loans/apply \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "254711987654",
    "loanAmount": 50000.00,
    "tenureMonths": 12,
    "loanPurpose": "BUSINESS"
  }'
```

---

### Step 2 — Airtel KYC Callback

> CapBPM calls Airtel for KYC data (`capbpm.fetchKycFromAirtel` worker) then the process **pauses** at `catch_kyc_response`. Call this endpoint to resume: publishes a **KycResponse** message correlated to the waiting process instance via `msisdn`.

```bash
curl -X POST {{baseURL}}/api/airtel/kyc/callback \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "254700123456",
    "fullName": "John Mwangi",
    "nationalId": "NID123456",
    "dob": "1990-01-15",
    "email": "john.mwangi@example.com"
  }'
```

---

### Step 3 — Airtel Submits Loan Application

> After receiving the eligibility result, the process **pauses** at `catch_loan_application`. Call this endpoint to resume: publishes a **LoanApplication** message correlated to the waiting process instance via `msisdn`.

```bash
curl -X POST {{baseURL}}/api/airtel/loans/submit \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "254700123456",
    "loanAmount": 5000.00,
    "tenureMonths": 6,
    "loanPurpose": "PERSONAL"
  }'
```

---

### Airtel Loan Process Flow

```
[Airtel] POST /api/airtel/loans/apply
         → publishes LoanRequest message
                 │
                 ▼
[CapBPM] capbpm_start (message start event)
         → capbpm.checkCustomerExists
                 │
          <Customer Exists?>
          ├── NO  → capbpm.fetchKycFromAirtel  ──► [PROCESS WAITS: catch_kyc_response]
          │                                              │
          │         [Airtel] POST /api/airtel/kyc/callback
          │                  → publishes KycResponse message
          │                              │
          │         capbpm.storeKycData ◄┘
          │         capbpm.optInCustomer
          │
          └── YES (merge) → capbpm.notifyOptedIn
                                │
                    gnu.getCreditScore
                                │
                    capbpm.sendEligibilityResult ──► [PROCESS WAITS: catch_loan_application]
                                                              │
                               [Airtel] POST /api/airtel/loans/submit
                                        → publishes LoanApplication message
                                                    │
                    capbpm.storeLoanInfo ◄───────────┘
                    capbpm.sendSubmissionConfirmation
                                │
                    cbs.checkCustomer
                                │
                    <Customer in CBS?>
                    ├── NO  → cbs.onboardCustomer
                    └── YES (merge) → cbs.processLoanDisbursement
                                              │
                                  capbpm.notifyLoanApproved
                                              │
                                     [END: Loan Disbursed]
```

| Decision point | Worker | Outcome variable | Effect |
|---|---|---|---|
| Check customer exists | `CheckCustomerExistsWorker` | `customerExists` | `false` → fetch KYC; `true` → skip to opt-in |
| Get credit score | `GetCreditScoreWorker` | `eligible` | `false` → process ends; `true` → wait for loan application |
| Check customer in CBS | `CheckCustomerInCbsWorker` | `customerInCbs` | `false` → onboard in CBS first; `true` → proceed to disbursement |

---

## Loan Application Form API

> **Process ID:** `loan-application-form-process`
> **Postman variable:** `{{taskKey}}` — value of `task_fill_application_jobKey` from Camunda Operate

---

### Deployment Order (required)

> **Deploy `loan-application-form.form` first**, then deploy `loan-application-form-process.bpmn`.
> The form uses `bindingType="latest"` — Zeebe resolves the form by ID at runtime, so the form must exist in Camunda before any process instance is started.
>
> **Via Camunda Modeler:** Deploy `loan-application-form.form` → then deploy `loan-application-form-process.bpmn`.

---

### Start the Form Process

> Starts `loan-application-form-process`. The process immediately reaches the Fill Application Form user task. Retrieve `task_fill_application_jobKey` from Operate (process instance → Variables) and use it as `{{taskKey}}` in the next step.

```bash
curl -X POST {{baseURL}}/api/loan-forms/start
```

**Response:**

```json
{
  "trackingId": "TRK-A1B2C3D4",
  "processInstanceKey": 2251799813685400,
  "status": "STARTED",
  "message": "Form process started — retrieve task_fill_application_jobKey from Operate to submit the form"
}
```

---

### Submit the Loan Application Form

> Use `task_fill_application_jobKey` from Operate as `{{taskKey}}`. Submits all form field values — `ValidateApplicationDataWorker` runs next.

```bash
curl -X POST {{baseURL}}/api/loan-forms/{{taskKey}}/submit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":    "Grace Njeri",
    "nationalId":       "NID987654",
    "emailAddress":     "grace.njeri@example.com",
    "phoneNumber":      "254712345678",
    "dateOfBirth":      "1990-06-15",
    "employmentType":   "EMPLOYED",
    "monthlyIncome":    4500.00,
    "requestedAmount":  20000.00,
    "tenureMonths":     24,
    "loanPurpose":      "HOME_IMPROVEMENT",
    "additionalInfo":   "Renovating kitchen and bathroom",
    "termsAccepted":    true
  }'
```

---

### Submit — Trigger Validation Failure

> `termsAccepted: false` fails the validation rule → `NotifyIncompleteWorker` → `end_form_incomplete`.

```bash
curl -X POST {{baseURL}}/api/loan-forms/{{taskKey}}/submit \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":  "Henry Odhiambo",
    "nationalId":     "NID111222",
    "emailAddress":   "henry@example.com",
    "phoneNumber":    "254700111222",
    "dateOfBirth":    "1985-03-20",
    "employmentType": "SELF_EMPLOYED",
    "monthlyIncome":  2000.00,
    "requestedAmount": 10000.00,
    "tenureMonths":   12,
    "loanPurpose":    "BUSINESS",
    "termsAccepted":  false
  }'
```

---

## Loan Risk Assessment API

> **Process ID:** `loan-risk-assessment-process`
> **Postman variable:** `{{applicationId}}` e.g. `APP-001`

---

### Assess Loan — LOW Risk (auto-approve)

> Credit score ≥ 750 and debt-to-income ratio ≤ 30% → `EvaluateRiskRulesWorker` sets `riskLevel=LOW` → `AutoApproveLoanWorker` → `end_loan_approved`.

```bash
curl -X POST {{baseURL}}/api/loans/assess \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "APP-001",
    "applicantName": "Alice Kariuki",
    "applicantAge": 35,
    "monthlyIncome": 5000.00,
    "requestedAmount": 10000.00,
    "creditScore": 780
  }'
```

---

### Assess Loan — MEDIUM Risk (manual review)

> Credit score ≥ 600 and debt-to-income ratio ≤ 50% → `EvaluateRiskRulesWorker` sets `riskLevel=MEDIUM` → `task_manual_review_loan` user task (loan-officer). Use `task_manual_review_loan_jobKey` from Camunda Operate as `{{taskKey}}`.

```bash
curl -X POST {{baseURL}}/api/loans/assess \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "APP-002",
    "applicantName": "Brian Omondi",
    "applicantAge": 28,
    "monthlyIncome": 2000.00,
    "requestedAmount": 18000.00,
    "creditScore": 650
  }'
```

---

### Assess Loan — HIGH Risk (auto-reject)

> Credit score < 600 or debt-to-income ratio > 50% → `EvaluateRiskRulesWorker` sets `riskLevel=HIGH` → `AutoRejectLoanWorker` → `end_loan_rejected`.

```bash
curl -X POST {{baseURL}}/api/loans/assess \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "APP-003",
    "applicantName": "Carol Wanjiru",
    "applicantAge": 22,
    "monthlyIncome": 1000.00,
    "requestedAmount": 40000.00,
    "creditScore": 520
  }'
```

---

### Assess Loan — Validation Failure (invalid application)

> Empty or invalid fields → `ValidateLoanApplicationWorker` sets `applicationValid=false` → `SendRejectionNotificationWorker` → `end_application_invalid`.

```bash
curl -X POST {{baseURL}}/api/loans/assess \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "APP-004",
    "applicantName": "David Mutua",
    "applicantAge": 35,
    "monthlyIncome": 3000.00,
    "requestedAmount": 200000.00
  }'
```

> Requested amount (200 000) exceeds 60× monthly income (180 000) — fails validation rule.

---

### Complete Manual Loan Review (MEDIUM risk path)

> The process pauses at `task_manual_review_loan` when `riskLevel=MEDIUM`. Use `task_manual_review_loan_jobKey` from Camunda Operate as `{{taskKey}}`.

**Approve:**

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/loan-review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewDecision": "APPROVED",
    "reviewNote": "Income verified and credit profile acceptable"
  }'
```

**Reject:**

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/loan-review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewDecision": "REJECTED",
    "reviewNote": "Insufficient income history"
  }'
```

**Request more information:**

```bash
curl -X POST {{baseURL}}/api/tasks/{{taskKey}}/loan-review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewDecision": "MORE_INFO",
    "reviewNote": "Please provide last 3 months payslips"
  }'
```

---

### Assess Loan — Omit Credit Score (simulated score)

> When `creditScore` is omitted, `EvaluateRiskRulesWorker` simulates a score from age and income. Good for testing the simulation branch.

```bash
curl -X POST {{baseURL}}/api/loans/assess \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "APP-005",
    "applicantName": "Eve Njoroge",
    "applicantAge": 40,
    "monthlyIncome": 4000.00,
    "requestedAmount": 15000.00
  }'
```

---

## Multi-Instance Demo

### Multi-Instance Demo Workers

| Job Type | Worker | Pattern | Key Variables |
|---|---|---|---|
| `demo.prepareData` | `PrepareDataWorker` | Setup | Sets `items`, `loopCounter=0`, `maxLoops=3`, `loopDone=false` |
| `demo.processLoop` | `ProcessLoopWorker` | Loop (XOR gateway) | Increments `loopCounter`; sets `loopDone=true` at iteration 3 |
| `demo.processSequential` | `ProcessSequentialWorker` | Sequential MI | Receives `currentItem`; outputs `sequentialResult` per item |
| `demo.processParallel` | `ProcessParallelWorker` | Parallel MI | Receives `currentItem`; outputs `parallelResult` per item |
| `demo.collectResults` | `CollectResultsWorker` | Collect | Reads `sequentialResults[]` and `parallelResults[]`; logs summary |

### Multi-Instance Demo Process Flow

```
[Start] → [Prepare Data]
              │
              │  items=["Item-A","Item-B","Item-C"], loopCounter=0, maxLoops=3
              ▼
     ┌──► [Process with Loop]  ── demo.processLoop ──► ProcessLoopWorker
     │           │                 increments loopCounter each time
     │           ▼
     │    <Loop Done?>  (loopDone = loopCounter >= 3)
     │    ├── NOT DONE (false) ─────────────────────────────────┘  (token goes back)
     │    │
     └────┘
          │
          └── DONE (true)
                │
                ▼
     [Process Sequentially]  ── demo.processSequential ──► ProcessSequentialWorker
      isSequential="true"         Zeebe runs: Item-A → completes
      inputCollection="=items"                Item-B → completes
      outputCollection="sequentialResults"    Item-C → completes
                │                             (strictly one at a time)
                ▼
     [Process in Parallel]  ── demo.processParallel ──► ProcessParallelWorker
      isSequential="false"        Zeebe runs: Item-A ┐
      inputCollection="=items"                Item-B ├─ all three jobs active simultaneously
      outputCollection="parallelResults"      Item-C ┘
                │                             (task completes when last one finishes)
                ▼
     [Collect Results]  ── demo.collectResults ──► CollectResultsWorker
      Logs sequentialResults[] and parallelResults[] side-by-side
                │
                ▼
            [End]
```

### Pattern Comparison

| | Loop | Sequential MI | Parallel MI |
|---|---|---|---|
| **How it's driven** | XOR gateway routes token back | Zeebe iterates collection automatically | Zeebe iterates collection automatically |
| **Jobs active at once** | 1 | 1 | N (all items simultaneously) |
| **Order guaranteed** | Yes (single token) | Yes (Item-A → B → C) | No (whichever finishes first) |
| **BPMN element** | `exclusiveGateway` + back-flow | `multiInstanceLoopCharacteristics isSequential="true"` | `multiInstanceLoopCharacteristics isSequential="false"` |
| **Counter managed by** | Process variable (`loopCounter`) | Zeebe internally | Zeebe internally |

---

## Multi-Instance Demo API

### Start Multi-Instance Demo

> Starts `multi-instance-demo-process`. No request body needed — `PrepareDataWorker` initialises all variables. Watch the application logs to see the three patterns execute in sequence.

```bash
curl -X POST {{baseURL}}/api/demo/start
```

**Response:**

```json
{
  "processInstanceKey": 2251799813685300,
  "status": "STARTED",
  "message": "Watch the logs: loop fires 3×, then sequential (Item-A→B→C), then parallel (all at once)"
}
```

---

### What to Observe in the Logs

**Pattern 1 — Loop** (`ProcessLoopWorker`):

```
[DEMO][Loop] iteration 1/3 | loopDone=false  ← gateway routes token back
[DEMO][Loop] iteration 2/3 | loopDone=false  ← gateway routes token back
[DEMO][Loop] iteration 3/3 | loopDone=true   ← gateway routes forward
```

**Pattern 2 — Sequential Multi-Instance** (`ProcessSequentialWorker`):

```
[DEMO][Sequential] processing item='Item-A' (one at a time)
[DEMO][Sequential] Completed item='Item-A'
[DEMO][Sequential] processing item='Item-B' (one at a time)   ← starts only after A completes
[DEMO][Sequential] Completed item='Item-B'
[DEMO][Sequential] processing item='Item-C' (one at a time)
[DEMO][Sequential] Completed item='Item-C'
```

**Pattern 3 — Parallel Multi-Instance** (`ProcessParallelWorker`):

```
[DEMO][Parallel] processing item='Item-A' (all items run simultaneously)
[DEMO][Parallel] processing item='Item-B' (all items run simultaneously)  ← same timestamp
[DEMO][Parallel] processing item='Item-C' (all items run simultaneously)  ← same timestamp
```

**Pattern 4 — Collect Results** (`CollectResultsWorker`):

```
══════════════════════════════════════════════════════
  DEMO SUMMARY
══════════════════════════════════════════════════════
  Sequential results (ordered, one at a time):
    [1] {item=Item-A, pattern=sequential, processedAt=...}
    [2] {item=Item-B, pattern=sequential, processedAt=...}
    [3] {item=Item-C, pattern=sequential, processedAt=...}
  Parallel results (all at once, arrival order varies):
    [1] {item=Item-B, pattern=parallel, processedAt=...}
    [2] {item=Item-A, pattern=parallel, processedAt=...}
    [3] {item=Item-C, pattern=parallel, processedAt=...}
══════════════════════════════════════════════════════
```
