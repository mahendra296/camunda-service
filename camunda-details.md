# Camunda 8 — Complete Guide

## What is Camunda 8?

Camunda 8 is a cloud-native, horizontally scalable **Business Process Management (BPM)** platform built around the **Zeebe** workflow engine. It enables developers and business analysts to model, automate, and monitor business processes using the **BPMN 2.0** (Business Process Model and Notation) standard.

### Key Differences from Camunda 7

| Feature | Camunda 7 | Camunda 8 |
|---|---|---|
| Engine | Java-based (embedded) | Zeebe (distributed, cloud-native) |
| Deployment | Embedded / on-prem | SaaS / Self-managed |
| Database | RDBMS (MySQL, PostgreSQL) | RocksDB (event-sourced) |
| Scalability | Vertical | Horizontal |
| REST API | Yes | Yes (v2) |
| gRPC | No | Yes |

---

## Camunda 8 Architecture Components

```
┌─────────────────────────────────────────────────────────────┐
│                        Camunda 8                            │
│                                                             │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │ Modeler │  │ Operate  │  │ Tasklist │  │ Optimize   │  │
│  └────┬────┘  └────┬─────┘  └────┬─────┘  └─────┬──────┘  │
│       │            │              │               │         │
│  ┌────▼────────────▼──────────────▼───────────────▼──────┐  │
│  │                    Zeebe Gateway                       │  │
│  └────────────────────────┬───────────────────────────────┘  │
│                           │                                  │
│  ┌────────────────────────▼───────────────────────────────┐  │
│  │              Zeebe Brokers (Partitioned)                │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

- **Zeebe** — Core workflow engine (distributed, event-sourced)
- **Modeler** — BPMN/DMN diagram editor (Desktop & Web)
- **Operate** — Process monitoring and incident management UI
- **Tasklist** — Human task management UI
- **Optimize** — Process analytics and reporting
- **Connectors** — Pre-built integrations (REST, Kafka, AWS, etc.)

---

## BPMN 2.0 Elements in Camunda 8

---

## 1. Events

Events represent something that **happens** during a process. Every process must start and end with an event.

> **Shape Rule:**
> - **Start Event** → thin single-border circle
> - **Intermediate Event** → thin double-border circle
> - **End Event** → thick/bold single-border circle
>
> The **icon inside** the circle tells you the event type (message, timer, signal, error, etc.)

---

### 1.1 Start Events

Mark the **beginning** of a process instance.

---

#### None Start Event (Default)

![None Start Event](images/bpmn/event-start-none.svg)

```xml
<startEvent id="StartEvent_1" name="Process Started" />
```
- Triggered manually or via API
- Used in most standard processes

---

#### Message Start Event

![Message Start Event](images/bpmn/event-start-message.svg)

```xml
<startEvent id="StartEvent_Message">
  <messageEventDefinition messageRef="Message_OrderReceived" />
</startEvent>
```
- Triggered when a specific message is received
- Example: Start an order process when a Kafka message arrives

---

#### Timer Start Event

![Timer Start Event](images/bpmn/event-start-timer.svg)

```xml
<startEvent id="StartEvent_Timer">
  <timerEventDefinition>
    <timeCycle>R/PT1H</timeCycle>  <!-- Every 1 hour -->
  </timerEventDefinition>
</startEvent>
```
- Triggered at a specific time or interval
- Example: Run a nightly report process at 2:00 AM

---

#### Signal Start Event

![Signal Start Event](images/bpmn/event-start-signal.svg)

- Triggered by a broadcast signal (can start multiple processes simultaneously)
- The triangle icon inside the circle represents the signal

---

### 1.2 Intermediate Events

Occur **between** start and end events.

---

#### Intermediate Timer Catch Event

![Intermediate Timer Event](images/bpmn/event-intermediate-timer.svg)

```xml
<intermediateCatchEvent id="Timer_Wait">
  <timerEventDefinition>
    <timeDuration>PT30M</timeDuration>  <!-- Wait 30 minutes -->
  </timerEventDefinition>
