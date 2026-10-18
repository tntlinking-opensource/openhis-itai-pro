/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.validator;

import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 住院就诊出院结算轨写门控（就诊 {@link EncounterStatus#PENDING}/{@link EncounterStatus#COMPLETED}/{@link EncounterStatus#DISCHARGED}）。
 *
 * <p>护士办理出院后进入 {@code PENDING} 待结算窗口，医嘱执行/开立/划价等写路径须拒绝（BR-IP-FLW-008）。</p>
 */
@Component
public class InpatientPendingSettlementClinicalGate {

    /**
     * 断言就诊处于可变更医嘱的临床态（在科/待出院），允许写路径继续。
     *
     * @param statusEnum 就诊状态 code
     * @param errorCode  业务语义码
     * @param message    用户提示
     * @throws BusinessException 已办理出院待结算/已结算/已离院时抛出
     */
    public void assertEncounterAllowsOrderClinicalMutation(
        String statusEnum,
        String errorCode,
        String message
    ) {
        String code = StringUtils.trimToNull(statusEnum);
        if (EncounterStatus.PENDING.getCode().equals(code)) {
            throw new BusinessException(errorCode, message);
        }
        if (EncounterStatus.COMPLETED.getCode().equals(code)) {
            throw new BusinessException(errorCode, message);
        }
        if (EncounterStatus.DISCHARGED.getCode().equals(code)) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 批量断言就诊可变更医嘱（护士站执行/取消执行/划价写路径）。
     *
     * @param rows      可见就诊读模型
     * @param errorCode 业务语义码
     * @param message   用户提示
     * @throws BusinessException 任一就诊不可变更时抛出
     */
    public void assertEncountersAllowOrderClinicalMutation(
        Collection<InpatientEncounterContextQueryModel> rows,
        String errorCode,
        String message
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            this.assertEncounterAllowsOrderClinicalMutation(row.getStatusEnum(), errorCode, message);
        }
    }
}
