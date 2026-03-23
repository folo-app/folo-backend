package com.folo.stock;

import com.folo.config.OpendartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpendartClientTest {

    @Test
    void fetchCorpCodesParsesZipXmlPayload() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/api/corpCode.xml");
                    assertThat(request.getURI().getQuery()).contains("crtfc_key=test-api-key");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(corpCodeZipPayload(), MediaType.APPLICATION_OCTET_STREAM));

        OpendartClient client = new OpendartClient(builder, properties());
        Map<String, OpendartCorpCodeRecord> records = client.fetchCorpCodes();

        assertThat(records).containsKey("005930");
        assertThat(records.get("005930").corpCode()).isEqualTo("00126380");
        assertThat(records.get("005930").corpName()).isEqualTo("삼성전자");

        server.verify();
    }

    @Test
    void fetchCompanyParsesCompanyJsonPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/api/company.json");
                    assertThat(request.getURI().getQuery()).contains("corp_code=00126380");
                    assertThat(request.getURI().getQuery()).contains("crtfc_key=test-api-key");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": "000",
                          "message": "정상",
                          "corp_name": "삼성전자",
                          "stock_code": "005930",
                          "corp_cls": "Y",
                          "hm_url": "www.samsung.com/sec",
                          "ir_url": "https://www.samsung.com/global/ir/",
                          "induty_code": "264"
                        }
                        """, MediaType.APPLICATION_JSON));

        OpendartClient client = new OpendartClient(builder, properties());
        OpendartCompanyRecord company = client.fetchCompany("00126380");

        assertThat(company.corpName()).isEqualTo("삼성전자");
        assertThat(company.stockCode()).isEqualTo("005930");
        assertThat(company.hmUrl()).isEqualTo("https://www.samsung.com/sec");
        assertThat(company.irUrl()).isEqualTo("https://www.samsung.com/global/ir/");
        assertThat(company.indutyCode()).isEqualTo("264");

        server.verify();
    }

    private OpendartProperties properties() {
        return new OpendartProperties(true, "test-api-key", "https://opendart.fss.or.kr/api");
    }

    private byte[] corpCodeZipPayload() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                  <list>
                    <corp_code>00126380</corp_code>
                    <corp_name>삼성전자</corp_name>
                    <corp_eng_name>Samsung Electronics Co., Ltd.</corp_eng_name>
                    <stock_code>005930</stock_code>
                    <modify_date>20260320</modify_date>
                  </list>
                </result>
                """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }
}
