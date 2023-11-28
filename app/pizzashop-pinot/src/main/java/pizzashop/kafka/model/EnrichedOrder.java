package pizzashop.kafka.model;

import pizzashop.models.OrderItem;

import java.util.List;

public class EnrichedOrder {
    public EnrichedOrder() {

    }

    public String id;
    public String userId;
    public String status;
    public String createdAt;

    public double price;

    public List<OrderItem> items;

}
