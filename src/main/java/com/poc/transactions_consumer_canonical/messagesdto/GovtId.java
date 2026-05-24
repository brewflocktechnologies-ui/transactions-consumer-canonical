package com.poc.transactions_consumer_canonical.messagesdto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GovtId {

    private String type;
    private String value;
    private String country;

    public GovtId() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