</intermediateCatchEvent>
```
- Pauses the process for a defined duration
- Example: Wait 30 minutes before sending a follow-up email

---

#### Intermediate Message Catch Event

![Intermediate Message Catch](images/bpmn/event-intermediate-message-catch.svg)

```xml
<intermediateCatchEvent id="Message_WaitForPayment">
  <messageEventDefinition messageRef="Message_PaymentConfirmed" />
</intermediateCatchEvent>
```
- Pauses the process until a specific message arrives
- Envelope icon is **outlined** (catch = receiving)
- Example: Wait for payment confirmation from a payment gateway

---

#### Intermediate Message Throw Event

![Intermediate Message Throw](images/bpmn/event-intermediate-message-throw.svg)

```xml
<intermediateThrowEvent id="Message_NotifyWarehouse">
  <messageEventDefinition messageRef="Message_ShipOrder" />
</intermediateThrowEvent>
```
- Sends a message to another process or system
- Envelope icon is **filled/dark** (throw = sending)
- Example: Notify the warehouse system to prepare shipment

---

#### Boundary Events

![Boundary Timer Event](images/bpmn/event-boundary-timer.svg)

Attached to a task — trigger when a condition occurs **while the task is active**.

```xml
<!-- Timer Boundary Event — escalate if task takes too long -->
<boundaryEvent id="Boundary_Escalate" attachedToRef="Task_ReviewOrder">
  <timerEventDefinition>
    <timeDuration>P1D</timeDuration>  <!-- 1 day -->
  </timerEventDefinition>
</boundaryEvent>
```
- **Interrupting** (solid border) — cancels the task and takes the boundary path
- **Non-interrupting** (dashed border) — runs in parallel without canceling the task

---

### 1.3 End Events

Mark the **end** of a process or path.

---

#### None End Event

![None End Event](images/bpmn/event-end-none.svg)

```xml
<endEvent id="EndEvent_1" name="Process Complete" />
```
- Simply ends the current path

---

#### Message End Event

![Message End Event](images/bpmn/event-end-message.svg)

```xml
<endEvent id="EndEvent_Notify">
  <messageEventDefinition messageRef="Message_SendConfirmation" />
</endEvent>
```
- Sends a message when the process ends
- Example: Send an order confirmation email

---

#### Error End Event

![Error End Event](images/bpmn/event-end-error.svg)

```xml
<endEvent id="EndEvent_Error">
  <errorEventDefinition errorRef="Error_PaymentFailed" />
</endEvent>
```
- Throws a BPMN error to be caught by an error boundary event
- Lightning bolt icon represents the error
- Example: Indicate payment failure to trigger compensation

---

#### Terminate End Event

![Terminate End Event](images/bpmn/event-end-terminate.svg)

```xml
<endEvent id="EndEvent_Terminate">
  <terminateEventDefinition />
</endEvent>
```
- Terminates **all active paths** in the process (not just the current one)
- Filled inner circle distinguishes it from a plain end event
- Example: Cancel all parallel tasks when an order is cancelled

---

## 2. Tasks

Tasks represent **work** to be performed. They are the core building blocks of a process.

> **Shape Rule:** All tasks are **rounded rectangles**.
> The **icon in the top-left corner** indicates the task type.

---

### 2.1 Service Task

![Service Task](images/bpmn/task-service.svg)

Work done **automatically** by an external worker or connector.

```xml
<serviceTask id="Task_SendEmail"
             name="Send Confirmation Email"
             zeebe:type="send-email-worker" />
