package dev.roadmap.aws.orders;

import dev.roadmap.aws.LabProperties;
import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Service;

/**
 * Two ways to emit an order event:
 * <ul>
 * <li>point-to-point: straight onto the orders queue, which one consumer owns;</li>
 * <li>pub/sub: onto the SNS topic, which fans out to every subscribed queue (billing, shipping).</li>
 * </ul>
 */
@Service
public class OrderPublisher {

	private final SqsTemplate sqs;

	private final SnsTemplate sns;

	private final LabProperties props;

	public OrderPublisher(SqsTemplate sqs, SnsTemplate sns, LabProperties props) {
		this.sqs = sqs;
		this.sns = sns;
		this.props = props;
	}

	public void enqueue(OrderPlaced order) {
		sqs.send(props.ordersQueue(), order);
	}

	public void broadcast(OrderPlaced order) {
		// The topic NAME is resolved to an ARN by the TopicArnResolver (CreateTopic is idempotent).
		sns.sendNotification(props.orderEventsTopic(), order, "OrderPlaced");
	}

}
