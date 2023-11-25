package pizzashop.kafka;

import io.debezium.serde.DebeziumSerdes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import pizzashop.kafka.model.HydratedOrderItem;
import pizzashop.kafka.model.OrderItemWithContext;
import pizzashop.kafka.serde.JsonDeserializer;
import pizzashop.kafka.serde.JsonSerializer;
import pizzashop.kafka.serde.OrderItemWithContextSerde;
import pizzashop.models.Order;
import pizzashop.models.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

@ApplicationScoped
public class EnrichedOrdersTopology {

    @Produces
    public Topology buildTopology() {
        String orderStatusesTopic = System.getenv().getOrDefault("ORDER_STATUSES_TOPIC",  "ordersStatuses");
        String ordersTopic = System.getenv().getOrDefault("ORDERS_TOPIC",  "orders");
        String productsTopic = System.getenv().getOrDefault("PRODUCTS_TOPIC",  "mysql-connector-1.pizzashop.products");
        String enrichedOrderItemsTopic = System.getenv().getOrDefault("ENRICHED_ORDER_ITEMS_TOPIC",  "enriched-order-items");
        String enrichedOrdersTopic = System.getenv().getOrDefault("ENRICHED_ORDERS_TOPIC", "enriched-orders");

        final Serde<Order> orderSerde = Serdes.serdeFrom(new JsonSerializer<>(), new JsonDeserializer<>(Order.class));
        OrderItemWithContextSerde orderItemWithContextSerde = new OrderItemWithContextSerde();

        Serde<String> productKeySerde = DebeziumSerdes.payloadJson(String.class);
        productKeySerde.configure(Collections.emptyMap(), true);

        Serde<Product> productSerde = DebeziumSerdes.payloadJson(Product.class);
        productSerde.configure(Collections.singletonMap("from.field", "after"), false);

        final Serde<HydratedOrderItem> hydratedOrderItemsSerde = Serdes.serdeFrom(new JsonSerializer<>(),
                new JsonDeserializer<>(HydratedOrderItem.class));

        StreamsBuilder builder = new StreamsBuilder();

        /*
        Orders:
          "id": "c6745d1f-cecb-4aa8-993b-6dea64d06f52",
          "createdAt": "2022-09-06T10:46:17.703283",
          "userId": 416,
          "price": 1040,
          "items": [
            {
              "productId": "21",
              "quantity": 2,
              "price": 45
            },
         */
        var orders = builder.stream(ordersTopic, Consumed.with(Serdes.String(), orderSerde));
        /*
        {
          "before": null,
          "after": {
            "id": 1,
            "name": "Moroccan Spice Pasta Pizza - Veg",
            "description": "A pizza with a combination of Harissa sauce & delicious pasta.",
            "category": "veg pizzas",
            "price": 335,
            "image": "https://oreil.ly/LCGSv",
            "created_at": "2022-12-05T16:56:02Z",
            "updated_at": 1670259362000
          }
        }
         */
        KTable<String, Product> products = builder.table(productsTopic, Consumed.with(productKeySerde, productSerde));

        //flatten an array of order items
        KStream<String, OrderItemWithContext> orderItems = orders.flatMap((key, order) -> {
            ArrayList<KeyValue<String, OrderItemWithContext>> result = new ArrayList<>();
            for (var item : order.items) {
                OrderItemWithContext orderItemWithContext = new OrderItemWithContext(order.id, order.createdAt, item);
                result.add(KeyValue.pair(item.productId, orderItemWithContext));
            }
            return result;
        });

        //The next step is to enrich each of those order item events with their associated product.
        /*
        Output example:
        {
          "orderId": "7bcd4bbe-c1a6-4bb3-807e-386a837bc2b3",
          "createdAt": "2022-09-13T05:22:53.617952",
          "product": {
            "id": "3",
            "name": "Pepsi Black Can",
            "description": "PEPSI BLACK CAN",
            "category": "beverages",
            "image": "https://oreil.ly/nYCzO",
            "price": 60
          },
          "orderItem": {
            "productId": "3",
            "quantity": 3,
            "price": 60
          }
        }
         */
        orderItems.join(products,
                        (orderItem, product) ->
                                new HydratedOrderItem(orderItem.orderId, orderItem.createdAt, product, orderItem.orderItem),
                        Joined.with(Serdes.String(), orderItemWithContextSerde, productSerde))
        .to(enrichedOrderItemsTopic, Produced.with(Serdes.String(), hydratedOrderItemsSerde));

        final Properties props = new Properties();

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, OrderItemWithContextSerde.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return builder.build(props);
    }

}
