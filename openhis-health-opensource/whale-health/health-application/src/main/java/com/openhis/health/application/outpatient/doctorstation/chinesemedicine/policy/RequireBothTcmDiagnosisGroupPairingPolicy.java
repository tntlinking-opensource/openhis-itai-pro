/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.policy;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 产品默认：每组须同时具备病名与证候（BR-TCM-DIA-001 / 一病一证）。
 *
 * <p>院点若需放宽，提供 {@code @Primary} 的同接口实现即可，无需改 AppService。</p>
 */
@Component
public class RequireBothTcmDiagnosisGroupPairingPolicy implements TcmDiagnosisGroupPairingPolicy {

    /**
     * 校验单组是否同时具备病名与证候目录主键。
     *
     * @param diseaseDefinitionId  病名目录主键
     * @param syndromeDefinitionId 证候目录主键
     * @throws BusinessException 任一侧为空时拒绝
     */
    @Override
    public void assertGroupAcceptable(Long diseaseDefinitionId, Long syndromeDefinitionId) {
        // Gate：一病一证成组，任一侧缺失即拒绝保存
        if (diseaseDefinitionId == null || syndromeDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "每组须同时选择病名与证候");
        }
    }
}
