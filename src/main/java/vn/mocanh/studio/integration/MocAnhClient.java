package vn.mocanh.studio.integration;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import vn.mocanh.studio.Json;

/** Use only in the main website backend, after authenticating its customer. */
public final class MocAnhClient {
  private final RestClient client;

  public MocAnhClient(String baseUrl, String integrationKey) {
    client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-Integration-Key", integrationKey)
            .build();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> catalog() {
    return client.get().uri("/api/v1/shop/catalog").retrieve().body(Map.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> createMix(String customerId, Map<String, Object> recipe) {
    return client
        .post()
        .uri("/api/v1/shop/mixes")
        .header("X-Customer-Id", customerId)
        .body(recipe)
        .retrieve()
        .body(Map.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> mixStatus(String customerId, String id) {
    return client
        .get()
        .uri("/api/v1/shop/mixes/{id}", id)
        .header("X-Customer-Id", customerId)
        .retrieve()
        .body(Map.class);
  }

  public byte[] mixImage(String customerId, String id) {
    return client
        .get()
        .uri("/api/v1/shop/mixes/{id}/image", id)
        .header("X-Customer-Id", customerId)
        .retrieve()
        .body(byte[].class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> sendRequest(String customerId, Map<String, Object> metadata) {
    var form = new LinkedMultiValueMap<String, Object>();
    form.add("metadata", Json.write(metadata));
    return client
        .post()
        .uri("/api/v1/shop/requests")
        .header("X-Customer-Id", customerId)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .body(Map.class);
  }
}
