package com.carselling.oldcar.search;

import com.carselling.oldcar.document.VehicleSearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Concrete implementation using OpenSearch Java Client.
 * Replaces the previous Spring Data Elasticsearch Interface.
 */
@Repository
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class VehicleSearchRepository {

  private final OpenSearchClient client;
  private static final String INDEX_NAME = "vehicle_search_v1";

  public java.util.Optional<VehicleSearchDocument> findById(String id) {
    try {
      // GetResponse<VehicleSearchDocument> response = client.get(g ->
      // g.index(INDEX_NAME).id(id), VehicleSearchDocument.class);
      // return response.found() ? java.util.Optional.ofNullable(response.source()) :
      // java.util.Optional.empty();

      // Explicit generic handling if needed, but standard usage:
      return java.util.Optional.ofNullable(
          client.get(g -> g.index(INDEX_NAME).id(id), VehicleSearchDocument.class).source());
    } catch (IOException e) {
      log.error("Failed to get vehicle {}", id, e);
      return java.util.Optional.empty();
    }
  }

  public void save(VehicleSearchDocument document) {
    try {
      IndexRequest<VehicleSearchDocument> request = IndexRequest.of(i -> i
          .index(INDEX_NAME)
          .id(document.getId())
          .document(document));
      client.index(request);
    } catch (IOException e) {
      log.error("Failed to index vehicle {}", document.getId(), e);
      throw new RuntimeException("Failed to index vehicle", e);
    }
  }

  public void saveAll(List<VehicleSearchDocument> documents) {
    if (documents.isEmpty())
      return;

    try {
      BulkRequest.Builder br = new BulkRequest.Builder();
      for (VehicleSearchDocument doc : documents) {
        br.operations(op -> op
            .index(idx -> idx
                .index(INDEX_NAME)
                .id(doc.getId())
                .document(doc)));
      }
      client.bulk(br.build());
    } catch (IOException e) {
      log.error("Failed to bulk index {} vehicles", documents.size(), e);
      throw new RuntimeException("Failed to bulk index vehicles", e);
    }
  }

  public void deleteById(String id) {
    try {
      client.delete(d -> d.index(INDEX_NAME).id(id));
    } catch (IOException e) {
      log.error("Failed to delete vehicle {}", id, e);
      // non-fatal
    }
  }

  public long count() {
    try {
      return client.count(c -> c.index(INDEX_NAME)).count();
    } catch (IOException e) {
      log.error("Failed to count vehicles", e);
      return 0;
    }
  }

  // --- Search Methods (Simplified for Migration) ---

  // Note: The previous interface had many specific findBy methods.
  // For this migration, we implement a flexible search method that the Service
  // layer can adapt to.

  public List<VehicleSearchDocument> search(Query query, int from, int size) {
    try {
      SearchRequest request = SearchRequest.of(s -> s
          .index(INDEX_NAME)
          .query(query)
          .from(from)
          .size(size));

      SearchResponse<VehicleSearchDocument> response = client.search(request, VehicleSearchDocument.class);
      return response.hits().hits().stream()
          .map(h -> h.source())
          .collect(Collectors.toList());

    } catch (IOException e) {
      log.error("Search failed", e);
      return new ArrayList<>();
    }
  }

  // Helper to build queries - exposed for Service to construct complex logic if
  // needed
  // or keep logic here. For strict typing, let's expose generic 'search' and move
  // implementations here.

  public List<VehicleSearchDocument> findByActiveTrueAndDealerVerifiedTrue(int page, int size) {
    Query query = BoolQuery.of(b -> b
        .must(m -> m.term(t -> t.field("active").value(v -> v.booleanValue(true))))
        .must(m -> m.term(t -> t.field("dealerVerified").value(v -> v.booleanValue(true)))))._toQuery();

    return search(query, page * size, size);
  }

  // Support for the generic 'findWithBoostingSearchAndFilters' logic
  public List<VehicleSearchDocument> findWithQuery(String searchText, boolean activeOnly, boolean verifiedOnly,
      int page, int size) {
    BoolQuery.Builder bool = new BoolQuery.Builder();

    if (activeOnly) {
      bool.must(m -> m.term(t -> t.field("active").value(v -> v.booleanValue(true))));
    }
    if (verifiedOnly) {
      bool.must(m -> m.term(t -> t.field("dealerVerified").value(v -> v.booleanValue(true))));
    }

    if (searchText != null && !searchText.isBlank()) {
      bool.must(m -> m
          .multiMatch(mm -> mm
              .query(searchText)
              .fields("brand^3", "model^3", "variant", "city")));
    }

    return search(bool.build()._toQuery(), page * size, size);
  }

  public List<VehicleSearchDocument> findSuggestions(String prefix) {
    if (prefix == null)
      return new ArrayList<>();

    Query query = BoolQuery.of(b -> b
        .should(s -> s.prefix(p -> p.field("brand").value(prefix)))
        .should(s -> s.prefix(p -> p.field("model").value(prefix)))
        .should(s -> s.wildcard(w -> w.field("brand").value("*" + prefix + "*")))
        .should(s -> s.wildcard(w -> w.field("model").value("*" + prefix + "*"))))._toQuery();

    return search(query, 0, 10);
  }

  public org.springframework.data.domain.Page<VehicleSearchDocument> findSimilarVehicles(String brand, String model,
      java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, org.springframework.data.domain.Pageable pageable) {
    BoolQuery.Builder bool = new BoolQuery.Builder();

    // Similarity Logic: Same Brand OR Same Model + Price Range
    bool.must(m -> m
        .bool(b -> b
            .should(s -> s.term(t -> t.field("brand.keyword").value(v -> v.stringValue(brand))))
            .should(s -> s.term(t -> t.field("model.keyword").value(v -> v.stringValue(model))))));

    if (minPrice != null || maxPrice != null) {
      bool.filter(f -> f
          .range(r -> r
              .field("price")
              .gte(org.opensearch.client.json.JsonData.of(minPrice))
              .lte(org.opensearch.client.json.JsonData.of(maxPrice))));
    }

    List<VehicleSearchDocument> results = search(bool.build()._toQuery(),
        pageable.getPageNumber() * pageable.getPageSize(), pageable.getPageSize());

    // Note: Returning Spring PageImpl ideally needs total count
    return new org.springframework.data.domain.PageImpl<>(results, pageable, results.size());
  }
}
