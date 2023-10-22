package pizzashop.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import pizzashop.repository.models.OrdersSummary;
import pizzashop.repository.models.TimePeriod;

import java.time.Instant;

@ApplicationScoped
public class OrdersQueries {

    @Inject
    private KafkaStreams streams;

    public OrdersSummary ordersSummary() {
        KStreamsWindowStore<Long> countStore = new KStreamsWindowStore<>(ordersCountsStore());
        KStreamsWindowStore<Double> revenueStore = new KStreamsWindowStore<>(revenueStore());

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        Instant twoMinutesAgo = now.minusSeconds(120);

        long recentCount = countStore.firstEntry(oneMinuteAgo, now);
        double recentRevenue = revenueStore.firstEntry(oneMinuteAgo, now);

        long  previousCount = countStore.firstEntry(twoMinutesAgo, oneMinuteAgo);
        double previousRevenue = revenueStore.firstEntry(twoMinutesAgo, oneMinuteAgo);

        TimePeriod currentTimePeriod = new TimePeriod(recentCount, recentRevenue);
        TimePeriod previousTimePeriod = new TimePeriod(previousCount, previousRevenue);
        return new OrdersSummary(
                currentTimePeriod, previousTimePeriod
        );
    }

    /*
    Both ordersCountsStore and revenueStore are returning data from window stores that hold the order count and amount
    of revenue generated, respectively. The reason for the while(true) { try {} catch {} } code block in both functions
    is that the store might not be available if we call this code before the stream thread is in a RUNNING state.
    Assuming we don’t have any bugs in our code, we will eventually get to the RUNNING state; it just might take a bit
    longer than it takes for the HTTP endpoint to start up.
     */
    private ReadOnlyWindowStore<String, Double> revenueStore() {
        while (true) {
            try {
                return streams.store(StoreQueryParameters.fromNameAndType(
                        "RevenueStore",
                        QueryableStoreTypes.windowStore()
                ));
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
            }
        }
    }

    private ReadOnlyWindowStore<String, Long> ordersCountsStore() {
        while (true) {
            try {
                return streams.store(StoreQueryParameters.fromNameAndType(
                        "OrdersCountStore",
                        QueryableStoreTypes.windowStore())
                );
            } catch (InvalidStateStoreException e) {
                System.out.println("e = " + e);
            }
        }
    }
}
