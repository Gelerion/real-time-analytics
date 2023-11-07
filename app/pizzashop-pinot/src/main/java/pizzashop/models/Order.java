package pizzashop.models;

import io.quarkus.runtime.annotations.RegisterForReflection;
import pizzashop.models.OrderItem;

import java.util.List;

@RegisterForReflection
public class Order {
    public Order() {}

    public String id;
    public String userId;
    public String createdAt;

    public double price;

    public double deliveryLat;
    public double deliveryLon;

    public List<OrderItem> items;
}
