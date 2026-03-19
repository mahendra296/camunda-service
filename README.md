# Order Management Process — Camunda Service

A Spring Boot + Camunda 8 service that orchestrates the complete order lifecycle using BPMN 2.0 workflows and Zeebe job workers.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [REST API Endpoints](#rest-api-endpoints)
4. [cURL / Postman Examples](#curl--postman-examples)
5. [Complete Process Flow](#complete-process-flow)
6. [Job Workers Reference](#job-workers-reference)
7. [Exception & Error Flows](#exception--error-flows)
8. [Message Correlation](#message-correlation)
9. [Timers](#timers)
10. [Process Variables](#process-variables)
11. [End States](#end-states)

> **Per-Product Shipments:** As of the latest update, the Shipment Process is a **parallel multi-instance sub-process** that runs once per product in the order. An order with 3 products produces 3 independent shipments, each with its own tracking number, carrier assignment, pick-pack, and dispatch step. The `shipments` process variable collects the result of all product shipments when they complete.

---

## Architecture Overview

```
Client (REST)
     │
     ▼
OrderProcessController          ← REST layer, receives HTTP requests
     │
     ▼
OrderProcessService             ← Business logic, talks to Camunda/Zeebe
     │
     ▼
CamundaClient (Zeebe)          ← Starts process instances, publishes messages
     │
     ▼
BPMN Engine (Zeebe)            ← Executes order-management-process.bpmn
     │
     ▼
Job Workers (@JobWorker)       ← Picks up service tasks, executes business logic
```

Each **service task** in the BPMN has a `type` (job type). Zeebe publishes a job with that type. The matching `@JobWorker` bean picks it up, processes it, and completes (or fails) the job. This is the core integration pattern throughout the entire service.

---

## Project Structure

```
src/main/java/com/camunda/
├── controller/
│   └── OrderProcessController.java     ← HTTP entry points
├── service/
│   └── OrderProcessService.java        ← Camunda client calls
├── worker/
│   ├── ValidateOrderWorker.java        ← order.validate
│   ├── CheckInventoryWorker.java       ← order.check-inventory
│   ├── ReserveInventoryWorker.java     ← order.reserve-inventory
│   ├── ProcessPaymentWorker.java       ← order.process-payment
│   ├── HandlePaymentFailureWorker.java ← order.handle-payment-failure
│   ├── NotifyPaymentFailedWorker.java  ← order.notify-payment-failed
│   ├── SendRejectionWorker.java        ← order.send-rejection
│   ├── NotifyBackorderWorker.java      ← order.notify-backorder
│   ├── SendConfirmationWorker.java     ← order.send-confirmation
│   ├── PrepareShipmentWorker.java      ← order.prepare-shipment
│   ├── ShipOrderWorker.java            ← order.ship
│   ├── SendDeliveryConfirmationWorker  ← order.send-delivery-confirmation
│   ├── ProcessCancellationWorker.java  ← order.process-cancellation
│   ├── HandleSlaBreachWorker.java      ← order.handle-sla-breach
│   ├── ReshipOrderWorker.java          ← order.reship
│   └── ProcessRefundWorker.java        ← order.process-refund
└── order/exception/
    └── GlobalExceptionHandler.java     ← REST error responses
src/main/resources/workflow/
└── order-management-process.bpmn      ← BPMN process definition
```

---

## REST API Endpoints

All endpoints are under `/api/orders`.

### 1. Start Order Process

```
POST /api/orders/{flowId}/start
```

**Path param:** `flowId` — the BPMN process ID (e.g. `order-management-process`)

**Request body:**
```json
{
  "orderId": "ORD-001",
  "customerId": "CUST-123",
  "customerEmail": "john@example.com",
  "customerName": "John Doe",
  "shippingAddress": "123 Main St, City",
  "paymentMethod": "CREDIT_CARD",
  "paymentToken": "tok_visa_4242",
  "items": [
    { "productId": "PROD-1", "productName": "Widget A", "quantity": 1, "unitPrice": 99.99 },
    { "productId": "PROD-2", "productName": "Widget B", "quantity": 2, "unitPrice": 29.99 },
    { "productId": "PROD-3", "productName": "Widget C", "quantity": 1, "unitPrice": 49.99 }
  ]
}
```

**What it does:** Creates a new Camunda process instance. Variables are passed to Zeebe and become available to all workers throughout the lifecycle.

**To simulate payment failure:** set `paymentToken` starting with `FAIL_` (e.g. `FAIL_card_decline`)

**To simulate out-of-stock:** set any `productId` starting with `OUT_` (e.g. `OUT_PROD-999`)

---

### 2. Cancel Order

```
POST /api/orders/{orderId}/cancel?reason=Customer+requested+cancellation
```

**What it does:** Publishes the `OrderCancellation` message to Zeebe, correlated by `orderId`. The process must be waiting at the Event-Based Gateway (after payment success) for this to be received.

---

### 3. Signal Shipment Ready

```
POST /api/orders/{orderId}/shipment-ready
Content-Type: application/json

{ "variables": { "note": "All products packed", "warehouseId": "WH-CENTRAL-01" } }
```

**What it does:** Publishes the `ShipmentReady` message correlated by `orderId`. This triggers the **parallel multi-instance Shipment Process** — one subprocess instance per product in the order. Each instance independently runs Assign Carrier → Generate Label → Pick & Pack → Dispatch, producing its own tracking number.

**Optional body variables:**

| Variable | Type | Description |
|---|---|---|
| `note` | String | Warehouse packing note |
| `warehouseId` | String | Warehouse identifier (forwarded to workers) |

**Response:**
```json
{
  "status": "SHIPMENT_READY_SENT",
  "orderId": "ORD-001",
  "message": "Shipment subprocess will run in parallel for each product in the order",
  "warehouseId": "WH-CENTRAL-01"
}
```

---

### 4. Per-Product Shipment Delivery (Courier Webhook)

```
POST /api/orders/{orderId}/shipments/{trackingNumber}/delivery?success=true&note=Delivered+to+front+door
```

**Path params:**
- `orderId` — the order identifier
- `trackingNumber` — the per-product tracking number generated during the shipment subprocess (e.g. `FSX-PROD001-A3B2C1D4`)

**What it does:** Publishes the `DeliveryConfirmation` BPMN message correlated by `orderId`, with `trackingNumber` and `deliverySuccess` as variables. Call this once per product shipment as the courier confirms delivery.

**Response:**
```json
{
  "status": "DELIVERED",
  "orderId": "ORD-001",
  "trackingNumber": "FSX-PROD001-A3B2C1D4",
  "deliverySuccess": true
}
```

---

### 5. Per-Product Shipment Status Update

```
POST /api/orders/{orderId}/shipments/{trackingNumber}/status
Content-Type: application/json

{ "status": "IN_TRANSIT", "location": "Chicago Hub", "note": "Departed facility" }
```

**What it does:** Publishes a `ShipmentStatusUpdate` message with mid-transit tracking information for a specific product shipment. Use this for intermediate status milestones (IN_TRANSIT, OUT_FOR_DELIVERY, etc.). Does not advance the BPMN process — informational/audit use only.

**Request body:**

| Field | Description |
|---|---|
| `status` | e.g. `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `HELD_AT_FACILITY` |
| `location` | Physical location or hub name |
| `note` | Optional free-text note |

---

### 6. Order-Level Delivery Confirmation

```
POST /api/orders/{orderId}/delivery-confirmation?success=true&note=All+items+delivered
```

**What it does:** Publishes the `DeliveryConfirmation` message at the order level (after all product shipments are complete). Sets `deliverySuccess=true/false`, which drives the final delivery gateway — success routes to `SendDeliveryConfirmationWorker`, failure routes to the `Handle Delivery Issue` user task.

---

## cURL / Postman Examples

> Base URL: `http://localhost:8081`
> Process ID: `order-management-process`

---

### 1. Start Order — Happy Path (3 Products → 3 Shipments)

Full order with 3 products. After payment, the Shipment subprocess runs **in parallel** for each product, generating an independent tracking number per product.

```bash
curl -X POST http://localhost:8081/api/orders/order-management-process/start \
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
      {
        "productId": "PROD-001",
        "productName": "Wireless Headphones",
        "quantity": 1,
        "unitPrice": 199.99
      },
      {
        "productId": "PROD-002",
        "productName": "USB-C Cable",
        "quantity": 3,
        "unitPrice": 9.99
      },
      {
        "productId": "PROD-003",
        "productName": "Laptop Stand",
        "quantity": 1,
        "unitPrice": 49.99
      }
    ]
  }'
```

**Result:** Process starts, validates order, checks inventory, reserves stock, processes payment, then waits for the `ShipmentReady` message. After that message arrives, **3 parallel shipment subprocesses** start — one per product.

---

### 2. Start Order — Trigger Payment BPMN Error

Omitting `paymentToken` causes `ProcessPaymentWorker` to throw a BPMN error (`PAYMENT_FAILED`), which is caught by the boundary event and routed to `HandlePaymentFailureWorker`.

```bash
curl -X POST http://localhost:8081/api/orders/order-management-process/start \
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
      {
        "productId": "PROD-003",
        "productName": "Laptop Stand",
        "quantity": 1,
        "unitPrice": 49.99
      }
    ]
  }'
```

---

### 3. Start Order — Trigger Payment Declined

A token starting with `FAIL_` makes `ProcessPaymentWorker` complete normally with `paymentStatus=DECLINED`, routing to `NotifyPaymentFailedWorker`.

```bash
curl -X POST http://localhost:8081/api/orders/order-management-process/start \
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
      {
        "productId": "PROD-004",
        "productName": "Gaming Mouse",
        "quantity": 2,
        "unitPrice": 79.99
      }
    ]
  }'
```

---

### 4. Start Order — Trigger Order Validation Failure

An invalid email causes Spring validation to return HTTP 400 before reaching Camunda. Use empty items to reach the BPMN rejection path.

```bash
curl -X POST http://localhost:8081/api/orders/order-management-process/start \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-004",
    "customerId": "CUST-126",
    "customerEmail": "invalid-email",
    "customerName": "Alice Brown",
    "shippingAddress": "321 Elm St, Seattle, WA 98101",
    "paymentMethod": "CREDIT_CARD",
    "paymentToken": "tok_valid",
    "items": [
      {
        "productId": "PROD-001",
        "productName": "Widget",
        "quantity": 1,
        "unitPrice": 19.99
      }
    ]
  }'
```

> **Note:** The above uses an invalid email — Spring `@Email` validation returns HTTP 400 before reaching Camunda. To reach the BPMN-level rejection, send a valid email with an empty `items` array.

---

### 5. Start Order — Trigger Out-of-Stock / Backorder

Any `productId` starting with `OUT_` causes `CheckInventoryWorker` to set `inStock=false`, triggering the backorder loop.

```bash
curl -X POST http://localhost:8081/api/orders/order-management-process/start \
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
      {
        "productId": "OUT_PROD-999",
        "productName": "Limited Edition Sneakers",
        "quantity": 1,
        "unitPrice": 249.99
      }
    ]
  }'
```

---

### 6. Signal Shipment Ready — Triggers Per-Product Parallel Shipments

After the process is waiting at the Event-Based Gateway, signal that the warehouse has packed all products. This fires one Shipment subprocess instance per product in parallel.

```bash
curl -X POST http://localhost:8081/api/orders/ORD-001/shipment-ready \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "note": "All 3 products packed and ready for collection",
      "warehouseId": "WH-CENTRAL-01"
    }
  }'
```

**Response:**
```json
{
  "status": "SHIPMENT_READY_SENT",
  "orderId": "ORD-001",
  "message": "Shipment subprocess will run in parallel for each product in the order",
  "warehouseId": "WH-CENTRAL-01"
}
```

**What happens next:** 3 parallel subprocess instances start. Each runs:
1. `AssignCarrierWorker` — selects carrier for that product
2. `GenerateShippingLabelWorker` — creates a unique tracking number (e.g. `FSX-PROD001-A3B2C1`)
3. `PickPackWorker` — packs the specific product
4. `DispatchCarrierWorker` — hands off to carrier; writes `shipmentResult` into `shipments[]`

The `shipments` process variable ends up as:
```json
[
  { "productId": "PROD-001", "productName": "Wireless Headphones", "trackingNumber": "FSX-001-A3B2C1D4", "carrierName": "FastShip Express", "status": "DISPATCHED", ... },
  { "productId": "PROD-002", "productName": "USB-C Cable",         "trackingNumber": "FSX-002-E5F6G7H8", "carrierName": "FastShip Express", "status": "DISPATCHED", ... },
  { "productId": "PROD-003", "productName": "Laptop Stand",        "trackingNumber": "FSX-003-I9J0K1L2", "carrierName": "FastShip Express", "status": "DISPATCHED", ... }
]
```

---

### 7. Per-Product Delivery Confirmation — Product Delivered

After the shipment subprocess completes, the courier calls this endpoint for each product it delivers. `trackingNumber` is the per-product tracking number from the `shipments` array.

```bash
# Product 1 delivered
curl -X POST "http://localhost:8081/api/orders/ORD-001/shipments/FSX-001-A3B2C1D4/delivery?success=true&note=Delivered+to+front+door"

# Product 2 delivered
curl -X POST "http://localhost:8081/api/orders/ORD-001/shipments/FSX-002-E5F6G7H8/delivery?success=true&note=Left+with+neighbour"

# Product 3 delivery failed
curl -X POST "http://localhost:8081/api/orders/ORD-001/shipments/FSX-003-I9J0K1L2/delivery?success=false&note=Recipient+not+home"
```

**Response (success):**
```json
{
  "status": "DELIVERED",
  "orderId": "ORD-001",
  "trackingNumber": "FSX-001-A3B2C1D4",
  "deliverySuccess": true,
  "note": "Delivered to front door"
}
```

**Response (failure):**
```json
{
  "status": "DELIVERY_FAILED",
  "orderId": "ORD-001",
  "trackingNumber": "FSX-003-I9J0K1L2",
  "deliverySuccess": false,
  "note": "Recipient not home"
}
```

---

### 8. Per-Product Shipment Status Update (Mid-Transit)

Push intermediate tracking events for a specific product shipment. Does not advance the BPMN process — used for customer visibility and audit logs.

```bash
# Product 1 — departed warehouse
curl -X POST http://localhost:8081/api/orders/ORD-001/shipments/FSX-001-A3B2C1D4/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "IN_TRANSIT",
    "location": "Chicago Distribution Hub",
    "note": "Departed facility at 08:32"
  }'

# Product 2 — out for delivery
curl -X POST http://localhost:8081/api/orders/ORD-001/shipments/FSX-002-E5F6G7H8/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "OUT_FOR_DELIVERY",
    "location": "Springfield Local Depot",
    "note": "On delivery vehicle"
  }'
```

**Response:**
```json
{
  "status": "SHIPMENT_STATUS_UPDATED",
  "orderId": "ORD-001",
  "trackingNumber": "FSX-001-A3B2C1D4",
  "shipmentStatus": "IN_TRANSIT",
  "location": "Chicago Distribution Hub"
}
```

---

### 9. Cancel Order

Cancel an in-flight order (must be waiting at the Event-Based Gateway). Correlates by `orderId`.

```bash
curl -X POST "http://localhost:8081/api/orders/ORD-001/cancel?reason=Customer+changed+mind"
```

With URL-encoded reason:

```bash
curl -X POST http://localhost:8081/api/orders/ORD-001/cancel \
  --get \
  --data-urlencode "reason=Found a better deal elsewhere"
```

---

### 10. Order-Level Delivery Confirmation — All Shipments Done

After all per-product shipments are confirmed, send the final order-level delivery confirmation. Routes to `SendDeliveryConfirmationWorker` → `end_order_complete`.

```bash
# All items delivered successfully
curl -X POST "http://localhost:8081/api/orders/ORD-001/delivery-confirmation?success=true&note=All+3+products+delivered"

# One or more items failed delivery
curl -X POST "http://localhost:8081/api/orders/ORD-001/delivery-confirmation?success=false&note=Product+3+returned+to+depot"
```

---

### Full Happy-Path Sequence (3-Product Order)

```bash
BASE=http://localhost:8081
ORDER_ID=ORD-001

# Step 1 — Start order (3 products)
curl -X POST $BASE/api/orders/order-management-process/start \
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

# Step 2 — Signal warehouse ready (starts 3 parallel shipment subprocesses)
curl -X POST $BASE/api/orders/$ORDER_ID/shipment-ready \
  -H "Content-Type: application/json" \
  -d '{ "variables": { "note": "All packed", "warehouseId": "WH-CENTRAL-01" } }'

# Step 3 — Per-product tracking updates (optional, informational)
curl -X POST $BASE/api/orders/$ORDER_ID/shipments/FSX-001-A3B2C1D4/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "IN_TRANSIT", "location": "Chicago Hub" }'

# Step 4 — Per-product delivery confirmations (courier webhooks)
curl -X POST "$BASE/api/orders/$ORDER_ID/shipments/FSX-001-A3B2C1D4/delivery?success=true&note=Delivered"
curl -X POST "$BASE/api/orders/$ORDER_ID/shipments/FSX-002-E5F6G7H8/delivery?success=true&note=Left+with+neighbour"
curl -X POST "$BASE/api/orders/$ORDER_ID/shipments/FSX-003-I9J0K1L2/delivery?success=true"

# Step 5 — Final order-level delivery confirmation
curl -X POST "$BASE/api/orders/$ORDER_ID/delivery-confirmation?success=true&note=All+products+delivered"
```

---

### Scenario Cheat Sheet

| Scenario | Key Variable / Token | Expected End State |
|---|---|---|
| Happy path (1 product) | `paymentToken: "tok_visa_4242"` | `end_order_complete` |
| Happy path (3 products) | 3 items in `items[]` | 3 shipment subprocesses run in parallel → `end_order_complete` |
| Payment BPMN error | `paymentToken: ""` (blank/null) | `end_payment_error` |
| Payment declined | `paymentToken: "FAIL_..."` | `end_payment_declined` |
| Out-of-stock loop | `productId: "OUT_..."` | Waits at restock timer, then retries |
| Customer cancels | POST `/{orderId}/cancel` before shipment | `end_order_cancelled` |
| SLA breach | Process waits 48h at Event-Based GW | Manager escalation → continues to ship |
| Shipping SLA | Ship subprocess runs > 72h | `end_sla_notified` (non-interrupting — subprocess continues) |
| Per-product delivery failed | `/{orderId}/shipments/{tracking}/delivery?success=false` | Sets `deliverySuccess=false` in variables |
| All delivery failed → reship | `/delivery-confirmation?success=false`, support sets `resolution=RESHIP` | `end_reshipped` |
| All delivery failed → refund | `/delivery-confirmation?success=false`, support sets `resolution=REFUND` | `end_refunded` |

---

## Complete Process Flow

```
[Start: Order Received]
        │
        ▼
[Validate Order]  ── job: order.validate ──► ValidateOrderWorker
        │
        ▼
   <Order Valid?>
   ├── NO  ──► [Send Rejection]  ── job: order.send-rejection ──► SendRejectionWorker
   │                │
   │                ▼
   │         [END: Order Rejected] (throws BPMN error: ORDER_REJECTED)
   │
   └── YES ──► [Check Inventory]  ── job: order.check-inventory ──► CheckInventoryWorker
                    │
                    ▼
               <In Stock?>
               ├── NO  ──► [Notify Backorder]  ── job: order.notify-backorder ──► NotifyBackorderWorker
               │                   │
               │                   ▼
               │           [Wait for Restock] (timer: PT1M / configurable)
               │                   │
               │                   ▼
               │           [Check Inventory]  ◄──────────────────────────────┐
               │            (re-runs worker, fresh inStock value)             │
               │                   │                                          │
               │                   └── inStock=false ── [Notify Backorder] ──┘
               │                         (loop continues until inStock=true)
               └── YES ──► [Reserve Inventory]  ── job: order.reserve-inventory ──► ReserveInventoryWorker
                                   │
                                   ▼
                           [Process Payment]  ── job: order.process-payment ──► ProcessPaymentWorker
                                   │
                    ┌──────────────┴──────────────┐
                    │ (BPMN Boundary Error Event)  │
                    │ errorCode: PAYMENT_FAILED    │ (payment throws BPMN error)
                    ▼                             ▼
          <Payment Successful?>     [Handle Payment Failure] ── job: order.handle-payment-failure
          ├── DECLINED ──► [Notify Payment Declined]          ──► HandlePaymentFailureWorker
          │                  job: order.notify-payment-failed      │
          │                  ──► NotifyPaymentFailedWorker          ▼
          │                  │                              [END: Payment Error]
          │                  ▼
          │         [END: Order Payment Declined]
          │
          └── SUCCESS ──► [Parallel Split Gateway]
                               │              │
                    ┌──────────┘              └──────────┐
                    ▼                                    ▼
          [Prepare Shipment]                  [Send Order Confirmation]
          job: order.prepare-shipment         job: order.send-confirmation
          ──► PrepareShipmentWorker           ──► SendConfirmationWorker
                    │                                    │
                    └──────────────┬─────────────────────┘
                                   ▼
                          [Parallel Join Gateway]  ← waits for BOTH branches
                                   │
                                   ▼
                       [Event-Based Gateway: Await Event]
                        ┌──────────┼──────────────┐
                        │          │               │
                        ▼          ▼               ▼
              [Customer        [SLA Breach      [Shipment
               Cancels]         Timer 48h]       Ready Msg]
                  │                 │                 │
                  ▼                 ▼                 │
        [Process Cancellation] [Escalate to          │
         job: order.process-    Manager]              │
         cancellation          (User Task)            │
         ──► ProcessCancellation    │                 │
              Worker               └────────────►────┘
                  │                          │
                  ▼                          ▼
           [END: Order          [Inclusive Merge Gateway]
            Cancelled]                  │
                                        ▼
                                  [Ship Order]  ── job: order.ship ──► ShipOrderWorker
                                        │
                              ┌─────────┘ (non-interrupting boundary)
                              │ Shipping SLA Timer (72h)
                              ▼
                   [Handle SLA Breach]  ── job: order.handle-sla-breach ──► HandleSlaBreachWorker
                              │
                              ▼
                      [END: SLA Escalation Notified]  ← process CONTINUES shipping
                              │
                      [Delivery Confirmed Msg]  ← waits for courier webhook
                              │
                              ▼
                       <Delivered Successfully?>
                       ├── YES ──► [Send Delivery Confirmation]
                       │           job: order.send-delivery-confirmation
                       │           ──► SendDeliveryConfirmationWorker
                       │                    │
                       │                    ▼
                       │           [END: Order Completed Successfully]
                       │
                       └── NO  ──► [Handle Delivery Issue] (User Task — support-agent)
                                            │
                                            ▼
                                    <Reship or Refund?>
                                    ├── RESHIP ──► [Reship Order]
                                    │              job: order.reship
                                    │              ──► ReshipOrderWorker
                                    │                      │
                                    │                      ▼
                                    │              [END: Order Reshipped]
                                    │
                                    └── REFUND ──► [Process Refund]
                                                   job: order.process-refund
                                                   ──► ProcessRefundWorker
                                                           │
                                                           ▼
                                                   [END: Order Refunded]
```

---

## Job Workers Reference

### Order-Level Workers

| Job Type | Worker Class | Triggered By | Key Logic | Output Variables |
|---|---|---|---|---|
| `order.validate` | `ValidateOrderWorker` | Start of process | Checks items not empty, email valid; calculates total | `orderValid`, `totalAmount`, `validationError` |
| `order.check-inventory` | `CheckInventoryWorker` | After validation (valid) | Products with `OUT_` prefix = out of stock | `inStock`, `outOfStockItems` |
| `order.reserve-inventory` | `ReserveInventoryWorker` | After stock confirmed | Reserves stock; generates reservation ID | `inventoryReservationId` |
| `order.process-payment` | `ProcessPaymentWorker` | After inventory reserved | Token starting with `FAIL_` throws BPMN error; otherwise sets status | `paymentStatus` (`SUCCESS`/`DECLINED`/`ERROR`) |
| `order.handle-payment-failure` | `HandlePaymentFailureWorker` | BPMN error boundary on payment | Releases inventory reservation; notifies customer | — |
| `order.notify-payment-failed` | `NotifyPaymentFailedWorker` | `paymentStatus = DECLINED` | Sends payment decline email | — |
| `order.send-rejection` | `SendRejectionWorker` | `orderValid = false` | Sends rejection email with reason | — |
| `order.notify-backorder` | `NotifyBackorderWorker` | `inStock = false` | Notifies customer of backorder; sets wait | — |
| `order.send-confirmation` | `SendConfirmationWorker` | Payment success (parallel) | Sends order confirmation email | — |
| `order.prepare-shipment` | `PrepareShipmentWorker` | Payment success (parallel) | Generates initial tracking number, warehouse ID | `trackingNumber`, `warehouseId`, `estimatedDelivery` |
| `order.send-delivery-confirmation` | `SendDeliveryConfirmationWorker` | `deliverySuccess = true` | Sends delivery confirmation + review request | — |
| `order.process-cancellation` | `ProcessCancellationWorker` | `OrderCancellation` message received | Processes refund; releases inventory; generates refund ID | `refundId` |
| `order.handle-sla-breach` | `HandleSlaBreachWorker` | Shipping SLA timer (72h, non-interrupting) | Alerts ops team; escalates to carrier | — |
| `order.reship` | `ReshipOrderWorker` | `resolution != REFUND` after delivery failure | Creates reship with new tracking number | `reshipTrackingNumber` |
| `order.process-refund` | `ProcessRefundWorker` | `resolution = REFUND` after delivery failure | Processes refund through payment gateway | `refundTransactionId` |

### Shipment Subprocess Workers (run once per product — parallel multi-instance)

These workers run **inside the multi-instance Shipment subprocess**. Each receives `currentItem` (the product map from `items[]`) and operates on a single product at a time.

| Job Type | Worker Class | Key Logic | Input from `currentItem` | Output Variables |
|---|---|---|---|---|
| `shipment.assign-carrier` | `AssignCarrierWorker` | Selects carrier: EXPRESS for orders > $500, STANDARD otherwise. Logs `productId`. | `productId` | `carrierId`, `carrierName`, `carrierCode`, `serviceLevel` |
| `shipment.generate-label` | `GenerateShippingLabelWorker` | Generates a **product-scoped tracking number** (`{carrierCode}-{productId}-{random}`). Calculates estimated delivery by service level. | `productId` | `trackingNumber`, `labelId`, `labelUrl`, `estimatedDeliveryMs` |
| `shipment.pick-pack` | `PickPackWorker` | Picks and packs the specific product using `currentItem.quantity`. Generates a product-scoped `packageId`. | `productId`, `quantity` | `packageId`, `itemCount`, `totalQuantity`, `packageStatus` |
| `shipment.dispatch` | `DispatchCarrierWorker` | Hands the package to the carrier. Builds a `shipmentResult` map (all shipment details for this product). This is **collected into the `shipments[]` array** by the multi-instance loop. | `productId`, `productName` | `dispatchReference`, `trackingUrl`, `shipmentStatus`, `shipmentResult` |

**Retry configuration:**
- All workers: `retries="3"` (except `order.process-payment` which has `retries="1"`)
- On exception: worker calls `client.newFailCommand()` with `retries - 1`

**Multi-instance loop variables:**

| Variable | Direction | Description |
|---|---|---|
| `items` | Input collection | The order's product list — one subprocess instance per element |
| `currentItem` | Input element | The product map available inside each subprocess instance (`productId`, `productName`, `quantity`, `unitPrice`) |
| `shipmentResult` | Output element | Set by `DispatchCarrierWorker` — the per-product shipment record |
| `shipments` | Output collection | Accumulated after all instances complete — array of all `shipmentResult` maps |

---

## Exception & Error Flows

### 1. BPMN Error — Payment Exception (System Error)

**When:** `ProcessPaymentWorker` throws a `ZeebeBpmnError` with error code `PAYMENT_FAILED`
**How to trigger:** Set `paymentToken` starting with `FAIL_` in the order request
**Handler:** Boundary Error Event on `task_process_payment` → `HandlePaymentFailureWorker`

```
ProcessPaymentWorker
  └── throws ZeebeBpmnError("PAYMENT_FAILED")
        │
        ▼
  [Boundary Error Event] catches PAYMENT_FAILED
        │
        ▼
  HandlePaymentFailureWorker  (job: order.handle-payment-failure)
    - releases inventory reservation
    - sends customer notification
        │
        ▼
  [END: Payment Error End]
```

### 2. Gateway Path — Payment Declined (Business Logic)

**When:** `ProcessPaymentWorker` completes normally but sets `paymentStatus = "DECLINED"`
**How to trigger:** Normal decline by payment gateway (non-exception path)
**Condition:** `= paymentStatus = "DECLINED"` on sequence flow

```
ProcessPaymentWorker
  └── completes with paymentStatus = "DECLINED"
        │
        ▼
  <Payment Successful?> gateway
        │  condition: paymentStatus = "DECLINED"
        ▼
  NotifyPaymentFailedWorker  (job: order.notify-payment-failed)
    - sends decline notification email
        │
        ▼
  [END: Order Payment Declined]
```

### 3. BPMN Error — Order Rejected End Event

**When:** `SendRejectionWorker` completes and process reaches `end_order_rejected`
**Note:** The end event throws error code `ORDER_REJECTED` (catchable by a parent process if used as a sub-process)

### 4. SLA Breach — Non-Interrupting Boundary Timer

**When:** `task_ship_order` runs longer than 72 hours
**Important:** This is a **non-interrupting** boundary event — the Ship Order task continues running

```
ShipOrderWorker (running...)
  │
  └── [Non-Interrupting 72h Timer fires]
            │
            ▼
      HandleSlaBreachWorker  (job: order.handle-sla-breach)
        - alerts ops team
        - escalates to carrier
            │
            ▼
      [END: SLA Escalation Notified]
      (the main ShipOrder flow continues in parallel)
```

### 5. Delivery Failure — Manual Resolution

**When:** `deliverySuccess = false` in the delivery confirmation message
**Handler:** User task assigned to `support-agent` role

```
DeliveryConfirmation message received (deliverySuccess=false)
        │
        ▼
  <Delivered Successfully?> gateway → NO
        │
        ▼
  [Handle Delivery Issue] — User Task (assignee: support-agent)
    Support agent sets variable: resolution = "RESHIP" or "REFUND"
        │
        ▼
  <Reship or Refund?> gateway
  ├── default (RESHIP) ──► ReshipOrderWorker  (job: order.reship)
  └── resolution = "REFUND" ──► ProcessRefundWorker  (job: order.process-refund)
```

### 6. Out-of-Stock — Backorder Loop

**When:** `inStock = false` from `CheckInventoryWorker`

> **Important:** The timer routes back to `CheckInventoryWorker`, **not** directly to the gateway.
> Routing to the gateway would skip the worker and leave `inStock` permanently `false`, causing an infinite loop.

```
CheckInventoryWorker  sets inStock = false
        │
        ▼
  <In Stock?> gateway → NO
        │
        ▼
  NotifyBackorderWorker  (job: order.notify-backorder)
    - notifies customer of backorder status
        │
        ▼
  [Wait for Restock] — timer (PT1M for dev / PT24H for prod)
        │
        ▼
  CheckInventoryWorker  ← worker runs again, sets fresh inStock value
        │
        ▼
  <In Stock?> gateway
  ├── true  → proceed to Reserve Inventory
  └── false → loop back through Notify Backorder → Timer
```

### 7. REST Exception Handling

`GlobalExceptionHandler` maps exceptions to HTTP responses:

| Exception | HTTP Status | Scenario |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Bean validation failed on request body |
| `ConstraintViolationException` | 400 | Constraint violation on path/query param |
| `OrderNotFoundException` | 404 | Order ID not found |
| `OrderConflictException` | 409 | Duplicate order or conflict |
| `ClientStatusException` | 503 | Zeebe/Camunda connectivity failure |
| `Exception` (catch-all) | 500 | Unexpected server error |

---

## Message Correlation

All messages correlate by `orderId`. Published via REST endpoints.

| Message Name | Published By | Correlates When | Sets Variables |
|---|---|---|---|
| `OrderCancellation` | `POST /{orderId}/cancel` | Waiting at Event-Based Gateway | `cancellationReason` |
| `ShipmentReady` | `POST /{orderId}/shipment-ready` | Waiting at Event-Based Gateway | `shipmentNote`, `warehouseId`, `shipmentReadyAt` |
| `DeliveryConfirmation` | `POST /{orderId}/shipments/{tracking}/delivery` or `/{orderId}/delivery-confirmation` | Waiting after shipment subprocess | `deliverySuccess`, `trackingNumber`, `deliveryNote`, `deliveredAt` |
| `ShipmentStatusUpdate` | `POST /{orderId}/shipments/{tracking}/status` | Informational — no BPMN catch event | `trackingNumber`, `shipmentStatus`, `shipmentLocation`, `statusNote` |

> **Important:** `OrderCancellation` and `ShipmentReady` are mutually exclusive at the Event-Based Gateway. Whichever arrives first wins. The 48h SLA timer is also competing — if it fires first, the manager escalation path is taken before shipment begins.

> **Per-product note:** `ShipmentReady` fires **once** but starts N parallel subprocess instances (one per product in `items[]`). Each instance receives `currentItem` (the product being shipped) and independently runs Assign Carrier → Generate Label → Pick & Pack → Dispatch.

---

## Timers

| Timer | Location | Duration | Type | Effect |
|---|---|---|---|---|
| Wait for Restock | Intermediate Catch Event (backorder loop) | PT1M (dev) / PT24H (prod) | Interrupting | Process pauses, then routes back to **CheckInventoryWorker** for a fresh stock check |
| SLA Breach — Pre-shipping | Intermediate Catch Event at Event-Based Gateway | PT48H (48 hours) | Event-based (competing) | Triggers manager escalation if no shipment ready |
| Shipping SLA | Boundary Event on `task_ship_order` | PT72H (72 hours) | Non-interrupting boundary | Fires SLA alert without cancelling ship task |

---

## Process Variables

### Input Variables (set at process start)

| Variable | Type | Description |
|---|---|---|
| `orderId` | String | Unique order identifier — used for message correlation |
| `customerId` | String | Customer identifier |
| `customerEmail` | String | Customer email address |
| `customerName` | String | Customer full name |
| `shippingAddress` | String | Delivery address |
| `paymentMethod` | String | e.g. `CREDIT_CARD`, `PAYPAL` |
| `paymentToken` | String | Payment gateway token |
| `items` | List | Array of `{productId, productName, quantity, unitPrice}` |

### Variables Set During Process Execution

| Variable | Set By | Description |
|---|---|---|
| `orderValid` | `ValidateOrderWorker` | `true/false` — drives order valid gateway |
| `totalAmount` | `ValidateOrderWorker` | Calculated order total |
| `validationError` | `ValidateOrderWorker` | Reason if invalid |
| `inStock` | `CheckInventoryWorker` | `true/false` — drives inventory gateway |
| `outOfStockItems` | `CheckInventoryWorker` | List of out-of-stock product IDs |
| `inventoryReservationId` | `ReserveInventoryWorker` | Reservation reference |
| `paymentStatus` | `ProcessPaymentWorker` | `SUCCESS`, `DECLINED`, or `ERROR` |
| `trackingNumber` | `PrepareShipmentWorker` | Initial order-level tracking number (pre-shipment) |
| `warehouseId` | `PrepareShipmentWorker` / `ShipmentReady` msg | Assigned warehouse |
| `estimatedDelivery` | `PrepareShipmentWorker` | Estimated delivery timestamp |
| `refundId` | `ProcessCancellationWorker` | Cancellation refund ID |
| `reshipTrackingNumber` | `ReshipOrderWorker` | New tracking number for reship |
| `refundTransactionId` | `ProcessRefundWorker` | Refund transaction reference |
| `cancellationReason` | Published via cancel API | Customer cancellation reason |
| `deliverySuccess` | Published via delivery API | `true/false` — drives delivery gateway |
| `resolution` | Set by support-agent user task | `RESHIP` or `REFUND` |

### Per-Product Shipment Variables (multi-instance scope)

| Variable | Scope | Description |
|---|---|---|
| `currentItem` | Inside subprocess instance | The product being shipped in this iteration: `{ productId, productName, quantity, unitPrice }` |
| `carrierId` | Inside subprocess instance | Carrier ID assigned to this product |
| `carrierName` | Inside subprocess instance | e.g. `FastShip Express`, `QuickDeliver Co` |
| `carrierCode` | Inside subprocess instance | e.g. `FSX`, `QDC` |
| `serviceLevel` | Inside subprocess instance | `STANDARD`, `EXPRESS`, or `ECONOMY` |
| `trackingNumber` | Inside subprocess instance | Per-product tracking number, e.g. `FSX-PROD001-A3B2C1D4` |
| `labelId` | Inside subprocess instance | Shipping label identifier |
| `labelUrl` | Inside subprocess instance | PDF label URL |
| `packageId` | Inside subprocess instance | Pick-pack package identifier |
| `dispatchReference` | Inside subprocess instance | Carrier dispatch reference (`DISP-{trackingNumber}`) |
| `shipmentResult` | Output of each instance | Map collected after dispatch: `{ productId, productName, trackingNumber, packageId, carrierId, carrierName, carrierCode, serviceLevel, dispatchReference, trackingUrl, labelUrl, status, dispatchedAt }` |
| `shipments` | Process-level (after loop) | Array of all `shipmentResult` maps — one entry per product |

---

## End States

| End State | BPMN Element | Reached When |
|---|---|---|
| Order Rejected | `end_order_rejected` | Validation fails (`orderValid=false`) — throws error `ORDER_REJECTED` |
| Payment Error End | `end_payment_error` | BPMN error `PAYMENT_FAILED` caught from payment task |
| Order Payment Declined | `end_payment_declined` | Payment gateway declines (`paymentStatus=DECLINED`) |
| Order Cancelled | `end_order_cancelled` | Customer sends cancellation message before shipment |
| SLA Escalation Notified | `end_sla_notified` | Shipping SLA timer fires (main process continues) |
| Order Completed Successfully | `end_order_complete` | Delivery confirmed successfully |
| Order Reshipped | `end_reshipped` | Delivery failed → support chose reship |
| Order Refunded | `end_refunded` | Delivery failed → support chose refund |
