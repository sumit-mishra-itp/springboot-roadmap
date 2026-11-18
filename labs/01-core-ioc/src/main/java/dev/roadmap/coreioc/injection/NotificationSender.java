package dev.roadmap.coreioc.injection;

/** Two implementations exist, so the container needs help (@Primary / @Qualifier) to pick one. */
public interface NotificationSender {
    String send(String message);
}
