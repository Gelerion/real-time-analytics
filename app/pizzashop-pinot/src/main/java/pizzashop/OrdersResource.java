package pizzashop;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.apache.pinot.client.Connection;
import org.apache.pinot.client.ConnectionFactory;
import org.apache.pinot.client.ResultSet;
import org.apache.pinot.client.ResultSetGroup;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import pizzashop.models.OrdersSummary;
import pizzashop.models.SummaryRow;
import pizzashop.models.TimePeriod;


import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.*;

@ApplicationScoped
@Path("/orders")
public class OrdersResource {

    private Connection connection = ConnectionFactory
            .fromHostList(System.getenv().getOrDefault("PINOT_BROKER",  "localhost:8099"));

    @GET
    @Path("/overview")
    public Response overview() {
        ResultSet resultSet = runQuery(connection, "select count(*) from orders limit 10");
        int totalOrders = resultSet.getInt(0);

        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        count()
                                .filterWhere("ts > ago('PT1M')")
                                .as("events1Min"),

                        count()
                                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                                .as("events1Min2Min"),

                        sum(field("price").coerce(Long.class))
                                .filterWhere("ts > ago('PT1M')")
                                .as("total1Min"),

                        sum(field("price").coerce(Long.class))
                                .filterWhere("ts <= ago('PT1M') AND ts > ago('PT2M')")
                                .as("total1Min2Min")

                ).from("orders")
                .getSQL();

        //System.out.println(query);

        ResultSet summaryResults = runQuery(connection, query);

        TimePeriod currentTimePeriod = new TimePeriod(
                summaryResults.getLong(0, 0), summaryResults.getDouble(0, 2));
        TimePeriod previousTimePeriod = new TimePeriod(
                summaryResults.getLong(0, 1), summaryResults.getDouble(0, 3));

        OrdersSummary ordersSummary = new OrdersSummary(
                totalOrders, currentTimePeriod, previousTimePeriod
        );

        return Response.ok(ordersSummary).build();
    }

    @GET
    @Path("/ordersPerMinute")
    public Response ordersPerMinute() {
        String query = DSL.using(SQLDialect.POSTGRES)
                .select(
                        field("ToDateTime(DATETRUNC('MINUTE', ts), 'yyyy-MM-dd HH:mm:ss')")
                                .as("dateMin"),
                        count(field("*")),
                        sum(field("price").coerce(Long.class))
                )
                .from("orders")
                .groupBy(field("dateMin"))
                .orderBy(field("dateMin"))
                .$where(field("dateMin").greaterThan(field("ago('PT1H')")))
                .$limit(DSL.inline(60))
                .getSQL();

        ResultSet summaryResults = runQuery(connection, query);

        int rowCount = summaryResults.getRowCount();

        List<SummaryRow> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            rows.add(new SummaryRow(
                    summaryResults.getString(index, 0),
                    summaryResults.getLong(index, 1),
                    summaryResults.getDouble(index, 2)
            ));
        }

        return Response.ok(rows).build();
    }

    private static ResultSet runQuery(Connection connection, String query) {
        System.out.println("Running the following query: " + query);
        ResultSetGroup resultSetGroup = connection.execute(query);
        return resultSetGroup.getResultSet(0);
    }
}
