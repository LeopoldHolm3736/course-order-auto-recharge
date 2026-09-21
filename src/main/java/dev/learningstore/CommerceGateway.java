package dev.learningstore;

public interface CommerceGateway {
    void configureAutoRecharge(double triggerBalance, double rechargeAmount, String idempotencyKey);
    double accountBalance();
    String sendEmail(String to, String subject, String text, String idempotencyKey);
}
