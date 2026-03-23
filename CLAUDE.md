# Claude Code Instructions — Camunda Service

> These instructions apply to **every request** in this project. Read and follow all sections before generating or modifying any code.

---

## 1. Project Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.3 |
| Process Engine | Camunda 8 / Zeebe (`camunda-spring-boot-starter` 8.8.14) |
| Persistence | Spring Data JPA + PostgreSQL + Liquibase |
| Code Formatter | Spotless (Palantir Java Format 2.89.0) |
| Utilities | Lombok 1.18.38 |
| Build | Maven |

---

## 2. Package Structure

All new classes **must** go into the correct package under `src/main/java/com/camunda/`:

```
com.camunda/
├── CamundaServiceApplication.java        # Spring Boot entry point — do not modify
├── config/                               # Spring & Camunda configuration beans
├── controller/                           # REST endpoints (thin — no business logic)
├── service/                              # Business logic and process orchestration
├── worker/                               # Camunda job workers (one class per job type)
├── dto/                                  # Request / response data transfer objects
├── exceptions/                           # Domain exceptions + GlobalExceptionHandler
└── model/                                # JPA entities and domain models (if needed)
```

**Hard rules:**
- Workers → `worker/` only. Never place a worker in `service/`.
- DTOs → `dto/` only. Never place DTOs in `model/`.
- Exceptions → `exceptions/` only.
- Configs → `config/` only.

---

## 3. Code Style — Spotless (Mandatory)

This project uses **Spotless** with Palantir Java Format. Every generated or modified Java file must pass formatting.

```bash
# Apply formatting after any code change
mvn spotless:apply

# Verify before committing
mvn spotless:check
```

**Never produce code that fails `spotless:check`.** Always run `spotless:apply` after code generation or refactoring.

Spotless rules enforced:
- Palantir Java Format 2.89.0 (PALANTIR style)
- Unused imports removed
- Trailing whitespace trimmed
- File ends with newline

---

## 4. Dependency Injection

**Constructor injection only.** Never use field injection (`@Autowired` on fields).

```java
// ✅ CORRECT
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class OrderProcessService {
    private final ZeebeClient zeebeClient;
    private final OrderRepository orderRepository;
}

// ❌ WRONG — never do this
@Component
public class OrderProcessService {
    @Autowired
    private ZeebeClient zeebeClient;
}
```

All injected dependencies must be `private final`.

---

## 5. Functional Programming Style

### Stream API over imperative loops

```java
// ✅ CORRECT
var confirmedItems = includeAll
        ? items.stream().toList()
        : items.stream()
                .filter(OrderItemDto::isConfirmed)
                .toList();

// ❌ WRONG
List<OrderItemDto> confirmedItems = new ArrayList<>();
for (OrderItemDto item : items) {
    if (item.isConfirmed()) confirmedItems.add(item);
}
```

### Method references over lambdas

```java
// ✅ CORRECT
items.stream()
        .map(OrderItemDto::getProductId)
        .filter(Objects::nonNull)
        .toList();

// ❌ WRONG
items.stream()
        .map(item -> item.getProductId())
        .filter(id -> id != null)
        .toList();
```

### Optional for nullable return values

```java
// ✅ CORRECT
public Optional<Order> findById(String id) {
    return Optional.ofNullable(orderRepository.get(id));
}

// ❌ WRONG — never return null
public Order findById(String id) {
    return orderRepository.get(id); // may return null
}
```

### var for local variables

Use `var` when the type is obvious from context.

```java
var processInstance = zeebeClient.newCreateInstanceCommand()
        .bpmnProcessId("order-management-process")
        .latestVersion()
        .variables(variables)
        .send()
        .join();
```

---

## 6. Job Worker Pattern

Each job worker handles **one Zeebe job type**. Follow this template exactly:

