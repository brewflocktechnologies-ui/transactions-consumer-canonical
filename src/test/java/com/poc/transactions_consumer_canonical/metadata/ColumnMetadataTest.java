package com.poc.transactions_consumer_canonical.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnMetadataTest {

    @Test
    void jdbcTypes() {
        ColumnMetadata c = new ColumnMetadata();
        c.setDbColumn("C");
        c.setSqlType("VARCHAR");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.VARCHAR);
        c.setSqlType("CLOB");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.VARCHAR);
        c.setSqlType("NUMERIC");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.NUMERIC);
        c.setSqlType("INTEGER");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.INTEGER);
        c.setSqlType("TIMESTAMP");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.TIMESTAMP);
        c.setSqlType("DATE");
        assertThat(c.jdbcType()).isEqualTo(java.sql.Types.DATE);
    }

    @Test
    void jdbcTypeUnknown() {
        ColumnMetadata c = new ColumnMetadata();
        c.setDbColumn("X");
        c.setSqlType("UNKNOWN");
        assertThatThrownBy(c::jdbcType).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown sqlType");
    }

    @Test
    void nullGuardAndConverter() {
        ColumnMetadata c = new ColumnMetadata();
        c.setNullGuard(null);
        assertThat(c.isNullGuard()).isTrue();
        c.setNullGuard(false);
        assertThat(c.isNullGuard()).isFalse();
        c.setConverter(null);
        assertThat(c.converterName()).isEqualTo("PASSTHROUGH");
        c.setConverter(" ");
        assertThat(c.converterName()).isEqualTo("PASSTHROUGH");
        c.setConverter("BOOLEAN_AS_INT");
        assertThat(c.converterName()).isEqualTo("BOOLEAN_AS_INT");
    }
}
