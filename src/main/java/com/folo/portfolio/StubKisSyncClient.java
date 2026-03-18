package com.folo.portfolio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folo.config.KisStubProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Component
public class StubKisSyncClient implements KisSyncClient {

    private final KisStubProperties kisStubProperties;
    private final ObjectMapper objectMapper;

    public StubKisSyncClient(KisStubProperties kisStubProperties, ObjectMapper objectMapper) {
        this.kisStubProperties = kisStubProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KisSyncTradePayload> syncTrades(String kisAppKey, String kisAppSecret) {
        try {
            if (kisStubProperties.stubFile() == null || kisStubProperties.stubFile().isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(Files.readString(Path.of(kisStubProperties.stubFile())), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("KIS stub sync failed", exception);
        }
    }
}
