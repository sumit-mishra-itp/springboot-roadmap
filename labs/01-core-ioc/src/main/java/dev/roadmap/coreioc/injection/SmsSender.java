package dev.roadmap.coreioc.injection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Qualifier("sms")
@Order(2)
public class SmsSender implements NotificationSender {
    @Override
    public String send(String message) {
        return "sms:" + message;
    }
}
