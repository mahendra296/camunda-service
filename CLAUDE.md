# Camunda Service — Coding Instructions

## Package Structure

Maintain the following package layout under `src/main/java/com/camunda/`:

```
com.camunda/
├── CamundaServiceApplication.java   # Spring Boot entry point
├── config/                          # Spring & Camunda configurations
├── controller/                      # REST endpoints (thin, no business logic)
├── service/                         # Business logic and orchestration
├── worker/                          # Camunda job workers (one class per task type)
├── dto/                             # Request/response data transfer objects
├── exceptions/                      # Domain-specific exceptions and global handler
└── (future) model/                  # Domain models if needed
```

New classes must go into the correct package. Do not place workers in service, DTOs in model, or exceptions outside `order/exception`.

## Code Style — Spotless

This project uses the **Spotless** plugin for code formatting. Before committing:

```bash
# Check formatting
mvn spotless:check

# Apply formatting
mvn spotless:apply
```

Never submit code that fails `spotless:check`. Run `spotless:apply` after any code generation or refactoring.

## Dependency Injection

- Constructor injection exclusively — never field injection (`@Autowired` on fields)
- Use Lombok `@RequiredArgsConstructor(onConstructor = @__(@Autowired))`
- All injected dependencies must be `private final` fields

```java
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class OrderProcessService {
    private final ZeebeClient zeebeClient;
    private final OrderRepository orderRepository;
}
```

## Functional Programming Style

- Use the Stream API over imperative loops
- Prefer method references over lambdas where possible
- Use `Optional` for nullable return values — never return `null`
- Use `var` for local variables where the type is obvious

```java
var confirmedItems = includeAll
        ? items.stream().toList()
        : items.stream()
                .filter(OrderItemDto::isConfirmed)
                .toList();
```

```java
// Method reference over lambda
items.stream()
        .map(OrderItemDto::getProductId)
        .filter(Objects::nonNull)
        .toList();
```

```java
// Optional for nullable values
public Optional<Order> findById(String id) {
    return Optional.ofNullable(orderRepository.get(id));
}
```


Update the readme.md file for new changes and also update the postman curl if new changes occurred.