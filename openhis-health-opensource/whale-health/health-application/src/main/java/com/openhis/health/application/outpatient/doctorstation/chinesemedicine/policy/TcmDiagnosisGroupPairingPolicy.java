/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.policy;

/**
 * 中医病证组「成组完整性」策略：默认一病一证；院点可 {@code @Primary} 覆盖为允许单侧。
 *
 * <p>由门诊/住院 {@code *TcmAppService#saveTcmDiagnosisGroupsBatch} 在 Gate 阶段调用。</p>
 */
public interface TcmDiagnosisGroupPairingPolicy {

    /**
     * 校验单组病/证目录是否满足本院成组规则。
     *
     * @param diseaseDefinitionId  病名目录主键，可空
     * @param syndromeDefinitionId 证候目录主键，可空
     * @throws com.openhis.whale.core.application.exception.BusinessException 不满足成组规则时
     */
    void assertGroupAcceptable(Long diseaseDefinitionId, Long syndromeDefinitionId);
}
