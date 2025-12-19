# Gateway Testing

## Chat Endpoint

### Basic Request
```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Customer-ID: CUST123456" \
  -d '{
    "messageText": "What is my account balance?"
  }'
```

### Example with Hebrew Message
```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Customer-ID: CUST123456" \
  -d '{
    "messageText": "מה יתרת החשבון שלי?"
  }'
```

### Testing Rate Limiting
Run the same request multiple times (more than 15 times per minute) to test rate limiting:
```bash
for i in {1..12}; do
  curl -X POST http://localhost:8081/api/v1/chat \
    -H "Content-Type: application/json" \
    -H "X-Customer-ID: CUST123456" \
    -d '{"messageText": "Test message"}'
  echo ""
done
```

### Testing Missing Customer ID (should return 400)
```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messageText": "What is my account balance?"
  }'
```

### Testing Empty Message (should return 400)
```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-Customer-ID: CUST123456" \
  -d '{
    "messageText": ""
  }'
```

## Expected Responses

### Success Response (200 OK)
```json
{
  "answer": "Gateway received your message. Language detection and processing coming soon.",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "explanation": "This is a placeholder response from the Gateway."
}
```

### Rate Limit Exceeded (429 Too Many Requests)
```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Please try again later."
}
```

### Missing Customer ID (400 Bad Request)
```json
{
  "code": "MISSING_CUSTOMER_ID",
  "message": "Customer ID header is required"
}
```

### Validation Error (400 Bad Request)
```json
{
  "code": "VALIDATION_ERROR",
  "message": "messageText: must not be blank"
}
```
