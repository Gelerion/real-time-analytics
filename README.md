# real-time-analytics
Ready to use example for real-time data analytics

# Applications of Real-Time Analytics
The following are some potential applications that we could build to solve various business problems:
- Human-based/internal: A dashboard showing the latest orders, revenue, products ordered, and customer satisfaction. This will allow operators to keep on top of what’s currently happening with the business and react to problems as they happen.
- Human-based/external: A web/mobile application that lets customers track order status and the time until their pizza gets delivered on a live map. This will allow users to plan for the arrival of their order and get an early indication if anything is going wrong with the order.
- Machine-based/internal: An anomaly detection system on access logs that sends alerts via Slack or email when unusual traffic patterns are detected. This will allow site reliability engineering (SRE) team to make sure the website is highly available and to detect denial-of-service attacks.
- Machine-based/external: A fraud detection system that detects and blocks fraudulent orders.

## Creating dashboards using Kafka-Streams
Querying in counts in real-time from kafka-store:
```http request
localhost:8080/orders/overview
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