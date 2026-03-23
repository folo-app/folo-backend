package com.folo.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.folo.config.OpendartProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class OpendartClient {

    private static final String CORP_CODE_PAYLOAD_VERSION = "opendart:v1/corpCode";
    private static final String COMPANY_PAYLOAD_VERSION = "opendart:v1/company";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final OpendartProperties properties;

    public OpendartClient(
            RestClient.Builder restClientBuilder,
            OpendartProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.enabled()
                && StringUtils.hasText(properties.apiKey())
                && StringUtils.hasText(properties.baseUrl());
    }

    public Map<String, OpendartCorpCodeRecord> fetchCorpCodes() {
        ensureConfigured();
        URI requestUri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/corpCode.xml")
                .queryParam("crtfc_key", properties.apiKey())
                .build()
                .encode()
                .toUri();

        byte[] body = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(byte[].class);

        if (body == null || body.length == 0) {
            throw new IllegalStateException("OPENDART corpCode.xml response is empty");
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(body),
                StandardCharsets.UTF_8
        )) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null) {
                throw new IllegalStateException("OPENDART corpCode.xml zip payload has no entries");
            }

            Reader reader = new InputStreamReader(zipInputStream, StandardCharsets.UTF_8);
            Document document = secureDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new InputSource(reader));
            NodeList items = document.getElementsByTagName("list");
            Map<String, OpendartCorpCodeRecord> records = new LinkedHashMap<>();
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
                String stockCode = normalizeText(childText(item, "stock_code"));
                String corpCode = normalizeText(childText(item, "corp_code"));
                if (!StringUtils.hasText(stockCode) || !StringUtils.hasText(corpCode)) {
                    continue;
                }

                records.put(
                        stockCode,
                        new OpendartCorpCodeRecord(
                                corpCode,
                                normalizeText(childText(item, "corp_name")),
                                stockCode,
                                parseDate(childText(item, "modify_date")),
                                CORP_CODE_PAYLOAD_VERSION
                        )
                );
            }
            return records;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse OPENDART corpCode.xml payload", exception);
        }
    }

    public OpendartCompanyRecord fetchCompany(String corpCode) {
        ensureConfigured();
        URI requestUri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/company.json")
                .queryParam("crtfc_key", properties.apiKey())
                .queryParam("corp_code", corpCode)
                .build()
                .encode()
                .toUri();

        JsonNode response = restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.isNull()) {
            throw new IllegalStateException("OPENDART company.json response is empty");
        }

        String status = readText(response, "status");
        String message = readText(response, "message");
        if ("013".equals(status)) {
            return null;
        }
        if (!"000".equals(status)) {
            throw new IllegalStateException("OPENDART company.json failed: %s %s".formatted(status, message));
        }

        return new OpendartCompanyRecord(
                corpCode,
                normalizeText(readText(response, "corp_name")),
                normalizeText(readText(response, "stock_code")),
                normalizeText(readText(response, "corp_cls")),
                normalizeUrl(readText(response, "hm_url")),
                normalizeUrl(readText(response, "ir_url")),
                normalizeText(readText(response, "induty_code")),
                COMPANY_PAYLOAD_VERSION
        );
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENDART is not configured");
        }
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private String childText(Element parent, String tagName) {
        NodeList childNodes = parent.getElementsByTagName(tagName);
        if (childNodes.getLength() == 0) {
            return null;
        }
        return childNodes.item(0).getTextContent();
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText(null);
    }

    private LocalDate parseDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim(), BASIC_DATE);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.equals("-") ? null : normalized;
    }

    private String normalizeUrl(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }
}
