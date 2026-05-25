package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extra coverage for {@link ValueConverter} branches not already exercised in
 * {@link ValueConverterTest}.
 */
class ValueConverterEdgeCasesTest {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private final ValueConverter passthrough = new ValueConverter.Passthrough(json);
    private final ValueConverter booleanAsInt = new ValueConverter.BooleanAsInt();

    private ColumnMetadata col(String sqlType) {
        return ColumnMetadata.builder().sqlType(sqlType).dbColumn("X").build();
    }

    @Test
    void passthrough_LocalDateTime_inputBypassesConversion() {
        LocalDateTime ldt = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        Object out = passthrough.toJdbc(ldt, col("TIMESTAMP"));
        assertThat(out).isSameAs(ldt);
    }

    @Test
    void passthrough_LocalDate_inputBypassesConversion() {
        LocalDate ld = LocalDate.of(1990, 5, 15);
        Object out = passthrough.toJdbc(ld, col("DATE"));
        assertThat(out).isSameAs(ld);
    }

    @Test
    void passthrough_VARCHAR_nonStringIsToString() {
        Object out = passthrough.toJdbc(42, col("VARCHAR"));
        assertThat(out).isEqualTo("42");
    }

    @Test
    void passthrough_VARCHAR_passesStringThrough() {
        Object out = passthrough.toJdbc("hello", col("VARCHAR"));
        assertThat(out).isEqualTo("hello");
    }

    @Test
    void passthrough_NUMERIC_acceptsBigDecimal() {
        BigDecimal bd = new BigDecimal("99.99");
        Object out = passthrough.toJdbc(bd, col("NUMERIC"));
        assertThat(out).isSameAs(bd);
    }

    @Test
    void passthrough_INTEGER_intInput() {
        assertThat(passthrough.toJdbc(7, col("INTEGER"))).isEqualTo(7);
    }

    @Test
    void passthrough_INTEGER_longInputCoercedDown() {
        assertThat(passthrough.toJdbc(8L, col("INTEGER"))).isEqualTo(8);
    }

    @Test
    void passthrough_INTEGER_stringParsed() {
        assertThat(passthrough.toJdbc("9", col("INTEGER"))).isEqualTo(9);
    }

    @Test
    void passthrough_nullSqlType_treatedAsVarchar() {
        assertThat(passthrough.toJdbc("x", ColumnMetadata.builder().build())).isEqualTo("x");
    }

    @Test
    void passthrough_fromJdbc_returnsDbValueDirectly() {
        BigDecimal bd = BigDecimal.TEN;
        assertThat(passthrough.fromJdbc(bd, col("NUMERIC"))).isSameAs(bd);
        assertThat(passthrough.fromJdbc(null, col("NUMERIC"))).isNull();
    }

    @Test
    void booleanAsInt_acceptsNumericInput() {
        ColumnMetadata c = col("NUMERIC");
        assertThat(booleanAsInt.toJdbc(0, c)).isEqualTo(0);
        assertThat(booleanAsInt.toJdbc(1, c)).isEqualTo(1);
        assertThat(booleanAsInt.toJdbc(5, c)).isEqualTo(1); // truthy
    }

    @Test
    void booleanAsInt_caseInsensitiveStrings() {
        ColumnMetadata c = col("NUMERIC");
        assertThat(booleanAsInt.toJdbc("True", c)).isEqualTo(1);
        assertThat(booleanAsInt.toJdbc("FALSE", c)).isEqualTo(0);
    }

    @Test
    void booleanAsInt_rejectsUnsupportedType() {
        ColumnMetadata column = col("NUMERIC");
        Object unsupported = new Object();

        assertThatThrownBy(() -> booleanAsInt.toJdbc(unsupported, column))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOLEAN_AS_INT");
    }

    @Test
    void booleanAsInt_fromJdbc_handlesBooleanInput() {
        assertThat(booleanAsInt.fromJdbc(Boolean.TRUE, col("NUMERIC"))).isEqualTo(Boolean.TRUE);
    }

    @Test
    void booleanAsInt_fromJdbc_returnsNullForUnknownType() {
        assertThat(booleanAsInt.fromJdbc("not-a-number", col("NUMERIC"))).isNull();
    }
}
