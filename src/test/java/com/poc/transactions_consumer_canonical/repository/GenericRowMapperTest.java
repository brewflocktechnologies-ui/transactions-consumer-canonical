package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericRowMapperTest {

    private Converters converters;
    private TableMetadata table;
    private GenericRowMapper mapper;

    @BeforeEach
    void setUp() {
        converters = new Converters(new ObjectMapper());
        table = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID")
                                .sqlType("VARCHAR").pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("amt").dbColumn("AMT").sqlType("NUMERIC").build(),
                        ColumnMetadata.builder().jsonName("cnt").dbColumn("CNT").sqlType("INTEGER").build(),
                        ColumnMetadata.builder().jsonName("ts").dbColumn("TS").sqlType("TIMESTAMP").build(),
                        ColumnMetadata.builder().jsonName("dob").dbColumn("DOB").sqlType("DATE").build(),
                        ColumnMetadata.builder().jsonName("clob").dbColumn("CLOB_COL").sqlType("CLOB").build(),
                        // jsonName-null column → emitted as nothing
                        ColumnMetadata.builder().dbColumn("INTERNAL_ONLY").sqlType("VARCHAR").build(),
                        // blank jsonName → also skipped
                        ColumnMetadata.builder().jsonName("  ").dbColumn("INTERNAL_TWO").sqlType("VARCHAR").build(),
                        // unknown sqlType → default branch hits rs.getObject
                        ColumnMetadata.builder().jsonName("misc").dbColumn("MISC").sqlType("OTHER").build(),
                        // dbColumn blank → readRaw returns null
                        ColumnMetadata.builder().jsonName("noDb").dbColumn(" ").sqlType("VARCHAR").build()
                ))
                .build();
        mapper = new GenericRowMapper(table, converters);
    }

    @Test
    void mapsAllTypesByDbColumnAndJsonName() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("X-1");
        when(rs.getBigDecimal("AMT")).thenReturn(new BigDecimal("99.99"));
        when(rs.getObject("CNT", Integer.class)).thenReturn(7);
        when(rs.getTimestamp("TS")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2024, 1, 2, 3, 4, 5)));
        when(rs.getDate("DOB")).thenReturn(Date.valueOf(LocalDate.of(1990, 5, 15)));
        when(rs.getString("CLOB_COL")).thenReturn("clob-text");
        when(rs.getObject("MISC")).thenReturn("anything");

        Map<String, Object> row = mapper.mapRow(rs, 0);

        assertThat(row)
                .containsKeys("ts", "noDb")
                .containsEntry("id", "X-1")
                .containsEntry("amt", new BigDecimal("99.99"))
                .containsEntry("cnt", 7)
                .containsEntry("dob", LocalDate.of(1990, 5, 15))
                .containsEntry("clob", "clob-text")
                .containsEntry("misc", "anything")
                .doesNotContainKeys("internalOnly", "INTERNAL_ONLY");
        assertThat(row.get("ts")).isInstanceOf(LocalDateTime.class);
        assertThat(row.get("noDb")).isNull();
    }

    @Test
    void timestampNull_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("TS")).thenReturn(null);
        when(rs.getDate("DOB")).thenReturn(null);

        Map<String, Object> row = mapper.mapRow(rs, 0);
        assertThat(row.get("ts")).isNull();
        assertThat(row.get("dob")).isNull();
    }

    @Test
    void unknownSqlTypeUsesGetObject() throws Exception {
        TableMetadata tiny = TableMetadata.builder()
                .name("Tiny").alias("tiny").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("blob").dbColumn("BLOB").sqlType(null).build()
                ))
                .build();
        GenericRowMapper m = new GenericRowMapper(tiny, converters);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ID")).thenReturn("x");
        when(rs.getObject("BLOB")).thenReturn(123);

        Map<String, Object> row = m.mapRow(rs, 0);
        assertThat(row).containsEntry("blob", 123);
    }
}