```

**Java Worker Example:**
```java
@JobWorker(type = "send-email-worker")
public void sendEmail(final JobClient client, final ActivatedJob job) {
    Map<String, Object> variables = job.getVariablesAsMap();
    String email = (String) variables.get("customerEmail");
    String orderId = (String) variables.get("orderId");

    // Send email logic
    emailService.send(email, "Order " + orderId + " confirmed!");

    client.newCompleteCommand(job.getKey()).send().join();
}
```

**Use Cases:** HTTP calls, database updates, email sending, calculations

---

### 2.2 User Task

![User Task](images/bpmn/task-user.svg)

Work assigned to a **human user**, visible in Tasklist.

```xml
<userTask id="Task_ReviewOrder"
          name="Review Order"
          zeebe:assignee="= orderReviewer"
          zeebe:candidateGroups="= reviewTeam" />
```

**Key Attributes:**
- `assignee` — specific user assigned to the task
- `candidateGroups` — group of users who can claim the task
- `dueDate` — deadline for task completion
- `formKey` — embedded form for user input

---

### 2.3 Script Task

![Script Task](images/bpmn/task-script.svg)

Executes a **FEEL script** inline.

```xml
<scriptTask id="Task_Calculate"
            name="Calculate Total"
            scriptFormat="feel">
  <script>
    { totalAmount: sum(items.price), taxAmount: sum(items.price) * 0.18 }
  </script>
</scriptTask>
```

- Uses **FEEL** (Friendly Enough Expression Language)
- Result is merged into process variables
- Example: Calculate order total from line items

---

### 2.4 Business Rule Task

![Business Rule Task](images/bpmn/task-business-rule.svg)

Evaluates a **DMN Decision Table**.

```xml
<businessRuleTask id="Task_CheckCredit"
                  name="Check Credit Score"
                  zeebe:calledDecisionId="CreditScoreDecision" />
```

**DMN Table Example:**

| Credit Score | Income | Output: Approved |
|---|---|---|
| >= 750 | any | true |
| 600-749 | > 50000 | true |
| < 600 | any | false |

---

### 2.5 Send Task

![Send Task](images/bpmn/task-send.svg)

Sends a **message** to another process or system.

```xml
<sendTask id="Task_SendInvoice"
          name="Send Invoice"
          zeebe:type="send-invoice-worker" />
```

- Functionally similar to a Service Task in Camunda 8
- Semantically indicates the act of sending a message
- Filled envelope icon in the top-left corner

---

### 2.6 Receive Task

![Receive Task](images/bpmn/task-receive.svg)

**Waits** for a message to arrive.

```xml
<receiveTask id="Task_WaitPayment"
             name="Wait for Payment"
             messageRef="Message_PaymentReceived" />
```

**Sending a message via API:**
```java
zeebeClient.newPublishMessageCommand()
    .messageName("Message_PaymentReceived")
    .correlationKey(orderId)
    .variables(Map.of("paymentStatus", "SUCCESS"))
    .send()
    .join();
```

- Outlined envelope icon (as opposed to filled in Send Task)

---

### 2.7 Call Activity

![Call Activity](images/bpmn/task-call-activity.svg)

**Calls a sub-process** (another BPMN process) by its process ID.

```xml
<callActivity id="Task_ProcessPayment"
              name="Process Payment"
              zeebe:processId="payment-process">
  <extensionElements>
    <zeebe:input source="= orderId" target="orderId" />
    <zeebe:input source="= amount" target="paymentAmount" />
    <zeebe:output source="= paymentId" target="paymentId" />
  </extensionElements>
</callActivity>
```

- **Thick border** + `[+]` marker at the bottom distinguishes it
- Reusable sub-processes
- Example: Reuse a "payment" process across order and subscription processes

---

### 2.8 Sub-Process (Embedded)

![Sub-Process](images/bpmn/task-subprocess.svg)

Groups tasks **within the same process** for better organization.

```xml
<subProcess id="SubProcess_OrderFulfillment" name="Order Fulfillment">
  <startEvent id="Start_Sub" />
  <serviceTask id="Task_PickItems" ... />
  <serviceTask id="Task_PackOrder" ... />
  <endEvent id="End_Sub" />
