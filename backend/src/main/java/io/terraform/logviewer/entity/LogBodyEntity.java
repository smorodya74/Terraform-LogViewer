package io.terraform.logviewer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tf_log_bodies")
@Getter
@Setter
@NoArgsConstructor
public class LogBodyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private LogEntryEntity logEntry;

    @Column(name = "kind", length = 32, nullable = false)
    private String kind;

    @Column(name = "body_json", columnDefinition = "text")
    private String bodyJson;
}
