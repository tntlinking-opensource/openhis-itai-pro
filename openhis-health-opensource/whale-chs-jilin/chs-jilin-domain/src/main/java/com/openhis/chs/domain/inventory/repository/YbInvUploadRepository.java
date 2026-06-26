/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 进销存上报流水仓储。 */
public interface YbInvUploadRepository extends JpaRepository<YbInvUpload, Long> {

    Optional<YbInvUpload> findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
        Long tenantId,
        String infno,
        String fixmedinsBchno,
        Long sourceLineId
    );

    Optional<YbInvUpload> findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
        Long tenantId,
        String infno,
        Long sourceLineId,
        String uploadStatus
    );

    List<YbInvUpload> findAllByTenantIdAndSourceTypeAndSourceIdOrderByIdAsc(
        Long tenantId,
        String sourceType,
        Long sourceId
    );

    List<YbInvUpload> findAllByTenantIdAndInfnoOrderByIdDesc(
        Long tenantId,
        String infno,
        Pageable pageable
    );

    List<YbInvUpload> findAllByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);
}
