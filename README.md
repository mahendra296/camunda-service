# camunda-service

**Camunda 8 Order Management Process Orchestration**
Spring Boot 3 · Zeebe · PostgreSQL · Liquibase · Docker Compose

---

## Overview

This service orchestrates a full **Order Management** business process using
[Camunda 8](https://docs.camunda.io/) Self-Managed and the Zeebe workflow engine.
It demonstrates every major BPMN 2.0 element type inside a realistic e-commerce flow.

It also ships a **DocuSign document-signing** workflow as a secondary process demonstrating
multi-party document orchestration with message correlation.

---

## Processes

| File | Process ID | Description |
|---|---|---|
| `order-management-process.bpmn` | `order-management-process` | Full e-commerce order lifecycle |

---

## Order Management Process

### Flow Diagram

```
 [Message Start: OrderReceived]
            │
            ▼
   [Validate Order]  ── XOR: Order Valid? ──► NO ──► [Send Rejection] ──► (Error End: Rejected)
            │ YES
            ▼
   [Check Inventory]  ── XOR: In Stock? ──► NO ──► [Notify Backorder] ──► (Timer 24h) ──┐
            │ YES                                                                          │(loop)
            ▼ ◄───────────────────────────────────────────────────────────────────────────┘
   [Reserve Inventory]
            │
            ▼
   [Process Payment] ◄── (Error Boundary: PAYMENT_FAILED) ──► [Handle Failure] ──► (End)
            │
           XOR: Payment Successful?
            ├── DECLINED ──► [Notify Declined] ──► (End: Declined)
            │
            │ SUCCESS
            ▼
   (Parallel Split) ──┬──► [Prepare Shipment]   ──┐
                      └──► [Send Confirmation]   ──┘
                                    │ (Parallel Join)
                                    ▼
                         (Event-Based Gateway)
                          ├── Message: OrderCancellation ──► [Process Cancellation] ──► (End: Cancelled)
                          ├── Timer: 48h SLA Breach       ──► [Escalate to Manager] (User Task) ──┐
                          └── Message: ShipmentReady      ─────────────────────────────────────────┤
                                                                   (Inclusive Gateway: Merge) ◄────┘
                                                                              │
                                                                              ▼
                                                                       [Ship Order]
                                                            (Non-Interrupting Timer Boundary 72h)
                                                            └──► [Handle SLA Breach] ──► (End: SLA)
                                                                              │
                                                                              ▼
                                                             [Message Catch: DeliveryConfirmation]
                                                                              │
                                                                     XOR: Delivered?
                                                              ┌─── YES ──► [Send Delivery Confirmation] ──► (End: Complete ✓)
                                                              └─── NO  ──► [Handle Delivery Issue] (User Task)
                                                                                      │
                                                                             XOR: Reship or Refund?
                                                                  ┌── RESHIP ──► [Reship Order]   ──► (End: Reshipped)
                                                                  └── REFUND ──► [Process Refund]  ──► (End: Refunded)
```

### BPMN Elements Used

| BPMN Element | Count | Details |
|---|---|---|
| Message Start Event | 1 | `OrderReceived` |
| Service Tasks | 14 | One per Zeebe job type |
| User Tasks | 2 | Escalate to Manager, Handle Delivery Issue |
| Exclusive Gateways | 5 | Order Valid, In Stock, Payment Result, Delivery Result, Reship/Refund |
| Parallel Gateway (split + join) | 2 | Concurrent shipment prep + confirmation |
| Event-Based Gateway | 1 | Awaits cancel/SLA/shipment-ready |
| Inclusive Gateway | 1 | Merges SLA escalation and shipment-ready paths |
| Timer Intermediate Catch | 2 | 24 h restock wait, 48 h SLA breach |
| Message Intermediate Catch | 3 | OrderCancellation, ShipmentReady, DeliveryConfirmation |
| Error Boundary Event | 1 | Interrupting, catches `PAYMENT_FAILED` |
| Non-Interrupting Timer Boundary | 1 | 72 h shipping SLA |
| End Events | 7 | Complete, Rejected (error), Cancelled, Payment Error, Declined, Reshipped, Refunded, SLA Notified |

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.3 |
| BPM Engine | Camunda 8 Self-Managed | 8.8.3 |
| Workflow Client | Zeebe Spring Boot Starter | 8.8.14 |
| Database | PostgreSQL | 17 |
| Migrations | Liquibase | (managed by Spring Boot) |
| ORM | Spring Data JPA + Hibernate | (managed by Spring Boot) |
| Build | Maven | 3.9+ |
| Code Format | Spotless (Palantir Java Format) | 3.3.0 |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

Services started:

| Service | Port | Credentials |
|---|---|---|
| PostgreSQL | 5432 | postgres / root |
| Elasticsearch | 9200 | — |
| Camunda (Operate + Tasklist + Zeebe) | 8080, 26500 | demo / demo |

Wait ~60 seconds for Camunda to fully initialise.

### 2. Create Database

```bash
docker exec -it postgres psql -U postgres -c "CREATE DATABASE camundadb;"
```

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The service starts on **port 8081** and auto-deploys all `*.bpmn` files to Zeebe.

---

## REST API — Order Management

Base URL: `http://localhost:8081`

---

### 1. Start Order Process

**POST** `/api/orders/start`

**Purpose:** Kicks off the entire order management workflow. Publishes an `OrderReceived` message to the Zeebe message broker, which correlates with the **Message Start Event** in the BPMN process. Once started, the process automatically moves through order validation → inventory check → payment processing → shipment preparation in sequence. This is always the first call in an order lifecycle.

**curl:**
```bash
curl -X POST http://localhost:8081/api/orders/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-123",
    "customerEmail": "john.doe@example.com",
    "customerName": "John Doe",
    "items": [
      {
        "productId": "PROD-A1",
        "productName": "Wireless Headphones",
        "quantity": 2,
        "unitPrice": 49.99
      },
      {
        "productId": "PROD-B2",
        "productName": "USB-C Cable",
        "quantity": 3,
        "unitPrice": 9.99
      }
    ],
    "shippingAddress": "123 Main St, Springfield, IL 62701",
    "paymentMethod": "CREDIT_CARD",
    "paymentToken": "tok_visa_test_4242"
  }'
```

**Response (200 OK):**
```json
{
  "orderId": "ORD-001",
  "processInstanceKey": 2251799813685249,
  "status": "STARTED",
  "message": "Order process started successfully"
}
```

---

### 2. Cancel Order

**POST** `/api/orders/{orderId}/cancel`

**Purpose:** Allows a customer to cancel an order while it is still in-flight (i.e., after payment but before delivery). Sends an `OrderCancellation` **intermediate message** to the running process. The process is waiting at the **Event-Based Gateway** after payment succeeds — if this message arrives before shipment confirmation, the process immediately routes to the `Process Cancellation` service task and terminates with a `Cancelled` end event. The `orderId` in the path is the correlation key used to find the correct running process instance.

**curl (with default reason):**
```bash
curl -X POST "http://localhost:8081/api/orders/ORD-001/cancel"
```

**curl (with custom reason):**
```bash
curl -X POST "http://localhost:8081/api/orders/ORD-001/cancel?reason=Item+no+longer+needed"
```

**Response (200 OK):**
```json
{
  "status": "CANCELLATION_SENT",
  "orderId": "ORD-001"
}
```

---

### 3. Signal Shipment Ready

**POST** `/api/orders/{orderId}/shipment-ready`

**Purpose:** Called by the **warehouse system** once the order is physically packed and ready to hand off to the courier. Sends a `ShipmentReady` **intermediate message** to the process. The process is waiting at the **Event-Based Gateway** (alongside the cancellation and 48h SLA timer). Receiving this message advances the process through the **Inclusive Gateway** merge into the `Ship Order` service task, then waits for delivery confirmation. If this message is not received within 48 hours, the process auto-escalates to a manager via a User Task.

**curl:**
```bash
curl -X POST http://localhost:8081/api/orders/ORD-001/shipment-ready \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "variables": {
      "note": "Packed and ready at Warehouse B, Bay 12"
    }
  }'
```

**Response (200 OK):**
```json
{
  "status": "SHIPMENT_READY_SENT",
  "orderId": "ORD-001"
}
```

---

### 4. Delivery Confirmation

**POST** `/api/orders/{orderId}/delivery-confirmation`

**Purpose:** This is the **courier webhook** called after a delivery attempt. Sends a `DeliveryConfirmation` message to the process with a `success` flag. The process evaluates an **Exclusive Gateway**:
- `success=true` → routes to `Send Delivery Confirmation` task → **End: Complete** (happy path)
- `success=false` → routes to `Handle Delivery Issue` **User Task**, then a manager decides to reship or refund

This is the final external trigger in the order lifecycle for successful deliveries.

**curl (successful delivery):**
```bash
curl -X POST "http://localhost:8081/api/orders/ORD-001/delivery-confirmation?success=true&note=Delivered+to+front+door"
```

**curl (failed delivery):**
```bash
curl -X POST "http://localhost:8081/api/orders/ORD-001/delivery-confirmation?success=false&note=Address+not+found"
```

**Response (200 OK):**
```json
{
  "status": "DELIVERY_CONFIRMATION_SENT",
  "orderId": "ORD-001"
}
```

---

### API Summary

| Method | Path | Description |
|---|---|---|
| POST | `/api/orders/start` | Start a new order process |
| POST | `/api/orders/{orderId}/cancel` | Cancel an in-flight order |
| POST | `/api/orders/{orderId}/shipment-ready` | Notify warehouse shipment is ready |
| POST | `/api/orders/{orderId}/delivery-confirmation` | Report delivery outcome |

### Request Parameters

**`POST /api/orders/start` — `OrderRequest` body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `orderId` | String | Yes | Unique order identifier |
| `customerId` | String | Yes | Customer identifier |
| `customerEmail` | String (email) | Yes | Customer email address |
| `customerName` | String | Yes | Customer display name |
| `items` | Array | Yes | List of order items (min 1) |
| `items[].productId` | String | Yes | Product SKU / identifier |
| `items[].productName` | String | Yes | Product display name |
| `items[].quantity` | Integer | Yes | Quantity (min 1) |
| `items[].unitPrice` | Decimal | No | Unit price |
| `shippingAddress` | String | Yes | Full shipping address |
| `paymentMethod` | String | No | `CREDIT_CARD`, `DEBIT_CARD`, `NET_BANKING` |
| `paymentToken` | String | No | Payment gateway token |

**`POST /api/orders/{orderId}/cancel` — Query params:**

| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `reason` | String | No | `Customer requested cancellation` | Cancellation reason |

**`POST /api/orders/{orderId}/delivery-confirmation` — Query params:**

| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `success` | Boolean | No | `true` | Whether delivery succeeded |
| `note` | String | No | — | Optional delivery note |

---

### End-to-End Happy Path (curl sequence)

```bash
# 1. Start order
curl -X POST http://localhost:8081/api/orders/start \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-1","customerEmail":"test@example.com","customerName":"Test User","items":[{"productId":"P1","productName":"Widget","quantity":1,"unitPrice":19.99}],"shippingAddress":"1 Test St","paymentMethod":"CREDIT_CARD","paymentToken":"tok_test"}'

# 2. Warehouse ships — notify when ready
curl -X POST http://localhost:8081/api/orders/ORD-001/shipment-ready \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","variables":{"note":"Ready at warehouse"}}'

# 3. Courier confirms delivery
curl -X POST "http://localhost:8081/api/orders/ORD-001/delivery-confirmation?success=true"
```

---

## Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP responses:

| Exception | HTTP Status | Trigger |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `@Valid` on `@RequestBody` fails |
| `ConstraintViolationException` | 400 | `@Validated` path/query param violation |
| `ClientStatusException` | 503 | Zeebe/Camunda gRPC unavailable |
| `OrderNotFoundException` | 404 | Order ID not found |
| `OrderConflictException` | 409 | Duplicate or conflicting state |
| `Exception` (catch-all) | 500 | Any unhandled runtime exception |

Error response shape:
```json
{
  "timestamp": "2026-03-18T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/orders/start",
  "errors": ["customerEmail: must be a well-formed email address"]
}
```

---

## Deprecated API: ZeebeBpmnError

`io.camunda.zeebe.spring.client.exception.ZeebeBpmnError` is **deprecated** since Camunda 8.

**Before (deprecated):**
```java
throw new ZeebeBpmnError("PAYMENT_FAILED", "Payment token missing");
```

**After (Camunda 8.8+) — use `JobClient` with `autoComplete = false`:**
```java
@JobWorker(type = "order.process-payment", autoComplete = false)
public void processPayment(JobClient client, ActivatedJob job, @Variable String token) {
    if (token == null || token.isBlank()) {
        // Throws BPMN error — caught by boundary error event in BPMN
        client.newThrowErrorCommand(job.getKey())
              .errorCode("PAYMENT_FAILED")
              .errorMessage("Payment token missing for order")
              .send()
              .join();
        return;
    }
    // Normal completion
    client.newCompleteCommand(job.getKey())
          .variables(Map.of("paymentStatus", "SUCCESS"))
          .send()
          .join();
}
```

## Camunda Web UIs

| UI | URL | Credentials |
|---|---|---|
| Operate (monitor instances) | http://localhost:8080/operate | demo / demo |
| Tasklist (user tasks) | http://localhost:8080/tasklist | demo / demo |

---