</subProcess>
```

- **Thin border** + `[+]` marker (click to expand in Modeler)
- Can have its own boundary events
- Useful for grouping related steps with error handling

---

## 3. Gateways

Gateways control the **flow** of the process — splitting and merging paths.

> **Shape Rule:** All gateways are **diamonds**.
> The **symbol inside** the diamond indicates the routing logic.

---

### 3.1 Exclusive Gateway (XOR)

![Exclusive Gateway](images/bpmn/gateway-exclusive.svg)

Only **one** outgoing path is taken based on a condition.

```xml
<exclusiveGateway id="Gateway_CheckStock" name="In Stock?" />

<sequenceFlow id="Flow_Yes"
              sourceRef="Gateway_CheckStock"
              targetRef="Task_PackOrder">
  <conditionExpression>= stockLevel > 0</conditionExpression>
</sequenceFlow>

<sequenceFlow id="Flow_No"
              sourceRef="Gateway_CheckStock"
              targetRef="Task_BackOrder"
              zeebe:defaultFlow="true" />
```

- **X** symbol inside the diamond
- Example flow:
```
Order Received → [In Stock? ✕] → YES → Pack Order → Ship
                               → NO  → Back Order → Notify Customer
```

---

### 3.2 Parallel Gateway (AND)

![Parallel Gateway](images/bpmn/gateway-parallel.svg)

**All** outgoing paths are taken simultaneously (fork), and waits for **all** paths to complete (join).

```xml
<!-- Split: all paths activate simultaneously -->
<parallelGateway id="Gateway_Split" name="Process in Parallel" />

<!-- Join: waits for all incoming paths to complete -->
<parallelGateway id="Gateway_Join" name="All Done" />
```

- **+** symbol inside the diamond
- Example flow:
```
Order Placed → [Fork +] → Send Confirmation Email ─┐
                       → Update Inventory          ─┤
                       → Reserve Shipping Slot     ─┘→ [Join +] → Continue
```

---

### 3.3 Inclusive Gateway (OR)

![Inclusive Gateway](images/bpmn/gateway-inclusive.svg)

**One or more** outgoing paths are taken based on conditions. Waits for all active paths at join.

```xml
<inclusiveGateway id="Gateway_Notifications" name="Send Notifications" />

<sequenceFlow id="Flow_Email">
  <conditionExpression>= customer.emailOptIn = true</conditionExpression>
</sequenceFlow>

<sequenceFlow id="Flow_SMS">
  <conditionExpression>= customer.smsOptIn = true</conditionExpression>
</sequenceFlow>
```

- **○** circle symbol inside the diamond
- Example: Send email AND/OR SMS AND/OR push based on user preferences

---

### 3.4 Event-Based Gateway

![Event-Based Gateway](images/bpmn/gateway-event-based.svg)

Waits for **one of several events** to occur — whichever happens first wins.

```xml
<eventBasedGateway id="Gateway_WaitForResponse" name="Wait for Response" />

<intermediateCatchEvent id="Event_PaymentReceived">
  <messageEventDefinition messageRef="Message_Payment" />
</intermediateCatchEvent>

<intermediateCatchEvent id="Event_Timeout">
  <timerEventDefinition>
    <timeDuration>PT24H</timeDuration>
  </timerEventDefinition>
</intermediateCatchEvent>
```

- **Double circle + pentagon** inside the diamond
- Example flow:
```
[Wait for Response ⬡] → Payment Received → Continue with Order
                      → 24h Timeout      → Send Reminder → Cancel
```

---

## 4. Sequence Flows & Conditions

Connections between elements, optionally with conditions.

```xml
<sequenceFlow id="Flow_Approve"
              sourceRef="Gateway_Decision"
              targetRef="Task_ProcessOrder">
  <conditionExpression xsi:type="tFormalExpression">
    = decision = "approved" and orderTotal &lt; 10000
  </conditionExpression>
