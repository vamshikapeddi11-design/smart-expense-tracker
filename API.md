# API Reference

Base URL: `http://localhost:8080`

All backend API paths are prefixed with `/api`.

## Authentication

The application uses JWT authentication.

After successful signup or login, the server returns a JWT token.

Authenticated requests use:

`Authorization: Bearer <token>`

The following endpoints do not require authentication:

- `POST /api/auth/signup`
- `POST /api/auth/login`

## Endpoint Table

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/signup` | No | Register a new user |
| POST | `/api/auth/login` | No | Log in |
| GET | `/api/expenses` | Yes | List expenses |
| POST | `/api/expenses` | Yes | Create an expense |
| GET | `/api/expenses/{id}` | Yes | Get an expense |
| PUT | `/api/expenses/{id}` | Yes | Update an expense |
| DELETE | `/api/expenses/{id}` | Yes | Delete an expense |
| GET | `/api/expenses/summary` | Yes | Get spending summary |
| GET | `/api/budgets` | Yes | List budgets |
| POST | `/api/budgets` | Yes | Create a budget |
| PUT | `/api/budgets/{id}` | Yes | Update a budget |
| DELETE | `/api/budgets/{id}` | Yes | Delete a budget |
| GET | `/api/budgets/alerts` | Yes | Get budget alerts |
| GET | `/api/recurring` | Yes | List recurring expenses |
| POST | `/api/recurring` | Yes | Create a recurring expense |
| PUT | `/api/recurring/{id}` | Yes | Update a recurring expense |
| DELETE | `/api/recurring/{id}` | Yes | Delete a recurring expense |
| POST | `/api/qr/parse` | Yes | Parse a UPI QR payload |
| POST | `/api/qr/scan-image` | Yes | Scan a QR image |
| POST | `/api/bank/consent` | Yes | Link a bank account |
| GET | `/api/bank/accounts` | Yes | List linked bank accounts |
| POST | `/api/bank/sync` | Yes | Sync bank transactions |
| POST | `/api/bank/import` | Yes | Import a bank statement |
| POST | `http://localhost:8000/categorize` | No | Categorize expense text |
| POST | `http://localhost:8000/insights` | No | Generate spending insights |
| POST | `http://localhost:8000/forecast` | No | Generate spending forecast |

## Error Responses

Common HTTP status codes:

- `400` — Validation error
- `401` — Missing or invalid authentication
- `404` — Resource not found
- `409` — Duplicate resource or conflicting request