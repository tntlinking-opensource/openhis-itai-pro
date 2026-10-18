/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * {@code encounter_diagnosis_ids} 有序 ids 串列的 JPA 精确 token 匹配谓词（避免 {@code LIKE %12%} 误匹配 112）。
 *
 * <p>与 {@link EncounterDiagnosisIdsSupport} 同属 ids 串协议；本类仅提供 Criteria 侧精确包含匹配。</p>
 */
public final class EncounterDiagnosisIdsPredicates {

    private EncounterDiagnosisIdsPredicates() {
    }

    /**
     * 构造「ids 串包含指定就诊诊断 id」谓词（等值 / 前缀 / 中缀 / 后缀四种形态）。
     *
     * @param cb                        CriteriaBuilder
     * @param encounterDiagnosisIdsPath 实体上的 {@code encounterDiagnosisIds} 路径
     * @param encounterDiagnosisId      目标 id
     * @return 谓词；id 为 null 时返回恒假
     */
    public static Predicate containsEncounterDiagnosisId(
        CriteriaBuilder cb,
        Path<String> encounterDiagnosisIdsPath,
        Long encounterDiagnosisId
    ) {
        if (encounterDiagnosisId == null) {
            return cb.disjunction();
        }
        String token = String.valueOf(encounterDiagnosisId);
        return cb.and(
            cb.isNotNull(encounterDiagnosisIdsPath),
            cb.or(
                cb.equal(encounterDiagnosisIdsPath, token),
                cb.like(encounterDiagnosisIdsPath, token + ",%"),
                cb.like(encounterDiagnosisIdsPath, "%," + token),
                cb.like(encounterDiagnosisIdsPath, "%," + token + ",%")
            )
        );
    }
}
