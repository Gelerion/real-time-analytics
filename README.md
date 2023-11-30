# real-time-analytics
Ready to use example for real-time data analytics

There are a variety of use cases for real-time analytics, and each use case has different requirements with respect 
to query throughput, query latency, query complexity, and data accuracy.

| Use case                    | Query throughput (queries/second) | Query latency (p95th) | Consistency & accuracy | Query complexity |
|-----------------------------|-----------------------------------|-----------------------|------------------------|------------------|
| User-facing analytics       | Very high                         | Very low              | Best effort            | Low              |
| Personalization             | Very high                         | Very low              | Best effort            | Low              |
| Metrics                     | High                              | Very low              | Accurate               | Low              |
| Anomaly detection           | Moderate                          | Very low              | Best effort            | Low              |
| Root-cause analytics        | High                              | Very low              | Best effort            | Low              |
| Visualizations & dashboards | Moderate                          | Low                   | Best effort            | Medium           |
| Ad hoc analytics            | Low                               | High                  | Best effort            | High             |
| Log analytics & text search | Moderate                          | Moderate              | Best effort            | Medium           |

# Applications of Real-Time Analytics
The following are some potential applications that we could build to solve various business problems:
- Human-based/internal: A dashboard showing the latest orders, revenue, products ordered, and customer satisfaction. This will allow operators to keep on top of what’s currently happening with the business and react to problems as they happen.
- Human-based/external: A web/mobile application that lets customers track order status and the time until their pizza gets delivered on a live map. This will allow users to plan for the arrival of their order and get an early indication if anything is going wrong with the order.
- Machine-based/internal: An anomaly detection system on access logs that sends alerts via Slack or email when unusual traffic patterns are detected. This will allow site reliability engineering (SRE) team to make sure the website is highly available and to detect denial-of-service attacks.
- Machine-based/external: A fraud detection system that detects and blocks fraudulent orders.

![Screenshot](images/rta_quadrants.png)

# Running locally
Once Docker Compose is installed, you can start everything by running the following command:
```sh
docker compose up
```
### Existing architecture
![Screenshot](images/architecture.png)
   
#### Components  
- [Order Service](orders-service)
  - Simulates a pizza order service that receives orders from customers and sends them to the `orders` topic for processing.
- [Mysql](docker/mysql)
  - Stores users and products data.
- Apache Kafka
  - Orders from the website are published to Kafka via the orders service.

#### Inspecting data
```sh
kcat -C -b localhost:29092 -t orders -c 1
```
Example output:
```json
{
  "id": "c6745d1f-cecb-4aa8-993b-6dea64d06f52",
  "createdAt": "2022-09-06T10:46:17.703283",
  "userId": 416,
  "status": "PLACED_ORDER",
  "price": 1040,
  "items": [
    {
      "productId": "21",
      "quantity": 2,
      "price": 45
    },
    {
      "productId": "36",
      "quantity": 3,
      "price": 60
    },
    {
      "productId": "72",
      "quantity": 2,
      "price": 385
    }
  ]
}
```

## Version 1. Creating dashboards using Kafka-Streams
Pizza Shop doesn't currently have real-time insight into the number of orders being placed or the revenue being generated. 
The company would like to know if there are spikes or dips in the numbers of orders so that it can react more quickly 
in the operations part of the business.
  
The Pizza Shop engineering team is already familiar with Kafka Streams from other applications that they’ve built, 
so we’re going to create a Kafka Streams app that exposes an HTTP endpoint showing recent orders and revenue.
We’ll build this app with the Quarkus framework, starting with a naive version. Then we’ll apply some optimizations. 
We’ll conclude with a summary of the limitations of using a stream processor to query streaming data. 

#### Architecture
![Screenshot](images/kafka-streams-architecture.png)

#### Running locally
Running infrastructure:
```sh
docker compose up
```
Running the application:
```sh
cd app/pizzashop && ./mvnw compile quarkus:dev
```

#### Querying in counts in real-time from kafka-store
```
localhost:8080/orders/overview
```
Example output:
```json
{
  "currentTimePeriod": {
    "orders": 994,
    "totalPrice": 4496973
  },
  "previousTimePeriod": {
    "orders": 985,
    "totalPrice": 4535117
  }
}
```

### Limitations of Kafka Streams
While this approach for querying streams has been successful in many cases, certain factors could impact its efficacy.

