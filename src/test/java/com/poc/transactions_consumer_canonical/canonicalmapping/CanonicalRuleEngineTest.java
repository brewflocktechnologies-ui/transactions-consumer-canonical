package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalRuleEngineTest {

    private CanonicalRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CanonicalRuleEngine(new ObjectMapper());
    }

    private EventEnvelope env(String source, String metadata) {
        EventEnvelope e = new EventEnvelope();
        e.setEventSource(source);
        e.setEventMetadata(metadata);
        e.setEventName("EVT");
        return e;
    }

    private EventTypeMapping withRules(List<String> sources, List<String> ops) {
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("X");
        RulesConfig r = new RulesConfig();
        r.setAllowedEventSources(sources);
        r.setAllowedOperations(ops);
        m.setRules(r);
        return m;
    }

    @Test
    void nullRules_allows() {
        EventTypeMapping m = new EventTypeMapping();
        m.setRules(null);
        assertThat(engine.shouldProcess(m, env("ANY", "{}"))).isTrue();
    }

    @Test
    void allowedSource_allowsMatching_caseInsensitive() {
        EventTypeMapping m = withRules(List.of("ais_service"), null);
        assertThat(engine.shouldProcess(m, env("AIS_SERVICE", "{}"))).isTrue();
    }

    @Test
    void disallowedSource_blocks() {
        EventTypeMapping m = withRules(List.of("AIS_SERVICE"), null);
        assertThat(engine.shouldProcess(m, env("UNKNOWN", "{}"))).isFalse();
    }

    @Test
    void blankSourceWithRestriction_blocks() {
        EventTypeMapping m = withRules(List.of("AIS_SERVICE"), null);
        assertThat(engine.shouldProcess(m, env("  ", "{}"))).isFalse();
        assertThat(engine.shouldProcess(m, env(null, "{}"))).isFalse();
    }

    @Test
    void emptyAllowedSourcesList_isUnrestricted() {
        EventTypeMapping m = withRules(List.of(), List.of());
        assertThat(engine.shouldProcess(m, env("ANY", "{}"))).isTrue();
    }

    @Test
    void operationCheck_matchesFromMetadataJson() {
        EventTypeMapping m = withRules(null, List.of("A", "U"));
        assertThat(engine.shouldProcess(m, env("X", "{\"operation\":\"A\"}"))).isTrue();
        assertThat(engine.shouldProcess(m, env("X", "{\"operation\":\"U\"}"))).isTrue();
        assertThat(engine.shouldProcess(m, env("X", "{\"operation\":\"D\"}"))).isFalse();
    }

    @Test
    void operationCheck_caseInsensitive() {
        EventTypeMapping m = withRules(null, List.of("a"));
        assertThat(engine.shouldProcess(m, env("X", "{\"operation\":\"A\"}"))).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{}", "{\"operation\":null}", "<<not json>>"})
    void blankMissingNullOrInvalidMetadata_treatedAsBlank_blocks(String metadata) {
        EventTypeMapping m = withRules(null, List.of("A"));
        assertThat(engine.shouldProcess(m, env("X", metadata))).isFalse();
    }

    @Test
    void noRestrictionAnywhere_allows() {
        EventTypeMapping m = withRules(null, null);
        assertThat(engine.shouldProcess(m, env("X", "{\"operation\":\"D\"}"))).isTrue();
    }
}
