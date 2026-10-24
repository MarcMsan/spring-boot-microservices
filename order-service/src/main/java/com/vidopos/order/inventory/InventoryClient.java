package com.vidopos.order.inventory;

import com.vidopos.order.orders.CustomerOrder;
import com.vidopos.order.web.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    public ReservationResult reserve(CustomerOrder order) {
        ReserveStockRequest request = new ReserveStockRequest(
                order.getId(),
                order.getItems().stream()
                        .map(item -> new ReserveStockRequest.Item(item.getProductId(), item.getQuantity()))
                        .toList());
        try {
            ReservationResult result = restClient.post()
                    .uri("/api/reservations")
                    .header(Correlation.HEADER, order.getCorrelationId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ReservationResult.class);
            if (result == null || result.status() == null) {
                throw new RetryableReservationException("Inventory returned an empty reservation response");
            }
            log.info(
                    "Inventory responded orderId={} status={} correlationId={}",
                    order.getId(),
                    result.status(),
                    order.getCorrelationId());
            return result;
        } catch (RestClientResponseException exception) {
            int code = exception.getStatusCode().value();
            if (code == 408 || code == 429 || code >= 500) {
                throw new RetryableReservationException("Inventory unavailable (HTTP " + code + ")", exception);
            }
            throw new TerminalReservationException(
                    "Inventory rejected the reservation call (HTTP " + code + ")");
        } catch (ResourceAccessException exception) {
            throw new RetryableReservationException("Inventory unavailable: " + exception.getMessage(), exception);
        }
    }
}
