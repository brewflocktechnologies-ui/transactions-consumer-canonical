package com.poc.transactions_consumer_canonical.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * springdoc-openapi configuration. Generates the OpenAPI 3 document at
 * {@code /v3/api-docs} and serves the interactive UI at {@code /swagger-ui.html}.
 *
 * <p>The v2 generic controller exposes {@code Map<String,Object>} payloads, so
 * the schema there is intentionally weak. Clients should call
 * {@code GET /api/v2/_metadata/{alias}} for the authoritative column list.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:transactions-consumer-canonical}")
    private String appName;

    @Bean
    public OpenAPI sendTransactionsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .description("""
                                **v1** — typed POJO endpoints under `/api/v1/send-transactions/*`.
                                Full request/response schemas are generated from DTO classes.

                                **v2** — generic metadata-driven endpoints under `/api/v2/{alias}/{id}`.
                                Payloads are JSON objects whose shape follows the YAML metadata
                                under `classpath:metadata/`. Call `GET /api/v2/_metadata/{alias}`
                                for the authoritative column list of any registered table.

                                Both versions are intentionally kept live to support a phased
                                migration of clients.""")
                        .version("v1+v2")
                        .contact(new Contact().name("Transactions Platform Team"))
                        .license(new License().name("Proprietary").url("https://example.com/license")))
                .externalDocs(new ExternalDocumentation()
                        .description("Architecture & ops runbook (README)")
                        .url("/"))
                .tags(List.of(
                        new Tag().name("send-transactions-v1")
                                .description("Typed POJO endpoints — stable contract, schema-validated."),
                        new Tag().name("send-transactions-v2")
                                .description("Generic metadata-driven endpoints — adding a column is a YAML edit, no code change."),
                        new Tag().name("metadata-discovery")
                                .description("Introspect the YAML-defined column lists, audit/CLOB/converter flags, child references.")
                ));
    }
}
