package dev.roadmap.coreioc.events;

/** Any object can be an event since Spring 4.2; it no longer has to extend ApplicationEvent. */
public record OrderPlaced(String orderId, long amountCents) {
}
