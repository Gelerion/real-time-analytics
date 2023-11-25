package pizzashop.kafka.model;

import pizzashop.models.OrderItem;
import pizzashop.models.Product;

public class HydratedOrderItem  {

    public HydratedOrderItem() {
    }

    public HydratedOrderItem(String orderId, String createdAt, Product product, OrderItem orderItem) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.product = product;
        this.orderItem = orderItem;
    }

    public String orderId;
    public String createdAt;
    public Product product;
    public OrderItem orderItem;

}