```java
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ValidateOrderWorker {

    @JobWorker(type = "order.validate", autoComplete = false)
    public void handle(final JobClient client, final ActivatedJob job) {
        log.info("Executing job type={} key={}", job.getType(), job.getKey());

        var variables = job.getVariablesAsMap();
        // ... business logic ...

        client.newCompleteCommand(job.getKey())
                .variables(Map.of("orderValid", true))
                .send()
                .join();
    }
}
```

**Rules:**
- One worker class per job type.
- Always use `autoComplete = false` — complete or fail the job explicitly.
- Log `type` and `key` at entry.
- Throw `ZeebeBpmnError` for BPMN boundary error events (e.g. `PAYMENT_FAILED`).
- Catch unexpected exceptions and call `client.newFailCommand(job.getKey())` with a meaningful message and remaining retries.

---

## 7. Controller Pattern

Controllers are thin — no business logic.

```java
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class OrderProcessController {

    private final OrderProcessService orderProcessService;

    @PostMapping("/{processId}/start")
    public ResponseEntity<StartOrderResponse> startOrder(
            @PathVariable String processId,
            @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderProcessService.startProcess(processId, request));
    }
}
```

---

## 8. Exception Handling

All domain exceptions live in `exceptions/`. Extend `RuntimeException`. Register them in `GlobalExceptionHandler`.

```java
// exceptions/OrderNotFoundException.java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}

// exceptions/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
```

---

## 9. BPMN Diagram Standards

When creating or modifying `.bpmn` files in `src/main/resources/workflow/`, follow these conventions.

### Element ID Naming

| Element Type | Pattern | Example |
|---|---|---|
| Start Event | `start_event_<name>` | `start_event_order_received` |
| End Event | `end_<outcome>` | `end_order_rejected` |
| Service Task | `task_<action>` | `task_validate_order` |
| User Task | `task_<action>` | `task_approve_shipment` |
| Gateway (exclusive) | `gw_<decision>` | `gw_order_valid` |
| Gateway (parallel) | `gw_parallel_<split|join>` | `gw_parallel_split` |
| Gateway (event-based) | `gw_event_based` | `gw_event_based` |
| Sequence Flow | `flow_<from>_to_<to>` | `flow_validate_to_gw_valid` |
| Catch Event | `catch_<trigger>` | `catch_cancellation` |
| Boundary Event | `boundary_<type>` | `boundary_payment_error` |
| Subprocess | `subprocess_<name>` | `subprocess_shipment` |
| Timer | `timer_<name>` | `timer_wait_restock` |

### Job Type Naming

Zeebe job types follow `<domain>.<action>` kebab-case:

```xml
<zeebe:taskDefinition type="order.validate" retries="3" />
<zeebe:taskDefinition type="shipment.assign-carrier" retries="3" />
<zeebe:taskDefinition type="airtel.checkCustomerExists" retries="3" />
```

### Message Naming

Messages use PascalCase. Correlation key must be set:

```xml
<bpmn:message id="Message_OrderCancellation" name="OrderCancellation">
  <bpmn:extensionElements>
    <zeebe:subscription correlationKey="=orderId" />
  </bpmn:extensionElements>
</bpmn:message>
```

### User Task Assignments

```xml
<bpmn:userTask id="task_approve_shipment" name="Approve Shipment">
  <bpmn:extensionElements>
    <zeebe:assignmentDefinition assignee="warehouse-supervisor" />
    <zeebe:formDefinition formKey="shipment-approval-form" />
  </bpmn:extensionElements>
</bpmn:userTask>
```

### Multi-instance Subprocess

```xml
<bpmn:multiInstanceLoopCharacteristics>
  <bpmn:extensionElements>
    <zeebe:loopCharacteristics
      inputCollection="=items"
      inputElement="currentItem"
      outputCollection="shipments"
      outputElement="=shipmentResult" />
  </bpmn:extensionElements>
</bpmn:multiInstanceLoopCharacteristics>
```

### BPMN Diagram Layout (DI)

