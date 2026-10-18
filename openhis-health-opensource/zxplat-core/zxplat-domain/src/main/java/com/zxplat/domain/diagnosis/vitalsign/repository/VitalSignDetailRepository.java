/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.repository;

import com.zxplat.domain.diagnosis.vitalsign.entity.VitalSignDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 生命体征明细仓储（DM-DIA-002 v2.1 §4.5）。
 */
public interface VitalSignDetailRepository extends JpaRepository<VitalSignDetail, Long> {

    /**
     * 按主表 id 查询有效明细，术语体系与字典项升序。
     *
     * @param vitalSignId 主表 id
     * @return 明细列表
     */
    List<VitalSignDetail> findAllByVitalSignIdAndIsDeletedFalseOrderByTermCodeAscItemCodeAsc(Long vitalSignId);

    /**
     * 按主表 id 与自然键查询有效明细。
     *
     * @param vitalSignId 主表 id
     * @param termCode    术语体系 code
     * @param itemCode    字典项 code
     * @return 明细
     */
    Optional<VitalSignDetail> findByVitalSignIdAndTermCodeAndItemCodeAndIsDeletedFalse(
        Long vitalSignId, String termCode, String itemCode);

    /**
     * 按主表 id 集合查询有效明细。
     *
     * @param vitalSignIds 主表 id 集合
     * @return 明细列表
     */
    List<VitalSignDetail> findAllByVitalSignIdInAndIsDeletedFalse(Collection<Long> vitalSignIds);
}
