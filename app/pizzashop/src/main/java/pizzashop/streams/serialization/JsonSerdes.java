package pizzashop.streams.serialization;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import pizzashop.models.Order;

public class JsonSerdes {

    public static Serde<Order> Order() {
        JsonSerializer<Order> serializer = new JsonSerializer<>();
        JsonDeserializer<Order> deserializer = new JsonDeserializer<>(Order.class);
        return Serdes.serdeFrom(serializer, deserializer);
    }

}
