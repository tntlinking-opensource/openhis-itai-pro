/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 1101 人员信息当日缓存 {@code yb_person_cache} 仓储 */
public interface YbPersonCacheRepository extends JpaRepository<YbPersonCache, Long> {

    /**
     * 按契约、凭证类型与凭证号查找人员缓存行。
     *
     * @param contractId    契约 ID
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param tenantId      租户 ID
     * @return 人员缓存行
     */
    Optional<YbPersonCache> findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalse(
        Long contractId, String mdtrtCertType, String mdtrtCertNo, Long tenantId);
}
