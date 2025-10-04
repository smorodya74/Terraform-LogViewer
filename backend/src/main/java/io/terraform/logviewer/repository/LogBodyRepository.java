package io.terraform.logviewer.repository;

import io.terraform.logviewer.entity.LogBodyEntity;
import io.terraform.logviewer.entity.LogEntryEntity;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogBodyRepository extends JpaRepository<LogBodyEntity, Long> {

    Stream<LogBodyEntity> findByLogEntry(LogEntryEntity entry);
}
