package com.poc.transactions_consumer_canonical.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import com.poc.transactions_consumer_canonical.repository.Converters;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypedRowMappersTest {

    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 5, 26, 10, 15, 30);
    private static final LocalDate DATE = LocalDate.of(1990, 6, 15);

    @Test
    void sendTranDtlMapperMapsScalarAndTimestampColumns() throws Exception {
        ResultSet rs = resultSetWithStringsAndTimestamp();
        when(rs.getObject("ACQ_ICA", Long.class)).thenReturn(123456L);

        SendTranDtl row = new SendTranDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getTranId()).isEqualTo("TRAN_ID-value");
        assertThat(row.getPaymtRef()).isEqualTo("PAYMT_REF-value");
        assertThat(row.getAcqIca()).isEqualTo(123456L);
        assertThat(row.getBncGtwyRqst()).isEqualTo("BNC_GTWY_RQST-value");
        assertThat(row.getEventTs()).isEqualTo(DATE_TIME);
        assertThat(row.getRplctnUpdtTs()).isEqualTo(DATE_TIME);
    }

    @Test
    void sendTranDtlMapperAllowsNullTimestamps() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getObject("ACQ_ICA", Long.class)).thenReturn(null);
        when(rs.getTimestamp(anyString())).thenReturn(null);

        SendTranDtl row = new SendTranDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getCrteTs()).isNull();
        assertThat(row.getEventTs()).isNull();
    }

    @Test
    void sendRecipDtlMapperMapsSenderRecipientDateAndAuditColumns() throws Exception {
        ResultSet rs = resultSetWithStringsAndTimestamp();
        when(rs.getDate(anyString())).thenReturn(Date.valueOf(DATE));

        SendRecipDtl row = new SendRecipDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getTranId()).isEqualTo("TRAN_ID-value");
        assertThat(row.getSendFirstNam()).isEqualTo("SEND_FIRST_NAM-value");
        assertThat(row.getSendDob()).isEqualTo(DATE);
        assertThat(row.getRecipFirstNam()).isEqualTo("RECIP_FIRST_NAM-value");
        assertThat(row.getRecipDob()).isEqualTo(DATE);
        assertThat(row.getTranCrteDt()).isEqualTo(DATE_TIME);
    }

    @Test
    void sendRecipDtlMapperAllowsNullDatesAndTimestamps() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getDate(anyString())).thenReturn(null);
        when(rs.getTimestamp(anyString())).thenReturn(null);

        SendRecipDtl row = new SendRecipDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getSendDob()).isNull();
        assertThat(row.getRecipDob()).isNull();
        assertThat(row.getCrteTs()).isNull();
    }

    @Test
    void sendTranAddrDtlMapperMapsAddressAndAuditColumns() throws Exception {
        ResultSet rs = resultSetWithStringsAndTimestamp();

        SendTranAddrDtl row = new SendTranAddrDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getId()).isEqualTo("ID-value");
        assertThat(row.getTranId()).isEqualTo("TRAN_ID-value");
        assertThat(row.getAddrType()).isEqualTo("ADDR_TYPE-value");
        assertThat(row.getCity()).isEqualTo("CITY-value");
        assertThat(row.getRplctnUpdtTs()).isEqualTo(DATE_TIME);
    }

    @Test
    void sendTranAddrDtlMapperAllowsNullTimestamps() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenReturn(null);
        when(rs.getTimestamp(anyString())).thenReturn(null);

        SendTranAddrDtl row = new SendTranAddrDtlRowMapper().mapRow(rs, 0);

        assertThat(row.getCrteTs()).isNull();
        assertThat(row.getUpdtTs()).isNull();
        assertThat(row.getRplctnUpdtTs()).isNull();
    }

    @Test
    void sendTransactionRowMapperBindsMetadataMapOntoTypedModelAndIgnoresUnknownColumns()
            throws Exception {
        TableMetadata table = TableMetadata.builder()
                .name("SEND_TRANSACTIONS")
                .alias("send-transactions")
                .pk("TRAN_ID")
                .pkJsonName("tranId")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("tranId").dbColumn("TRAN_ID")
                                .sqlType("VARCHAR").pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("tranAmt").dbColumn("TRAN_AMT")
                                .sqlType("NUMERIC").build(),
                        ColumnMetadata.builder().jsonName("tranCrteDt").dbColumn("TRAN_CRTE_DT")
                                .sqlType("TIMESTAMP").build(),
                        ColumnMetadata.builder().jsonName("nonFinTxn").dbColumn("NON_FIN_TXN")
                                .sqlType("INTEGER").converter("BOOLEAN_AS_INT").build(),
                        ColumnMetadata.builder().jsonName("futureOnly").dbColumn("FUTURE_ONLY")
                                .sqlType("VARCHAR").build()
                ))
                .build();
        MetadataRegistry registry = mock(MetadataRegistry.class);
        when(registry.require("SEND_TRANSACTIONS")).thenReturn(table);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SendTransactionRowMapper rowMapper =
                new SendTransactionRowMapper(registry, new Converters(mapper), mapper);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("TRAN_ID")).thenReturn("T-1");
        when(rs.getBigDecimal("TRAN_AMT")).thenReturn(new BigDecimal("10.25"));
        when(rs.getTimestamp("TRAN_CRTE_DT")).thenReturn(Timestamp.valueOf(DATE_TIME));
        when(rs.getObject("NON_FIN_TXN", Integer.class)).thenReturn(1);
        when(rs.getString("FUTURE_ONLY")).thenReturn("ignored");

        SendTransaction row = rowMapper.mapRow(rs, 0);

        assertThat(row.getTranId()).isEqualTo("T-1");
        assertThat(row.getTranAmt()).isEqualByComparingTo("10.25");
        assertThat(row.getTranCrteDt()).isNotNull();
        assertThat(row.getNonFinTxn()).isTrue();
    }

    private ResultSet resultSetWithStringsAndTimestamp() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(invocation -> invocation.getArgument(0) + "-value");
        when(rs.getTimestamp(anyString())).thenReturn(Timestamp.valueOf(DATE_TIME));
        return rs;
    }
}
