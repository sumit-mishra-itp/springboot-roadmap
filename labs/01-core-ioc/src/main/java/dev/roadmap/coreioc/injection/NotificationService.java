package dev.roadmap.coreioc.injection;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Constructor injection: a single constructor needs no @Autowired.
 * Shows the four ways to resolve "more than one candidate".
 */
@Service
public class NotificationService {

    private final NotificationSender defaultSender;      // resolved by @Primary
    private final NotificationSender smsSender;          // resolved by @Qualifier
    private final List<NotificationSender> allSenders;   // every bean, sorted by @Order
    private final Map<String, NotificationSender> byName; // bean name -> bean

    public NotificationService(NotificationSender defaultSender,
                               @Qualifier("sms") NotificationSender smsSender,
                               List<NotificationSender> allSenders,
                               Map<String, NotificationSender> byName) {
        this.defaultSender = defaultSender;
        this.smsSender = smsSender;
        this.allSenders = allSenders;
        this.byName = byName;
    }

    public String notifyDefault(String message) {
        return defaultSender.send(message);
    }

    public String notifySms(String message) {
        return smsSender.send(message);
    }

    public List<String> broadcast(String message) {
        return allSenders.stream().map(sender -> sender.send(message)).toList();
    }

    public String notifyVia(String beanName, String message) {
        NotificationSender sender = byName.get(beanName);
        if (sender == null) {
            throw new IllegalArgumentException("No sender bean named " + beanName + "; known: " + byName.keySet());
        }
        return sender.send(message);
    }
}
