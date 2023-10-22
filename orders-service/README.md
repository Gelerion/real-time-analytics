## Orders Service
The orders service publishes multiple orders per second into the orders topic in Kafka. 
The structure of the events in this topic is shown here:

```json
{
   "id":"string",
   "createdAt":"string",
   "userId":"integer",
   "price":"double",
   "status":"string",
   "items": [
      {
         "productId": "string",
         "quantity": "integer",
         "price": "double"
      }
   ]
}
```