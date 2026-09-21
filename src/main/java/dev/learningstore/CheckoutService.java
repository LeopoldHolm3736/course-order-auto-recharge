package dev.learningstore;

import java.util.ArrayList;
import java.util.List;

public final class CheckoutService {
    public record CheckoutResult(String orderId, String state, double infrastructureBalance,
                                 List<String> messageIds) {}

    private final CommerceGateway gateway;
    private final String operationsEmail;

    public CheckoutService(CommerceGateway gateway, String operationsEmail) {
        this.gateway = gateway;
        this.operationsEmail = operationsEmail;
    }

    public void prepareContinuity(double triggerBalance, double rechargeAmount) {
        gateway.configureAutoRecharge(triggerBalance, rechargeAmount,
                "course-store-autorecharge-v1");
    }

    public CheckoutResult checkoutAndFulfill(CourseOrder order, boolean rechargeFired) {
        double balance = gateway.accountBalance();
        List<String> messages = new ArrayList<>();

        // Payment is already accepted at this boundary; enrollment is the fulfillment step.
        String state = "ENROLLED";
        messages.add(gateway.sendEmail(order.learnerEmail(),
                "Receipt for " + order.courseTitle(),
                "Order " + order.orderId() + " is paid. Seats: " + order.seats() + ".",
                order.orderId() + "-receipt"));
        messages.add(gateway.sendEmail(order.learnerEmail(),
                "Your course access is ready",
                "Order " + order.orderId() + " is ENROLLED. You can begin the course now.",
                order.orderId() + "-status-enrolled"));

        if (rechargeFired) {
            messages.add(gateway.sendEmail(operationsEmail,
                    "Infrai balance recharged",
                    "Automatic recharge fired while fulfilling order " + order.orderId() + ".",
                    order.orderId() + "-recharge-notice"));
        }
        return new CheckoutResult(order.orderId(), state, balance, List.copyOf(messages));
    }
}
