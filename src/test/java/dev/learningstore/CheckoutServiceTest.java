package dev.learningstore;

import java.util.ArrayList;
import java.util.List;

public final class CheckoutServiceTest {
    public static void main(String[] args) {
        FakeGateway gateway = new FakeGateway();
        CheckoutService service = new CheckoutService(gateway, "course-ops@example.com");
        service.prepareContinuity(10.0, 50.0);
        var result = service.checkoutAndFulfill(
                new CourseOrder("order-7", "learner@example.com", "Number Sense", 1), true);

        require(gateway.configured, "automatic recharge should be configured");
        require("ENROLLED".equals(result.state()), "paid order should be fulfilled");
        require(result.messageIds().size() == 3, "receipt, order update, and recharge notice should be sent");
        require(gateway.recipients.equals(List.of(
                "learner@example.com", "learner@example.com", "course-ops@example.com")),
                "recharge notice should go to course operations");
        System.out.println("PASS checkout enrolls the learner and sends all three messages");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements CommerceGateway {
        boolean configured;
        final List<String> recipients = new ArrayList<>();

        public void configureAutoRecharge(double triggerBalance, double rechargeAmount, String idempotencyKey) {
            configured = triggerBalance == 10.0 && rechargeAmount == 50.0
                    && idempotencyKey.equals("course-store-autorecharge-v1");
        }
        public double accountBalance() { return 37.5; }
        public String sendEmail(String to, String subject, String text, String idempotencyKey) {
            recipients.add(to);
            return "message-" + recipients.size();
        }
    }
}
