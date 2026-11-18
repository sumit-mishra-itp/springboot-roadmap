package dev.roadmap.coreioc.injection;

import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Primary // wins when an unqualified NotificationSender is requested
@Order(1) // position when injected as List<NotificationSender>
public class EmailSender implements NotificationSender {
    @Override
    public String send(String message) {
        return "email:" + message;
    }
}
