package pizzashop.kafka.serde;

import org.apache.kafka.common.serialization.Serdes;
import pizzashop.kafka.model.OrderItemWithContext;


public class OrderItemWithContextSerde extends Serdes.WrapperSerde<OrderItemWithContext> {
    public OrderItemWithContextSerde() {
        super(new JsonSerializer<>(), new JsonDeserializer<>(OrderItemWithContext.class));
    }
}
