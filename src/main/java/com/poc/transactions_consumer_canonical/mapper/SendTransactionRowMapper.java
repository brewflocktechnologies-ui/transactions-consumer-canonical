package com.poc.transactions_consumer_canonical.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import com.poc.transactions_consumer_canonical.repository.Converters;
import com.poc.transactions_consumer_canonical.repository.GenericRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Metadata-driven row mapper for the {@code SEND_TRANSACTIONS} table.
 *
 * <p>Reading is delegated to {@link GenericRowMapper} (which uses
 * {@code send_transactions.yaml} to discover columns, JDBC types and converters),
 * then Jackson binds the resulting {@code jsonName → value} map onto a
 * {@link SendTransaction} POJO.
 *
 * <h3>Adding a new column</h3>
 * <ol>
 *   <li>Add the column in the table DDL.</li>
 *   <li>Add a row in {@code metadata/send_transactions.yaml}
 *       (set {@code jsonName} to match the POJO field name).</li>
 * </ol>
 * The mapper picks it up automatically — no Java change required here.
 * (If the new value also needs to be exposed through APIs, add the field on
 * {@link SendTransaction} / the request/response DTOs; otherwise it is silently
 * ignored thanks to {@code FAIL_ON_UNKNOWN_PROPERTIES = false}.)
 */
@Slf4j
@Component
public class SendTransactionRowMapper implements RowMapper<SendTransaction> {

    private static final String TABLE_NAME = "SEND_TRANSACTIONS";

    private final GenericRowMapper delegate;
    private final ObjectMapper binder;

    public SendTransactionRowMapper(MetadataRegistry registry,
                                    Converters converters,
                                    ObjectMapper jsonMapper) {
        TableMetadata table = registry.require(TABLE_NAME);
        this.delegate = new GenericRowMapper(table, converters);
        // Use a copy so we can relax unknown-property handling without affecting
        // the shared web-layer mapper.
        this.binder = jsonMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature
                                   .FAIL_ON_UNKNOWN_PROPERTIES, false);
        log.info("[SendTransactionRowMapper] Metadata-driven; {} column(s) wired from {}",
                table.getColumns().size(), TABLE_NAME);
    }

    @Override
    public SendTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = delegate.mapRow(rs, rowNum);
        if (row == null) return null;
        return binder.convertValue(row, SendTransaction.class);
    }
}
