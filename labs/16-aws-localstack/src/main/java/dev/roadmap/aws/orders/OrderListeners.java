package dev.roadmap.aws.orders;

import io.awspring.cloud.sqs.annotation.SnsNotificationMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Component;

/**
 * Listeners only translate SQS messages into calls on {@link OrderProcessor}. With the default
 * acknowledgement mode ON_SUCCESS, a message is deleted only if the method returns normally. If it
 * throws, the message stays in the queue and reappears after the visibility timeout.
 */
@Component
class OrderListeners {

	private final OrderProcessor processor;

	OrderListeners(OrderProcessor processor) {
		this.processor = processor;
	}

	@SqsListener("${lab.orders-queue}")
	void onOrder(OrderPlaced order) {
		processor.fulfil(order);
	}

	// The billing subscription uses RawMessageDelivery=true, so the SQS body IS the order JSON.
	@SqsListener("${lab.billing-queue}")
	void onOrderForBilling(OrderPlaced order) {
		processor.charge(order);
	}

	// The shipping subscription keeps the default SNS envelope: {"Type":"Notification","Message":"..."}.
	// @SnsNotificationMessage unwraps the envelope and converts the inner Message.
	@SqsListener("${lab.shipping-queue}")
	void onOrderForShipping(@SnsNotificationMessage OrderPlaced order) {
		processor.ship(order);
	}

}