- All shapes must have a corresponding `bpmndi:BPMNShape` with `dc:Bounds`.
- All flows must have a corresponding `bpmndi:BPMNEdge` with `di:waypoint` entries.
- Labels must have `bpmndi:BPMNLabel` with `dc:Bounds` positioned near the element.
- Boundary events: place `dc:Bounds` overlapping the host task border.
- Non-interrupting boundary events: set `cancelActivity="false"`.

---

## 10. README Update Rules

**Update `README.md` automatically whenever any of the following change:**

| Change | README Section to Update |
|---|---|
| New REST endpoint added | `REST API Endpoints` — add new cURL example |
| Existing endpoint modified | `REST API Endpoints` — update the affected cURL |
| New job worker added | `Job Workers Reference` — add row to the correct table |
| Worker output variables changed | `Job Workers Reference` — update `Key Output Variables` column |
| New BPMN process added | `Project Structure`, `Architecture Overview`, `Complete Process Flow` |
| Process flow changed | `Complete Process Flow` — update the ASCII diagram |
| New DTO added | `Project Structure` |
| New controller or service added | `Project Structure` |
| New Postman variable needed | Update the variable list at the top of `REST API Endpoints` |

### README cURL Format Rules

- Always use Postman collection variable syntax: `{{baseURL}}`, `{{orderId}}`, `{{taskKey}}`, etc.
- Every cURL must include `-H "Content-Type: application/json"` when sending a body.
- Add a `>` comment line before each cURL explaining what scenario it triggers.
- Follow the existing section structure: scenario heading → comment → cURL block.

```markdown
### Endpoint Title

> Brief description of what this triggers in the BPMN process.

\`\`\`bash
curl -X POST {{baseURL}}/api/path \
  -H "Content-Type: application/json" \
  -d '{
    "field": "value"
  }'
\`\`\`
```

---

## 11. Postman / cURL Update Rules

When a new endpoint is added or an existing one changes:

1. Add or update the cURL in the matching section of `README.md`.
2. If a new Postman variable is needed (e.g. `{{loanId}}`), add it to the variable list at the top of the `REST API Endpoints` section.
3. For message-based endpoints, note the correlation key used and what BPMN element it targets.
4. For user task endpoints, include a note: `> Use <variable_name> from Camunda Operate as {{taskKey}}`.

---

## 12. Checklist — Before Finishing Any Task

Run through this checklist before completing every request:

- [ ] New class is in the correct package (`worker/`, `service/`, `controller/`, `dto/`, `exceptions/`, `config/`, `model/`)
- [ ] Constructor injection used (`@RequiredArgsConstructor`, `private final`)
- [ ] No field-level `@Autowired`
- [ ] Streams / method references used instead of imperative loops where applicable
- [ ] `Optional` used for nullable returns — no `null` returned
- [ ] `mvn spotless:apply` would pass on all modified files
- [ ] If a new worker was added → `Job Workers Reference` table in `README.md` updated
- [ ] If a new endpoint was added → `REST API Endpoints` section in `README.md` updated with cURL
- [ ] If process flow changed → `Complete Process Flow` diagram in `README.md` updated
- [ ] If a new Postman variable is required → variable list in `README.md` updated
- [ ] BPMN element IDs follow naming conventions in Section 9
- [ ] BPMN diagram (DI) has shapes and edges for all new elements

---

## 13. Active Processes

| Process ID | BPMN File | Correlation Key |
|---|---|---|
| `order-management-process` | `order-management-process.bpmn` | `orderId` |
| `airtel-loan-capbpm-process` | `airtel-loan-process.bpmn` | `msisdn` |

---

## 14. Postman Variables Reference

| Variable | Value |
|---|---|
| `{{baseURL}}` | `http://localhost:8081` |
| `{{orderId}}` | e.g. `ORD-001` |
| `{{productId}}` | e.g. `PROD-001` |
| `{{taskKey}}` | Job key from Camunda Operate |
| `{{trackingNumber}}` | Tracking number from worker output |
| `{{msisdn}}` | e.g. `254700123456` |