The underlying database used by Kafka Streams is RocksDB, a key-value store that allows you to store and retrieve data 
using key-value pairs. This fork of Google’s LevelDB is optimized for write-heavy workloads with large datasets.

One of its constraints is that we can create only one index per key-value store. This means that if we decide to query 
the data along another dimension, we’ll need to update the topology to create another key-value store. If we do a 
non-key search, RocksDB will do a full scan to find the matching records, leading to high query latency.

Our key-value stores are also capturing only events that happened in the last one minute and the minute before that. 
If we wanted to capture data going further back, we’d need to update the topology to capture more events. In our case,
we could imagine a future use case where we’d want to compare the sales numbers from right now with the numbers from 
this same time last week or last month. This would be difficult to do in Kafka Streams because we’d need to store 
historical data, which would take up a lot of memory.

So although we can use Kafka Streams to write real-time analytics queries and it will do a reasonable job, we probably 
need to find a tool that better fits the problem.

## Version 2. Creating dashboards using Apache Pinot
Data warehouses are a form of OLAP database, but they aren’t suitable for real-time analytics because they don’t satisfy
the requirements in terms of ingestion latency, query latency, and concurrency.
  
Batch ETL pipelines are commonly used to populate big data warehouses such as `BigQuery` or `Redshift`. However, this causes 
ingestion latency and makes the data outdated when queried. Moreover, their query engines are not optimized for 
millisecond latency, but for ad hoc querying with acceptable latencies in the seconds. 
Finally, our serving layer needs to scale to thousands of queries per second if we’re building user-facing applications, 
which isn’t the sweet spot of data warehouses.

Instead we will use a real-time OLAP database, and it’s `Apache Pinot` that is going to perform the role of serving layer 
for our application. `Pinot` is a database that was created at LinkedIn in 2013, after the engineering staff determined
that no off-the-shelf solutions met the social networking site’s requirements of predictable low latency, data 
freshness in seconds, fault tolerance, and scalability. These are essential features for any organization that wants 
to obtain real-time insights from its data and make informed decisions quickly.

#### Architecture
![Screenshot](images/pinot_architecture.png)

#### Running locally
Running infrastructure:
```sh
docker compose -f compose.yaml -f compose-pinot.yaml up
```
Running the application:
```sh
cd app/pizzashop-pinot && ./mvnw compile quarkus:dev
```
  
