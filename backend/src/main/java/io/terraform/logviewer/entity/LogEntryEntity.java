package io.terraform.logviewer.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tf_log_entries")
@Getter
@Setter
@NoArgsConstructor
public class LogEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "level", length = 16)
    private String level;

    @Column(name = "section", length = 16)
    private String section;

    @Column(name = "module", length = 255)
    private String module;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "req_id", length = 128)
    private String reqId;

    @Column(name = "trans_id", length = 128)
    private String transactionId;

    @Column(name = "rpc", length = 255)
    private String rpc;

    @Column(name = "resource_type", length = 255)
    private String resourceType;

    @Column(name = "data_source_type", length = 255)
    private String dataSourceType;

    @Column(name = "http_op_type", length = 32)
    private String httpOperationType;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "import_id", length = 64)
    private String importId;

    @Column(name = "unread", nullable = false)
    private boolean unread = true;

    @Lob
    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Lob
    @Column(name = "attrs_json", columnDefinition = "text")
    private String attrsJson;

    @Lob
    @Column(name = "annotations_json", columnDefinition = "text")
    private String annotationsJson;
}
