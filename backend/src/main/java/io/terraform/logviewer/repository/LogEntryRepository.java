package io.terraform.logviewer.repository;

import io.terraform.logviewer.entity.LogEntryEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LogEntryRepository extends JpaRepository<LogEntryEntity, Long>,
        JpaSpecificationExecutor<LogEntryEntity> {

    Page<LogEntryEntity> findAll(Specification<LogEntryEntity> spec, Pageable pageable);

    @Query("select e from LogEntryEntity e where e.reqId = :reqId order by e.timestamp asc")
    List<LogEntryEntity> findAllByReqIdOrderByTimestampAsc(@Param("reqId") String reqId);

    @Modifying
    @Transactional
    @Query("update LogEntryEntity e set e.unread = :unread where e.id in :ids")
    int updateUnreadStatus(@Param("ids") List<Long> ids, @Param("unread") boolean unread);

    @Modifying
    @Transactional
    @Query("update LogEntryEntity e set e.unread = :unread where e.reqId = :reqId")
    int updateUnreadStatusByReqId(@Param("reqId") String reqId, @Param("unread") boolean unread);

    @Query("select min(e.timestamp) from LogEntryEntity e where (:reqId is null or e.reqId = :reqId)")
    Optional<OffsetDateTime> findMinTimestamp(@Param("reqId") String reqId);

    @Query("select max(e.timestamp) from LogEntryEntity e where (:reqId is null or e.reqId = :reqId)")
    Optional<OffsetDateTime> findMaxTimestamp(@Param("reqId") String reqId);

    interface ImportSummaryView {
        String getImportId();

        String getFileName();

        OffsetDateTime getFirstTimestamp();

        OffsetDateTime getLastTimestamp();

        long getTotal();
    }

    @Query("""
            select e.importId as importId,
                   coalesce(e.fileName, '') as fileName,
                   min(e.timestamp) as firstTimestamp,
                   max(e.timestamp) as lastTimestamp,
                   count(e) as total
            from LogEntryEntity e
            where e.importId is not null and e.importId <> ''
            group by e.importId, coalesce(e.fileName, '')
            order by max(e.timestamp) desc
            """)
    List<ImportSummaryView> findImportSummaries();
}
