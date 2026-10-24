package com.vidopos.order.outbox;

import com.vidopos.order.config.OutboxProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxClaimer {

    private static final String CLAIM_DUE = """
            WITH candidate AS (
                SELECT id
                FROM outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= now())
                   OR (status = 'PROCESSING' AND locked_until < now())
                ORDER BY next_attempt_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE outbox AS o
            SET status = 'PROCESSING',
                locked_until = now() + (:leaseSeconds * interval '1 second'),
                attempt_count = o.attempt_count + 1,
                updated_at = now()
            FROM candidate
            WHERE o.id = candidate.id
            RETURNING o.id
            """;

    private static final String CLAIM_ORDER = """
            WITH candidate AS (
                SELECT id
                FROM outbox
                WHERE order_id = :orderId
                  AND (
                    status = 'PENDING'
                    OR (status = 'PROCESSING' AND locked_until < now())
                  )
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE outbox AS o
            SET status = 'PROCESSING',
                locked_until = now() + (:leaseSeconds * interval '1 second'),
                attempt_count = o.attempt_count + 1,
                updated_at = now()
            FROM candidate
            WHERE o.id = candidate.id
            RETURNING o.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxProperties properties;

    public OutboxClaimer(NamedParameterJdbcTemplate jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    public List<UUID> claimDue() {
        return claim(CLAIM_DUE, new MapSqlParameterSource("leaseSeconds", properties.leaseSeconds()));
    }

    @Transactional
    public List<UUID> claimOrder(UUID orderId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("leaseSeconds", properties.leaseSeconds())
                .addValue("orderId", orderId);
        return claim(CLAIM_ORDER, params);
    }

    private List<UUID> claim(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }
}
