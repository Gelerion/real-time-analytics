package pizzashop.models;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class OrdersSummary {

    private long totalOrders;
    private TimePeriod currentTimePeriod;
    private TimePeriod previousTimePeriod;


    public OrdersSummary(long totalOrders, TimePeriod currentTimePeriod, TimePeriod previousTimePeriod) {
        this.totalOrders = totalOrders;
        this.currentTimePeriod = currentTimePeriod;
        this.previousTimePeriod = previousTimePeriod;
    }

    public TimePeriod getCurrentTimePeriod() {
        return currentTimePeriod;
    }

    public TimePeriod getPreviousTimePeriod() {
        return previousTimePeriod;
    }

    public long getTotalOrders() {
        return totalOrders;
    }
}
