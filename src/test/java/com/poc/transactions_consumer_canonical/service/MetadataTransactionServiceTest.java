package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.metadata.ChildMetadata;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataTransactionServiceTest {

    private MetadataRegistry registry;
    private GenericTableRepository repo;
    private MetadataTransactionService service;
    private TableMetadata parent;
    private TableMetadata oneToOneChild;
    private TableMetadata oneToManyChild;

    @BeforeEach
    void setUp() {
        registry = mock(MetadataRegistry.class);
        repo = mock(GenericTableRepository.class);
        service = new MetadataTransactionService(registry, repo);

        oneToOneChild = TableMetadata.builder().name("CHILD_11").alias("c11")
                .pk("CHILD_ID").pkJsonName("childId")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("childId").dbColumn("CHILD_ID")
                                .sqlType("VARCHAR").pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("tranId").dbColumn("TRAN_ID").sqlType("VARCHAR").build()))
                .build();
        oneToManyChild = TableMetadata.builder().name("CHILD_1M").alias("c1m")
                .pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("tranId").dbColumn("TRAN_ID").sqlType("VARCHAR").build()))
                .build();
        parent = TableMetadata.builder().name("PARENT").alias("parent")
                .pk("TRAN_ID").pkJsonName("tranId")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("tranId").dbColumn("TRAN_ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("name").dbColumn("NAME").sqlType("VARCHAR").build()))
                .children(List.of(
                        ChildMetadata.builder().jsonName("oneOne").tableRef("CHILD_11")
                                .cardinality("ONE_TO_ONE").childKey("TRAN_ID").build(),
                        ChildMetadata.builder().jsonName("oneMany").tableRef("CHILD_1M")
                                .cardinality("ONE_TO_MANY").childKey("TRAN_ID")
                                .idJsonName("id").generateIdIfMissing(true).build()))
                .build();
        parent.validate();
        oneToOneChild.validate();
        oneToManyChild.validate();

        when(registry.require("parent")).thenReturn(parent);
        when(registry.require("CHILD_11")).thenReturn(oneToOneChild);
        when(registry.require("CHILD_1M")).thenReturn(oneToManyChild);
    }

    @Test
    void findByPk_absent_returnsEmpty() {
        when(repo.findByPk("PARENT", "X")).thenReturn(Optional.empty());
        assertThat(service.findByPk("parent", "X")).isEmpty();
    }

    @Test
    void findByPk_present_attachesOneToOneAndOneToManyChildren() {
        Map<String, Object> parentRow = new HashMap<>();
        parentRow.put("tranId", "X");
        parentRow.put("name", "Alice");

        when(repo.findByPk("PARENT", "X")).thenReturn(Optional.of(parentRow));
        when(repo.findByFk("CHILD_11", "TRAN_ID", "X"))
                .thenReturn(Optional.of(Map.of("childId", "C-1")));
        when(repo.findAllByFk("CHILD_1M", "TRAN_ID", "X"))
                .thenReturn(List.of(Map.of("id", "M-1"), Map.of("id", "M-2")));

        Optional<Map<String, Object>> out = service.findByPk("parent", "X");

        assertThat(out).isPresent();
        Map<String, Object> result = out.get();
        assertThat(result).containsEntry("oneOne", Map.of("childId", "C-1"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> many = (List<Map<String, Object>>) result.get("oneMany");
        assertThat(many).hasSize(2);
    }

    @Test
    void findByPk_oneToOneChildAbsent_storedAsNull() {
        when(repo.findByPk("PARENT", "X")).thenReturn(Optional.of(new HashMap<>(Map.of("tranId", "X"))));
        when(repo.findByFk("CHILD_11", "TRAN_ID", "X")).thenReturn(Optional.empty());
        when(repo.findAllByFk("CHILD_1M", "TRAN_ID", "X")).thenReturn(List.of());

        Optional<Map<String, Object>> out = service.findByPk("parent", "X");
        assertThat(out).isPresent();
        assertThat(out.get().get("oneOne")).isNull();
        assertThat(out.get().get("oneMany")).isInstanceOf(List.class);
    }
}
