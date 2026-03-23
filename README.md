# Camunda Service — Order Management & Airtel Loan

A Spring Boot + Camunda 8 service that orchestrates multiple business processes using BPMN 2.0 workflows and Zeebe job workers: the complete **order lifecycle** and the **Airtel loan origination** flow.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Complete Process Flow](#complete-process-flow)
4. [Job Workers Reference](#job-workers-reference)
5. [REST API Endpoints](#rest-api-endpoints)
   - [Order Management API](#order-management-api)
   - [Airtel Loan API](#airtel-loan-api)

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
│   └── AirtelLoanController.java           ← Airtel loan initiation endpoint
├── service/
│   ├── OrderProcessService.java            ← Process instance & message logic
│   ├── UserTaskService.java                ← Job completion via JobClient
│   └── AirtelLoanService.java              ← Starts airtel-loan-capbpm-process
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
│   └── ProcessRefundWorker.java            ← order.process-refund
├── dto/
│   ├── OrderRequest.java
│   ├── OrderItemDto.java
│   ├── MessageRequest.java
│   ├── StartOrderResponse.java
│   ├── AirtelLoanRequest.java
│   ├── AirtelLoanResponse.java
│   ├── AirtelKycCallbackRequest.java
│   └── AirtelLoanSubmitRequest.java
└── exceptions/
    ├── GlobalExceptionHandler.java
    ├── OrderNotFoundException.java
    └── OrderConflictException.java
src/main/resources/workflow/
├── order-management-process.bpmn
└── airtel-loan-process.bpmn
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

> Read the variable value from **Camunda Operate** (process instance → Variables tab) and use it as `{{taskKey}}` in the user task API calls below.

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

> **Postman collection variables:** `{{baseURL}}` = `http://localhost:8081` · `{{orderId}}` · `{{productId}}` · `{{taskKey}}` · `{{trackingNumber}}` · `{{msisdn}}`

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