</sequenceFlow>
```

**FEEL Expression Examples:**
```feel
= status = "ACTIVE"
= amount > 1000 and currency = "USD"
= list contains(roles, "admin")
= date("2026-01-01") > today()
```

---

## 5. Lanes & Pools

### Pool
Represents a **participant** (organization or system) in a collaboration.

### Lane
Represents a **role or department** within a pool — for visual organization.

```
┌─────────────────────────────────────────┐
│                 Order Process            │
│  ┌──────────────────────────────────┐   │
│  │  Customer        [Place Order]   │   │
│  ├──────────────────────────────────┤   │
│  │  Sales Team      [Review Order]  │   │
│  ├──────────────────────────────────┤   │
│  │  Warehouse       [Pack & Ship]   │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 6. Data Objects & Variables

### Process Variables

Variables are the **data** that flows through a process instance.

```java
// Starting a process with variables
zeebeClient.newCreateInstanceCommand()
    .bpmnProcessId("order-process")
    .latestVersion()
    .variables(Map.of(
        "orderId", "ORD-12345",
        "customerId", "CUST-001",
        "items", List.of(
            Map.of("sku", "ITEM-A", "qty", 2, "price", 29.99),
            Map.of("sku", "ITEM-B", "qty", 1, "price", 49.99)
        ),
        "totalAmount", 109.97
    ))
    .send()
    .join();
```

### Variable Mapping in Call Activities

```xml
<zeebe:ioMapping>
  <zeebe:input source="= orderId" target="orderId" />
  <zeebe:input source="= totalAmount" target="paymentAmount" />
  <zeebe:output source="= transactionId" target="paymentTransactionId" />
</zeebe:ioMapping>
```

---

## 7. Error Handling

### BPMN Errors

Used for **expected business errors** (not technical failures).

```xml
<serviceTask id="Task_ValidatePayment" zeebe:type="validate-payment" />

<boundaryEvent id="Error_PaymentFailed" attachedToRef="Task_ValidatePayment">
  <errorEventDefinition errorRef="Error_InsufficientFunds" />
</boundaryEvent>
```

```java
// Throwing a BPMN error from a worker
@JobWorker(type = "validate-payment")
public void validatePayment(JobClient client, ActivatedJob job) {
    if (paymentFailed) {
        client.newThrowErrorCommand(job.getKey())
            .errorCode("INSUFFICIENT_FUNDS")
            .errorMessage("Payment declined: insufficient funds")
            .send()
            .join();
    }
}
```

### Incidents

**Unexpected failures** create incidents in Operate:
- Job worker throws an unhandled exception
- Variable mapping fails
- Expression evaluation fails

Incidents can be **retried** or **resolved** via the Operate UI or API.

---

## 8. Compensation

Used to **undo** completed work when something goes wrong.

```xml
<serviceTask id="Task_ChargeCard" name="Charge Card" isForCompensation="false" />

<!-- Compensation handler -->
<serviceTask id="Task_RefundCard" name="Refund Card" isForCompensation="true" />

<association id="Assoc_1"
             sourceRef="Task_ChargeCard"
             targetRef="Task_RefundCard" />

<!-- Trigger compensation -->
<endEvent id="EndEvent_Compensate">
  <compensateEventDefinition />
</endEvent>
```

---

## 9. Message Correlation

Messages link events and tasks across different process instances.

```java
zeebeClient.newPublishMessageCommand()
    .messageName("Message_PaymentReceived")
    .correlationKey("ORD-12345")  // Must match the process variable
    .variables(Map.of(
        "paymentId", "PAY-789",
        "paymentStatus", "SUCCESS"
    ))
    .timeToLive(Duration.ofHours(1))
    .send()
    .join();
```

