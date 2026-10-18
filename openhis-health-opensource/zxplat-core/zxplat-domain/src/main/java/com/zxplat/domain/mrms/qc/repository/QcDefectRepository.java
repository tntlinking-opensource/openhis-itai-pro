/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.qc.repository;

import com.zxplat.domain.mrms.qc.entity.QcDefect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 质控缺陷仓储。
 */
public interface QcDefectRepository extends JpaRepository<QcDefect, Long> {

    /**
     * 按病案列出缺陷，创建时间倒序。
     *
     * @param caseRecordId 病案主档主键
     * @return 缺陷列表
     */
    List<QcDefect> findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc(Long caseRecordId);

    /**
     * 按病案与状态集合查询活跃缺陷。
     *
     * @param caseRecordId 病案主档主键
     * @param statusCodes 状态 code 集合
     * @return 匹配的缺陷列表
     */
    List<QcDefect> findByCaseRecordIdAndStatusCodeInAndIsDeletedFalse(
            Long caseRecordId, Collection<String> statusCodes);

    /**
     * 按病案+规则+状态查唯一 open 缺陷（幂等 upsert）。
     *
     * @param caseRecordId 病案主档主键
     * @param ruleCode 规则 code
     * @param statusCode 缺陷状态 code
     * @return 存在则含实体，否则 empty
     */
    Optional<QcDefect> findByCaseRecordIdAndRuleCodeAndStatusCodeAndIsDeletedFalse(
            Long caseRecordId, String ruleCode, String statusCode);

    /**
     * 统计指定状态与严重级别的缺陷数（归档阻断校验）。
     *
     * @param caseRecordId 病案主档主键
     * @param statusCodes 状态 code 集合
     * @param severityCode 严重级别 code
     * @return 命中条数
     */
    long countByCaseRecordIdAndStatusCodeInAndSeverityCodeAndIsDeletedFalse(
            Long caseRecordId, Collection<String> statusCodes, String severityCode);

    /**
     * 按主键加载未软删缺陷。
     *
     * @param id 缺陷主键
     * @return 存在则含实体，否则 empty
     */
    Optional<QcDefect> findByIdAndIsDeletedFalse(Long id);
}
