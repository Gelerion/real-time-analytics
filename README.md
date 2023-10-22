# real-time-analytics
Ready to use example for real-time data analytics

# Applications of Real-Time Analytics
The following are some potential applications that we could build to solve various business problems:
- Human-based/internal: A dashboard showing the latest orders, revenue, products ordered, and customer satisfaction. This will allow operators to keep on top of what’s currently happening with the business and react to problems as they happen.
- Human-based/external: A web/mobile application that lets customers track order status and the time until their pizza gets delivered on a live map. This will allow users to plan for the arrival of their order and get an early indication if anything is going wrong with the order.
- Machine-based/internal: An anomaly detection system on access logs that sends alerts via Slack or email when unusual traffic patterns are detected. This will allow site reliability engineering (SRE) team to make sure the website is highly available and to detect denial-of-service attacks.
- Machine-based/external: A fraud detection system that detects and blocks fraudulent orders.

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
./mvnw compile quarkus:dev
```

#### Querying in counts in real-time from kafka-store
```http
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
the data along another dimension, we’ll need to update the topology to create another key-value store. If we do a non-key search, RocksDB will do a full scan to find the matching records, leading to high query latency.

Our key-value stores are also capturing only events that happened in the last one minute and the minute before that. 
If we wanted to capture data going further back, we’d need to update the topology to capture more events. In our case,
we could imagine a future use case where we’d want to compare the sales numbers from right now with the numbers from 
this same time last week or last month. This would be difficult to do in Kafka Streams because we’d need to store 
historical data, which would take up a lot of memory.

So although we can use Kafka Streams to write real-time analytics queries and it will do a reasonable job, we probably 
need to find a tool that better fits the problem.