**In the BPMN:**
```xml
<intermediateCatchEvent id="Event_WaitPayment">
  <messageEventDefinition messageRef="Message_PaymentReceived">
    <zeebe:subscription correlationKey="= orderId" />
  </messageEventDefinition>
</intermediateCatchEvent>
```

---

## 10. Timers

### Timer Formats (ISO 8601)

| Type | Format | Example |
|---|---|---|
| Duration | `PTnHnMnS` | `PT30M` = 30 minutes |
| Date | `YYYY-MM-DDTHH:MM:SSZ` | `2026-12-01T09:00:00Z` |
| Cycle | `R[n]/PT...` | `R3/PT1H` = 3 times, hourly |
| Infinite Cycle | `R/PT...` | `R/P1D` = daily, forever |

**FEEL Expression for Dynamic Timer:**
```feel
= date and time(dueDate) + duration("PT24H")
```

---

## Complete Example: Order Processing Process

```
[Order Received] (Message Start)
       |
[Validate Order] (Service Task — validate-order-worker)
       |
[Order Valid?] (Exclusive Gateway ✕)
    |        |
   YES       NO
    |         \→ [Notify Invalid] → [End: Invalid]
    |
[Process in Parallel] (Parallel Gateway + — Fork)
    |           |
[Check Stock]  [Check Credit]
(Service Task) (Business Rule Task — DMN)
    |           |
[All Done] (Parallel Gateway + — Join)
    |
[Both Checks Passed?] (Exclusive Gateway ✕)
    |            |
   YES           NO → [Cancel Order] → [End: Cancelled]
    |
[Reserve Items] (Service Task)
    |
[Charge Payment] (Service Task)
    |
[Wait for Response ⬡] (Event-Based Gateway)
    |                   |
[Payment Received]  [24h Timeout]
(Message Catch)     (Timer Catch)
    |                   |
[Ship Order]        [Send Reminder]
    |                   |
[End: Complete]     [Cancel Order]
                        |
                   [End: Cancelled]
```

---

## Camunda 8 REST API Quick Reference

```bash
# Deploy a process
POST /v2/deployments
Content-Type: multipart/form-data
Body: resources=[bpmn file]

# Start a process instance
POST /v2/process-instances
{
  "processDefinitionKey": 123456,
  "variables": { "orderId": "ORD-001" }
}

# Complete a job
POST /v2/jobs/{jobKey}/completion
{
  "variables": { "result": "success" }
}

# Publish a message
POST /v2/messages/publication
{
  "messageName": "Message_PaymentReceived",
  "correlationKey": "ORD-001",
  "variables": { "paymentId": "PAY-123" }
}
```

---

## FEEL Expression Language Cheatsheet

```feel
# Arithmetic
= amount * 1.18
= sum([10, 20, 30])           -- 60

# String
= string length(name) > 5
= upper case(status) = "ACTIVE"

# Date/Time
= today() + duration("P7D")   -- 7 days from now
= day of week(dueDate) = 1    -- Monday

# List
= count(items) > 0
= some item in items satisfies item.price > 100
= [item.name for item in items]

# Conditional
= if score >= 700 then "approved" else "rejected"
```

---

## Best Practices

1. **Keep processes simple** — aim for processes that fit on one screen; use Call Activities for complex sub-flows
2. **Use meaningful names** — name elements as actions: "Send Confirmation Email", not "Task 1"
3. **Handle all paths** — always have a default flow on Exclusive Gateways
4. **Map variables explicitly** — use input/output mappings in Call Activities to avoid variable pollution
5. **Set job retries** — configure retry counts and backoff for Service Tasks
6. **Use boundary events** — add timer boundary events to User Tasks for SLA enforcement
7. **Idempotent workers** — design Job Workers to be safely re-executable (jobs can retry)
8. **Version your processes** — use semantic versioning in process IDs (`order-process-v2`)
9. **Monitor with Operate** — set up alerts for incidents and SLA breaches
10. **Test with process tests** — use `camunda-process-test` library for integration testing
