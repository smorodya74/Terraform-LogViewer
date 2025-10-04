package io.terraform.logviewer.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerraformLogParserTest {

    private TerraformLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new TerraformLogParser(new ObjectMapper());
    }

    @Test
    void recognizesPlanSectionFromMessage() {
        String json = """
                {"timestamp":"2024-05-10T12:00:00Z","message":"Starting Terraform plan for prod"}
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.section()).isEqualTo("plan");
        assertThat(record.timestamp()).isEqualTo(OffsetDateTime.parse("2024-05-10T12:00:00Z"));
        assertThat(record.timestampGuessed()).isFalse();
    }

    @Test
    void recognizesApplySectionAndHttpBodies() {
        String json = """
                {
                  "timestamp":"2024-05-11T15:30:45Z",
                  "message":"Terraform apply complete",
                  "http_request":{"path":"/apply","body":"{}"},
                  "http_response":{"status":200,"body":"{\\"ok\\":true}"}
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.section()).isEqualTo("apply");
        assertThat(record.bodies()).extracting(ParsedLogRecord.ParsedPayload::kind)
                .containsExactlyInAnyOrder("request", "response");
        assertThat(record.bodies()).extracting(ParsedLogRecord.ParsedPayload::json)
                .anySatisfy(body -> assertThat(body).contains("apply"))
                .anySatisfy(body -> assertThat(body).contains("ok"));
    }

    @Test
    void fallsBackToContextWhenTimestampAndLevelMissing() {
        OffsetDateTime lastTs = OffsetDateTime.parse("2024-05-12T08:00:00Z");
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(lastTs, "WARN");

        ParsedLogRecord record = parser.parse("{\"message\":\"no timestamp here\"}", context);

        assertThat(record.timestamp()).isEqualTo(lastTs);
        assertThat(record.timestampGuessed()).isTrue();
        assertThat(record.level()).isEqualTo("WARN");
        assertThat(record.levelGuessed()).isTrue();
    }

    @Test
    void extractsTimestampAndLevelFromPlainText() {
        String line = "2024-05-13T09:15:30Z [error] Terraform APPLY failed";
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(line, context);

        assertThat(record.timestamp()).isEqualTo(OffsetDateTime.parse("2024-05-13T09:15:30Z"));
        assertThat(record.timestampGuessed()).isFalse();
        assertThat(record.level()).isEqualTo("ERROR");
        assertThat(record.levelGuessed()).isFalse();
        assertThat(record.section()).isEqualTo("apply");
        assertThat(record.message()).contains("APPLY failed");
    }

    @Test
    void resolvesSectionFromNestedFieldAndExtractsModule() {
        String json = """
                {
                  "timestamp":"2024-05-15T10:00:00Z",
                  "terraform":{"phase":"PLAN","module_addr":"module.network"},
                  "message":"terraform event"
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.section()).isEqualTo("plan");
        assertThat(record.module()).isEqualTo("module.network");
    }

    @Test
    void extractsIdsAndHttpMetadataFromNestedObjectsAndTokens() {
        String json = """
                {
                  "timestamp":"2024-05-16T11:30:00Z",
                  "message":"rpc=provider.ApplyResourceChange req_id=req-123 trans_id=trans-456 status=201",
                  "details":{
                    "rpc":{"name":"provider.ApplyResourceChange"},
                    "http":{"method":"post","status_code":"201"}
                  },
                  "terraform":{
                    "tf_resource_type":"aws_s3_bucket"
                  }
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.rpc()).isEqualTo("provider.ApplyResourceChange");
        assertThat(record.reqId()).isEqualTo("req-123");
        assertThat(record.transactionId()).isEqualTo("trans-456");
        assertThat(record.resourceType()).isEqualTo("aws_s3_bucket");
        assertThat(record.httpOperationType()).isEqualTo("POST");
        assertThat(record.statusCode()).isEqualTo(201);
    }

    @Test
    void detectsPlanSectionFromDryRunHints() {
        String json = """
                {
                  "timestamp":"2024-05-17T09:45:00Z",
                  "message":"Executing terraform in dry-run mode to preview infrastructure changes"
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.section()).isEqualTo("plan");
    }

    @Test
    void detectsApplySectionFromResourceCreationMessages() {
        String line = "module.app.aws_s3_bucket.assets: Creation complete after 2s [id=bucket-123]";
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(line, context);

        assertThat(record.section()).isEqualTo("apply");
    }

    @Test
    void detectsApplySectionFromCliArguments() {
        String json = """
                {
                  "timestamp":"2024-05-18T12:00:00Z",
                  "terraform":{
                    "cli_args":["terraform","apply","-auto-approve"]
                  },
                  "message":"Terraform execution starting"
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.section()).isEqualTo("apply");
    }

    @Test
    void capturesBodiesFromNestedPayloadsAndIgnoresIds() {
        String json = """
                {
                  "timestamp":"2024-05-19T14:20:00Z",
                  "details":{
                    "payload":{
                      "rpc_request":{"foo":"bar"},
                      "rpc_response":{"bar":"baz"},
                      "error_body":"{\\"error\\":\\"boom\\",\\"code\\":500}"
                    },
                    "metadata":{
                      "request_id":"req-should-not-appear"
                    }
                  }
                }
                """;
        TerraformLogParser.ImportContext context = new TerraformLogParser.ImportContext(null, null);

        ParsedLogRecord record = parser.parse(json, context);

        assertThat(record.bodies()).extracting(ParsedLogRecord.ParsedPayload::kind)
                .contains("request", "response", "error");
        assertThat(record.bodies()).extracting(ParsedLogRecord.ParsedPayload::json)
                .anySatisfy(body -> assertThat(body).contains("\"foo\":\"bar\""))
                .anySatisfy(body -> assertThat(body).contains("\"bar\":\"baz\""))
                .anySatisfy(body -> assertThat(body).contains("\"error\":\"boom\""));
        assertThat(record.bodies())
                .noneMatch(payload -> payload.json().contains("req-should-not-appear"));
    }
}
