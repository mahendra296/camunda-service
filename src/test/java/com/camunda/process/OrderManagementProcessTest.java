package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class OrderManagementProcessTest {

    private static final String PROCESS_ID = "order-management-process";

    /**
     * task_approve_shipment, task_handle_delivery_issue and task_initiate_refund are plain
     * bpmn:userTask elements with no <zeebe:userTask/> marker, so - unlike task_approve_cancellation,
     * which has the marker - they are classic job-based user tasks (job type
     * "io.camunda.zeebe:userTask", see UserTaskInterceptorWorker) rather than native Zeebe user
     * tasks, and must be completed via the job API, not completeUserTask/assertThatUserTask.
     */
    private static final String JOB_BASED_USER_TASK = "io.camunda.zeebe:userTask";

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/order-management-process.bpmn")
                .send()
                .join();
    }

    private ProcessInstanceEvent startOrder(String orderId, List<Map<String, Object>> items) {
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of("orderId", orderId, "items", items))
                .send()
                .join();
    }

    private void publishMessage(String messageName, String correlationKey) {
        client.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .send()
                .join();
    }

    private void publishMessage(String messageName, String correlationKey, Map<String, Object> variables) {
        client.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variables)
                .send()
                .join();
    }

    /** Drives a single-item order through payment, shipment and delivery confirmation. */
    private ProcessInstanceEvent runToDeliveryConfirmation(String orderId, boolean deliverySuccess) {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "APPROVED"));
        processTestContext.mockJobWorker("order.prepare-shipment").thenComplete();
        processTestContext.mockJobWorker("order.send-confirmation").thenComplete();
        processTestContext.mockJobWorker("order.notify-shipment-approval").thenComplete();
        processTestContext.mockJobWorker("shipment.assign-carrier").thenComplete();
        processTestContext.mockJobWorker("shipment.generate-label").thenComplete();
        processTestContext.mockJobWorker("shipment.pick-pack").thenComplete();
        processTestContext.mockJobWorker("shipment.dispatch").thenComplete();

        var instance = startOrder(orderId, List.of(Map.of("productId", "P1")));

        assertThat(instance).isWaitingForMessage("ShipmentReady");
        publishMessage("ShipmentReady", orderId);

        assertThat(instance).isWaitingForMessage("ItemShipmentReady");
        publishMessage("ItemShipmentReady", orderId + "_P1");

        assertThat(instance).hasActiveElements("task_approve_shipment");
        processTestContext.completeJob(JOB_BASED_USER_TASK);

        assertThat(instance).isWaitingForMessage("DeliveryConfirmation");
        publishMessage("DeliveryConfirmation", orderId, Map.of("deliverySuccess", deliverySuccess));

        return instance;
    }

    @Test
    void invalidOrder_isRejected() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", false));
        processTestContext.mockJobWorker("order.send-rejection").thenComplete();

        var instance = startOrder("ORD-INVALID", List.of());

        // end_order_rejected is modeled as an error end event (Error_OrderRejected), but this
        // process is always started as a root process with nothing to catch that error - so
        // reaching it raises an UNHANDLED_ERROR_EVENT incident instead of completing. This is a
        // pre-existing bug in order-management-process.bpmn; this test documents current behavior.
        assertThat(instance).hasCompletedElements("task_send_rejection").hasActiveIncidents();
    }

    @Test
    void outOfStock_notifiesBackorderThenTimerEndsBranch() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", false));
        processTestContext.mockJobWorker("order.notify-backorder").thenComplete();

        var instance = startOrder("ORD-BACKORDER", List.of());
        assertThat(instance).hasActiveElements("timer_wait_restock");

        processTestContext.increaseTime(Duration.ofMinutes(2));

        // timer_wait_restock has no outgoing flow - that branch, and the whole instance, ends there.
        assertThat(instance).isCompleted().hasCompletedElements("task_notify_backorder", "timer_wait_restock");
    }

    @Test
    void paymentBoundaryError_handlesFailureAndEnds() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenThrowBpmnError("PAYMENT_FAILED");
        processTestContext.mockJobWorker("order.handle-payment-failure").thenComplete();

        var instance = startOrder("ORD-PAY-ERR", List.of());

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("task_handle_payment_failure", "end_payment_error");
    }

    @Test
    void paymentDeclined_endsAsDeclined() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "DECLINED"));
        processTestContext.mockJobWorker("order.notify-payment-failed").thenComplete();

        var instance = startOrder("ORD-DECLINED", List.of());

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("task_notify_payment_failed", "end_payment_declined");
    }

    @Test
    void customerCancels_eventBasedGatewayRoutesToCancellation() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "APPROVED"));
        processTestContext.mockJobWorker("order.prepare-shipment").thenComplete();
        processTestContext.mockJobWorker("order.send-confirmation").thenComplete();
        processTestContext.mockJobWorker("order.process-cancellation").thenComplete();

        String orderId = "ORD-CANCEL";
        var instance = startOrder(orderId, List.of());

        assertThat(instance).isWaitingForMessage("OrderCancellation");
        publishMessage("OrderCancellation", orderId);

        processTestContext.completeUserTask("task_approve_cancellation");

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("task_process_cancellation", "end_order_cancelled")
                .hasNotActivatedElements("catch_shipment_ready", "catch_sla_breach");
    }

    @Test
    void slaBreachTimer_eventBasedGatewayRoutesToSlaHandling() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "APPROVED"));
        processTestContext.mockJobWorker("order.prepare-shipment").thenComplete();
        processTestContext.mockJobWorker("order.send-confirmation").thenComplete();
        processTestContext.mockJobWorker("order.handle-sla-breach").thenComplete();

        var instance = startOrder("ORD-SLA", List.of());
        assertThat(instance).hasActiveElements("gw_event_based");

        processTestContext.increaseTime(Duration.ofHours(49));

        assertThat(instance).isCompleted().hasCompletedElementsInOrder("task_handle_sla_breach", "end_sla_notified");
    }

    @Test
    void happyPath_singleItemShipsAndDeliversSuccessfully() {
        var instance = runToDeliveryConfirmation("ORD-HAPPY", true);

        processTestContext.mockJobWorker("order.send-delivery-confirmation").thenComplete();

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("ship_end", "task_send_delivery_confirmation", "end_order_complete");
    }

    @Test
    void deliveryIssue_reshipResolutionReshipsOrder() {
        var instance = runToDeliveryConfirmation("ORD-RESHIP", false);

        processTestContext.mockJobWorker("order.reship").thenComplete();
        assertThat(instance).hasActiveElements("task_handle_delivery_issue");
        processTestContext.completeJob(JOB_BASED_USER_TASK, Map.of("resolution", "RESHIP"));

        assertThat(instance).isCompleted().hasCompletedElementsInOrder("task_reship_order", "end_reshipped");
    }

    @Test
    void deliveryIssue_refundResolutionRefundsOrder() {
        var instance = runToDeliveryConfirmation("ORD-REFUND", false);

        processTestContext.mockJobWorker("order.process-refund").thenComplete();
        assertThat(instance).hasActiveElements("task_handle_delivery_issue");
        processTestContext.completeJob(JOB_BASED_USER_TASK, Map.of("resolution", "REFUND"));

        assertThat(instance).hasActiveElements("task_initiate_refund");
        processTestContext.completeJob(JOB_BASED_USER_TASK);

        assertThat(instance).isCompleted().hasCompletedElementsInOrder("task_process_refund", "end_refunded");
    }

    @Test
    void multiItemShipment_eachItemShipsIndependently() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "APPROVED"));
        processTestContext.mockJobWorker("order.prepare-shipment").thenComplete();
        processTestContext.mockJobWorker("order.send-confirmation").thenComplete();
        processTestContext.mockJobWorker("order.notify-shipment-approval").thenComplete();
        processTestContext.mockJobWorker("shipment.assign-carrier").thenComplete();
        processTestContext.mockJobWorker("shipment.generate-label").thenComplete();
        processTestContext.mockJobWorker("shipment.pick-pack").thenComplete();
        processTestContext.mockJobWorker("shipment.dispatch").thenComplete();

        String orderId = "ORD-MULTI";
        var instance = startOrder(orderId, List.of(Map.of("productId", "P1"), Map.of("productId", "P2")));

        assertThat(instance).isWaitingForMessage("ShipmentReady");
        publishMessage("ShipmentReady", orderId);

        publishMessage("ItemShipmentReady", orderId + "_P1");
        publishMessage("ItemShipmentReady", orderId + "_P2");

        assertThat(instance).hasActiveElement("task_approve_shipment", 2);
        processTestContext.completeJob(JOB_BASED_USER_TASK);
        processTestContext.completeJob(JOB_BASED_USER_TASK);

        assertThat(instance).hasCompletedElement("ship_end", 2).isWaitingForMessage("DeliveryConfirmation");
    }

    @Test
    void nonInterruptingShippingSlaBoundary_firesWhileSubprocessStillRunning() {
        processTestContext.mockJobWorker("order.validate").thenComplete(Map.of("orderValid", true));
        processTestContext.mockJobWorker("order.check-inventory").thenComplete(Map.of("inStock", true));
        processTestContext.mockJobWorker("order.reserve-inventory").thenComplete();
        processTestContext.mockJobWorker("order.process-payment").thenComplete(Map.of("paymentStatus", "APPROVED"));
        processTestContext.mockJobWorker("order.prepare-shipment").thenComplete();
        processTestContext.mockJobWorker("order.send-confirmation").thenComplete();
        processTestContext.mockJobWorker("order.notify-shipment-approval").thenComplete();
        processTestContext.mockJobWorker("order.handle-sla-breach").thenComplete();

        String orderId = "ORD-SHIP-SLA";
        var instance = startOrder(orderId, List.of(Map.of("productId", "P1")));

        assertThat(instance).isWaitingForMessage("ShipmentReady");
        publishMessage("ShipmentReady", orderId);
        assertThat(instance).isWaitingForMessage("ItemShipmentReady");
        publishMessage("ItemShipmentReady", orderId + "_P1");

        // task_approve_shipment is deliberately left uncompleted so the subprocess is still
        // running when the non-interrupting 72h shipping SLA boundary fires.
        assertThat(instance).hasActiveElements("task_approve_shipment");
        processTestContext.increaseTime(Duration.ofHours(73));

        assertThat(instance)
                .isActive()
                .hasCompletedElementsInOrder("task_handle_sla_breach", "end_sla_notified")
                .hasActiveElements("task_approve_shipment");
    }
}
