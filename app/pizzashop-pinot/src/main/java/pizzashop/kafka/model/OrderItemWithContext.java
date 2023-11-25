package pizzashop.kafka.model;

import pizzashop.models.OrderItem;

public class OrderItemWithContext {

    public OrderItemWithContext() {
    }

    public OrderItemWithContext(String orderId, String createdAt, OrderItem orderItem) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.orderItem = orderItem;
    }

    public String orderId;
    public String createdAt;
    public OrderItem orderItem;
}
