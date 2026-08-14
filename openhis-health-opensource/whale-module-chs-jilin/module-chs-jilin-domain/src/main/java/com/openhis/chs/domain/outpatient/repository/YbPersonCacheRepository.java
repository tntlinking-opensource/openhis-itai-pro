/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 按契约查找未过期缓存（快照含身份证号时的回落查询，按缓存时间倒序）。
     *
     * @param contractId 契约 ID
     * @param tenantId   租户 ID
     * @param expireAt   须晚于该时刻（通常为 now）
     * @return 未过期缓存列表
     */
    List<YbPersonCache> findByContractIdAndTenantIdAndIsDeletedFalseAndExpireAtAfterOrderByCachedAtDesc(
        Long contractId, Long tenantId, LocalDateTime expireAt);

    /**
     * 按契约、凭证类型与凭证号查找最新一条人员缓存（含已过期，按 cached_at 倒序）。
     *
     * <p>供医生站险种展示：住院患者入院读卡后缓存可能已过日终，仍需回显职工/居民。</p>
     *
     * @param contractId    契约 ID
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param tenantId      租户 ID
     * @return 最新缓存行
     */
    Optional<YbPersonCache> findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalseOrderByCachedAtDesc(
        Long contractId, String mdtrtCertType, String mdtrtCertNo, Long tenantId);
}
