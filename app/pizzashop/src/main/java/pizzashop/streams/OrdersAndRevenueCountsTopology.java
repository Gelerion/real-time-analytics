package pizzashop.streams;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import pizzashop.models.Order;
import pizzashop.streams.serialization.JsonSerdes;
import java.util.Properties;
import java.time.Duration;

@ApplicationScoped
public class OrdersAndRevenueCountsTopology {

    @Produces
    public Topology buildTopology() {
        // Create a stream over the `orders` topic
        StreamsBuilder builder = new StreamsBuilder();
        // Use: ObjectMapperSerde
        // e.g. https://quarkus.io/guides/kafka-streams
        KStream<String, Order> orders = builder
                .stream("orders", Consumed.with(Serdes.String(), JsonSerdes.Order()));


        // Defining the window size of our state store
        Duration windowSize = Duration.ofSeconds(60);
        Duration advanceSize = Duration.ofSeconds(1);
        Duration gracePeriod = Duration.ofSeconds(60);
        TimeWindows hoppingWindow = TimeWindows
                .ofSizeAndGrace(windowSize, gracePeriod)
                //A classic example of a hopping window implementation is a dashboard with moving averages—say,
                // average clicks on a certain e-commerce page, like a product details page for an air fryer,
                // for 2-minute windows in the past 24 hours.
                .advanceBy(advanceSize);

        // Create an OrdersCountStore that keeps track of the number of orders over the last two minutes
        orders.groupBy((key, value) -> "count", Grouped.with(Serdes.String(), JsonSerdes.Order()))
                .windowedBy(hoppingWindow)
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("OrdersCountStore")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long())
                );

        // Create a RevenueStore that keeps track of the amount of revenue generated over the last two minutes
        orders.groupBy((key, value) -> "count", Grouped.with(Serdes.String(), JsonSerdes.Order()))
                .windowedBy(hoppingWindow)
                .aggregate(
                        () -> 0.0,
                        (key, order, aggregate) -> aggregate + order.price,
                        Materialized.<String, Double, WindowStore<Bytes, byte[]>>as("RevenueStore")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Double())
                );

        final Properties props = new Properties();

        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerdes.Order().getClass());

        return builder.build(props);
    }
}
