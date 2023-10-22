package pizzashop;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import pizzashop.repository.OrdersQueries;
import pizzashop.repository.models.OrdersSummary;

@ApplicationScoped
@Path("/orders")
public class OrdersResource {
    @Inject
    OrdersQueries ordersQueries;

    @GET
    @Path("/overview")
    public Response overview() {
        OrdersSummary ordersSummary = ordersQueries.ordersSummary();
        return Response.ok(ordersSummary).build();
    }
}
