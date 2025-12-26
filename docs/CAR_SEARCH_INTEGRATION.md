# Car Search Integration Guide

This guide explains how the frontend should integrate with the Elasticsearch-backed car search APIs (REST and GraphQL).

## REST API

### 1. Search Endpoint

`GET /api/search/cars`

#### Query parameters

- `q` *(string, optional)* – free-text keyword (brand, model, variant, description, etc.).
- `brand` *(string, optional)* – exact brand filter (e.g. `Toyota`).
- `model` *(string, optional)* – exact model filter (e.g. `Camry`).
- `variant` *(string, optional)* – trim/variant string.
- `fuelType` *(string, optional)* – e.g. `PETROL`, `DIESEL`, `ELECTRIC`.
- `transmission` *(string, optional)* – e.g. `AUTOMATIC`, `MANUAL`.
- `city` *(string, optional)* – city/location filter.
- `minYear` / `maxYear` *(int, optional)* – inclusive year range.
- `minPrice` / `maxPrice` *(number, optional)* – inclusive price range.
- `verifiedDealer` *(boolean, optional)* – filter to only verified dealers.
- `page` *(int, default 0)* – zero-based page index.
- `size` *(int, default 20, max 50)* – page size.

#### Response shape

The endpoint returns an `ApiResponse<Page<CarSearchHitDto>>` where `data` is a Spring-style page:

- `data.content[]` – list of hits with fields:
  - `id` – car ID.
  - `brand`, `model`, `variant`.
  - `year`.
  - `price`.
  - `city`.
  - `fuelType`, `transmission`.
  - `verifiedDealer` (boolean).
  - `thumbnailUrl` – URL to thumbnail image.
  - `sellerType` – e.g. `USER`, `DEALER`, `ADMIN`.
- `data.totalElements`, `data.totalPages`, `data.number` (page index), `data.size`.

The backend sorts primarily by Elasticsearch relevance (via function_score) and secondarily by `createdAt` (newest first).

#### Example request

```http
GET /api/search/cars?q=toyota&city=Delhi&minPrice=500000&maxPrice=1500000&page=0&size=20
```

### 2. Suggestions Endpoint (Autocomplete)

`GET /api/search/cars/suggestions`

#### Query parameters

- `q` *(string, required)* – prefix typed by the user.
- `limit` *(int, optional, default 10, max 20)* – maximum suggestions.

#### Response

`ApiResponse<List<String>>` where each string is a suggestion like `"Toyota Camry"`.

Use this to power dropdown autocomplete in the search bar (debounced on the frontend).

### 3. Health Endpoint

`GET /api/search/cars/health`

- Returns `ApiResponse` with a success or error message about Elasticsearch connectivity.
- Intended mainly for internal monitoring / debugging.

## GraphQL API

The GraphQL endpoint is available at `/graphql`.

### 1. Types

Relevant types in `schema.graphqls`:

```graphql
type CarSearchHit {
  id: ID!
  brand: String!
  model: String!
  variant: String
  year: Int
  price: Float
  city: String
  fuelType: String
  transmission: String
  verifiedDealer: Boolean
  thumbnailUrl: String
  sellerType: String
}

type CarSearchPage {
  content: [CarSearchHit!]!
  totalPages: Int!
  totalElements: Int!
  pageNumber: Int!
  pageSize: Int!
}

input CarSearchInput {
  keyword: String
  brand: String
  model: String
  variant: String
  fuelType: String
  transmission: String
  city: String
  minYear: Int
  maxYear: Int
  minPrice: Float
  maxPrice: Float
  verifiedDealer: Boolean
  page: Int
  size: Int
}
```

### 2. Search Query

```graphql
query SearchCars($input: CarSearchInput!) {
  searchCars(input: $input) {
    content {
      id
      brand
      model
      variant
      year
      price
      city
      fuelType
      transmission
      verifiedDealer
      thumbnailUrl
      sellerType
    }
    totalPages
    totalElements
    pageNumber
    pageSize
  }
}
```

#### Example variables

```json
{
  "input": {
    "keyword": "toyota",
    "city": "Delhi",
    "minPrice": 500000,
    "maxPrice": 1500000,
    "page": 0,
    "size": 20
  }
}
```

### 3. Suggestions Query

```graphql
query CarSuggestions($prefix: String!, $limit: Int) {
  searchCarSuggestions(prefix: $prefix, limit: $limit)
}
```

- Returns `[String!]!` with suggestion strings like `"Toyota Camry"`.

## Frontend Guidance

### 1. Debounced Keyword Search

- Use a debounce (e.g. 250–400ms) on the search input before calling `GET /api/search/cars` or the `searchCars` GraphQL query.
- Maintain the last requested keyword and cancel/ignore stale responses if a newer query is in flight.

### 2. Faceted Filters

- Treat filters (brand, model, fuelType, transmission, city, year/price ranges, verifiedDealer) as optional query parameters / input fields.
- Only send filters that have a value; empty strings should be omitted.
- When a filter changes, reset `page` back to 0.

### 3. Pagination

- Use `page` (0-based) and `size` exactly as returned by the backend.
- For REST: read `data.totalPages`, `data.totalElements`, `data.number`, `data.size`.
- For GraphQL: read from `searchCars.totalPages`, etc.

### 4. Result Caps and UX

- Do not set `size` above 50; the backend enforces a cap.
- For infinite scrolling, increment `page` while `page < totalPages - 1`.
- Always show a clear “no results” state when `content` is empty.

### 5. Security & Performance Notes

- Search endpoints are public (`permitAll`) but protected by global rate limiting; avoid hammering them with very frequent requests.
- Sanitize user input on the client (trim, sensible max length for keyword).
- Prefer GraphQL when you already use `/graphql` heavily; otherwise REST is simpler.
