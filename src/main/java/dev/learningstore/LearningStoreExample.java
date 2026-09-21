package dev.learningstore;

public final class LearningStoreExample {
    private LearningStoreExample() {}

    public static void main(String[] args) {
        String learnerEmail = environment("LEARNER_EMAIL");
        String operationsEmail = environment("OPERATIONS_EMAIL");

        CommerceGateway gateway = new InfraiCommerceGateway(InfraiConfig.fromEnvironment());
        CheckoutService checkout = new CheckoutService(gateway, operationsEmail);
        checkout.prepareContinuity(10.0, 50.0);

        CourseOrder order = new CourseOrder("course-order-1042", learnerEmail,
                "Practical Geometry", 1);
        CheckoutService.CheckoutResult result = checkout.checkoutAndFulfill(order, true);
        System.out.printf("order=%s state=%s infraiBalance=%.2f messageIds=%s%n",
                result.orderId(), result.state(), result.infrastructureBalance(), result.messageIds());
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
