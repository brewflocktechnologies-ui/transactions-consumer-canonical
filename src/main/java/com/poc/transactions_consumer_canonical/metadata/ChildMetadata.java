package com.poc.transactions_consumer_canonical.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for a child-table reference on a parent table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChildMetadata {

    /** JSON field name in the request/response payload (e.g. "tranDtl", "addrDtl"). */
    private String jsonName;

    /** DB table name of the child (e.g. "SEND_TRAN_DTL"). Must resolve via MetadataRegistry. */
    private String tableRef;

    /** ONE_TO_ONE or ONE_TO_MANY. */
    private String cardinality;

    /** DB column on the child that references the parent PK (e.g. "TRAN_ID"). */
    private String childKey;

    /** For 1:many — JSON name of the child's own PK (e.g. "id"). */
    private String idJsonName;

    /** For 1:many — when true, service generates a UUID if the client did not provide an id. */
    private Boolean generateIdIfMissing;

    public boolean isGenerateIdIfMissing() { return Boolean.TRUE.equals(generateIdIfMissing); }
    public boolean isOneToOne()  { return "ONE_TO_ONE".equalsIgnoreCase(cardinality); }
    public boolean isOneToMany() { return "ONE_TO_MANY".equalsIgnoreCase(cardinality); }
}
