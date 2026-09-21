package dev.learningstore;

public record CourseOrder(String orderId, String learnerEmail, String courseTitle, int seats) {
    public CourseOrder {
        if (orderId.isBlank() || learnerEmail.isBlank() || courseTitle.isBlank() || seats < 1) {
            throw new IllegalArgumentException("A course order needs an id, learner, course, and at least one seat");
        }
    }
}