#### Querying in counts in real-time from Apache Presto
```
localhost:8080/orders/overview
```
Example output:
```json
{
  "totalOrders": 17665,
  "currentTimePeriod": {
    "orders": 968,
    "totalPrice": 4313950.0
  },
  "previousTimePeriod": {
    "orders": 958,
    "totalPrice": 4281751.0
  }
}
```
## Building a Real-Time Analytics Dashboard
The current architecture includes the [Order Service](https://www.notion.so/gelerion/orders-service), which simulates 
users generating order events. These order events are produced to the Kafka topic named `orders`, consumed in real 
time by Pinot, and ingested into the [orders](https://www.notion.so/gelerion/docker/pinot/config/orders/table.json) 
table. We use a small rest service, [pizzashop](https://www.notion.so/gelerion/app/pizzashop-pinot), to query the data from Pinot.
  
We are going to build a [dashboards](https://www.notion.so/gelerion/dashboards) service for data visualization, 
using the Streamlit framework.  

![Screenshot](images/rta_dashbords_architecture.png)
  
### Running locally:
Remove everything:
```sh
docker compose \
      -f compose.yaml \
      -f compose-pinot-arm.yaml \
      -f compose-pizzashop.yaml \
      -f compose-dashboard.yaml \
      down --volumes
```

Run everything:
```sh
docker compose \
      -f compose.yaml \
      -f compose-pinot-arm.yaml \
      -f compose-pizzashop.yaml \
      -f compose-dashboard.yaml \
      up
```
### Dashboards
Dashboards are managed by the [dashboards](https://www.notion.so/gelerion/dashboards) service.
  
![Screenshot](images/basic_dashboard.png)
  
Links:
- Pinot UI: http://localhost:9000
- Dashboards UI: http://localhost:8501

## Capture Product changes using Debezium (CDC)
We get a solid overview of the number of orders and the revenue that the business is making. What’s 
missing is that they don’t know what’s happening at the product level.
  
The data about individual products is currently stored in the MySQL database, but we need to get it out of there and 
into our real-time analytics architecture.
  
Change data capture describes the process of capturing the changes made to data in a source system and 
then making them available to downstream/derived data systems. These changes could be new records, 
changes to existing records, or deleted records.  
`Debezium` is an open source, distributed platform for change data capture, written by RedHat. It 
consists of a set of connectors that run in a Kafka Connect cluster. Each connector works with a 
specific database, where it captures changes as they occur and then streams a record of each change 
event to a topic in Kafka. These events are then read from the Kafka topic by consuming applications.

![Screenshot](images/debezium.png)

#### Setup Debezium
```
  debezium:
    image: debezium/connect:2.4
    
  debezium_deploy:
    image: debezium/connect:2.4
    depends_on:
      debezium: {condition: service_healthy}
    volumes:
      - ./docker/debezium/register_mysql.sh:/register_mysql.sh
```
After starting the Debezium container, we need to register the MySQL connector with the Kafka Connect cluster.
  
#### Querying state changes
```
kcat -C -b localhost:29092 -t mysql.pizzashop.products -c1 | jq '.payload'
```
Example output
```
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
```

## Add most popular items and categories
Although the `orders` stream doesn't currently contain detailed information about products, we have set up Debezium to 
capture any changes to the MySQL `products` table and write them into the `products` stream. 
We need to combine the `orders` stream and `products` streams by using a stream processor.
We’ll put the new stream into Apache Pinot and update the dashboard with the top-selling products and categories.

![Screenshot](images/kafka_streams.png)
  
To do so we need to create a `KTable` on top of the `products` stream and join with a `KStream` 
on top of the `orders` stream.
```
KStream<String, Order> orders = builder.stream("orders")
KTable<String, Product> products = builder.table("products")
orders.join(products).to("enriched-order-items")
```
  
Checkout the complete example:
[EnrichedOrdersTopology.java](app/pizzashop-pinot/src/main/java/pizzashop/kafka/EnrichedOrdersTopology.java)

Next, we need to add a new table and schema to Pinot that consumes the data from this new stream. 
See the example: [enriched-orders](docker/pinot/config/orders_items_enriched/schema.json)
  
Finally, we need to update the dashboard to show the top-selling products and categories. 
To do so, we need to update the `pizzashop` application to query the new table and expose the data via a new endpoint.
```
    @GET
    @Path("/popular")
    public Response popular() {
        return Response.ok()
                .entity(orderService.popular())
                .build();
    }
```
And update the [dashboard](dashboards/app.py) to query the new endpoint.
```
response = requests.get(f"{pizza_shop_service_api}/orders/popular").json()
```

### Dashboards with most popular items and categories
![Screenshot](images/most_popular_items_dashboard.png)


## Services
- [Order Service](orders-service)
  - Simulates a pizza order service that receives orders from customers and sends them to the `orders` topic for processing
- [Pizza Shop](app/pizzashop-pinot)
  - Queries the `orders` table and exposes an HTTP endpoint for querying the data
  - Available on: http://localhost:8080
- [Dashboards](dashboar`ds)
  - Visualizes the data from the `orders` table, showing the number of orders and the revenue that the business is making
  - Available on: http://localhost:8501
  
- [Mysql](docker/mysql)
  - Stores users and products data
  - Available on: localhost:3306
    - user: mysqluser
    - password: mysqlpw
- [Pinot](docker/pinot)
  - Consumes orders from the `orders` topic and ingests them into the `orders` table for real time analysis
  - Available on: http://localhost:9000
  
- [Kafka](docker/kafka)
  - Available on: localhost:29092
- [Zookeeper](docker/zookeeper)
  - Used for Kafka and Pinot coordination
  - Available on: localhost:2181
- [Debezium](docker/debezium)
  - Captures changes from the `users` and `products` tables
  - Available on: localhost:8083

## Running for the first time
Before executing `doker compose` for the first time, you need to build `pizzashop` docker image:
```sh
cd app/pizzashop-pinot &&  
sdk use java 17.0.8-amzn &&
./mvnw package &&
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/pizzashop-jvm .
```
  
Running manually
```sh
docker compose -f compose.yaml -f compose-pinot.yaml down --volumes
docker compose -f compose.yaml -f compose-pinot.yaml up
cd app/pizzashop-pinot
sdk use java 17.0.8-amzn 
./mvnw clean package
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/pizzashop-jvm .
cd ..
cd ..
cd dashboards
docker build --tag streamlit .
docker run -p 8501:8501 streamlit
cd ..
cd delivery-service
docker build --tag delivery-service .
```

#### Frontend
See images/frontend

### TODO:
Generators, move to quarkus streams
- Explain endpoints
- Delivery services listens to the `orders` topic and sends updates to the `orders_statuses` topic
- Delivery services also update delivery coordinates with IN_TRANSIT status and long/lat coordinates
  - deliveryStatuses topic
- Pinot and upserts (option(skipUpsert=true)) -- using the skipUpsert=true flag to tell Pinot to return all the records associated with this id rather than just the latest one, which is what it would do by default
example
```
select id, userId, status, price, ts
from orders_enriched
WHERE id = '816b9d84-9426-4055-9d48-54d11496bfbe'
limit 10
option(skipUpsert=true)
```

Time spend for each order (query Pinot) -- for orders currently in the kitchen
```
select status,
	   min((now() - ts) / 1000) as min,
	   percentile((now() - ts) / 1000, 50) AS percentile50,
       avg((now() - ts) / 1000) as avg,
	   percentile((now() - ts) / 1000, 75) AS percentile75,
	   percentile((now() - ts) / 1000, 90) AS percentile90,
	   percentile((now() - ts) / 1000, 90) AS percentile99,
     max((now() - ts) / 1000) as max
from orders_enriched
WHERE status NOT IN ('DELIVERED', 'OUT_FOR_DELIVERY')
group by status;
```
| status          | min    | percentile50 | avg                 | percentile75 | percentile90 | percentile99 | max      |
|-----------------|--------|--------------|---------------------|--------------|--------------|--------------|----------|
| ORDER_CONFIRMED | 18.723 | 22.935       | 23.13713333333334   | 25.452       | 27.114       | 29.13        | 29.719   |
| BEING_COOKED    | 18.752 | 38.39        | 39.55397026604069   | 47.026       | 60.505       | 72.908       | 77.366   |
| BEING_PREPARED  | 18.715 | 25.705       | 25.971310344827568  | 29.753       | 32.114       | 35.277       | 35.526   |
| PLACED_ORDER    | 14.544 | 25.708       | 26.718140921409194  | 32.268       | 38.268       | 46.308       | 47.852   |
  

Geo formula to approximate delivery time:

```
        dist = geopy.distance.distance(shop_location, delivery_location).meters
        minutes_to_deliver = (dist / (driver_km_per_hour * 1000)) * 60
```
  
Geospatial in Pinot
The transformation function uses the stPoint function, which has the following signature: `STPOINT(x, y, isGeography)` 
This function returns a geography point object with the provided coordinate values  
The location column also has a geospatial index:
```
"fieldConfigList": [
   {
     "name": "location",
     "encodingType":"RAW",
     "indexType":"H3",
     "properties": {"resolutions": "5"}
   }
 ]
```
Geospatial indexing enables efficient querying of location-based data, such as latitude and longitude coordinates or 
geographical shapes. Apache Pinot’s geospatial index is based on Uber’s Hexagonal Hierarchical Spatial Index (H3) library.
This library provides hexagon-based hierarchical gridding. Indexing a point means that the point is translated to 
a geoId, which corresponds to a hexagon. Its neighbors in H3 can be approximated by a ring of hexagons. Direct neighbors 
have a distance of 1, their neighbors are at a distance of 2, and so on.
![Screenshot](images/hexagons_index.png)

 #### How many delivers around  within a particular virtual perimeter around a real area
Example of getting long, lat coordinates for a particular order:
```
select deliveryLat, deliveryLon, id, ts
from delivery_statuses
WHERE id = '816b9d84-9426-4055-9d48-54d11496bfbe'
limit 10
option(skipUpsert=true)
```

We can also run a query to see how many deliveries are within a particular virtual perimeter around a real area, known 
as a `geofence`. Perhaps the operations team has information that there’s been an accident in a certain part of the city
and wants to see how many drivers are potentially stuck. An example query is shown here:
```
select count(*)
from delivery_statuses
WHERE ST_Contains(
         ST_GeomFromText('POLYGON((
           77.6110752789269 12.967434625129457,
           77.61949358844464 12.972227849153782,
           77.62067778131079 12.966846580403327,
           77.61861133323839 12.96537193573893,
           77.61507457042217 12.965872682158846,
           77.6110752789269 12.967434625129457))'),
	 toGeometry(location)
      ) = 1
AND status = 'IN_TRANSIT'
```