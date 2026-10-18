/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.constants.InpatientEncounterStatusDisplayConstants;
import com.zxplat.domain.share.enums.EncounterStatus;

/**
 * 住院（{@code IMP}）{@code status_enum_text} 解析工具（DM-ADM-002 §2.3.2）。
 *
 * <p>门诊共用 {@link EncounterStatus#getDisplay()}；住院读模型与应用层出参须使用本工具，
 * 与 {@link com.zxplat.domain.share.constants.InpatientEncounterStatusDisplayConstants} 对齐。</p>
 */
public final class InpatientEncounterStatusDisplayUtil {

    private InpatientEncounterStatusDisplayUtil() {
    }

    /**
     * 按状态码与读模型辅助列解析 IMP 产品展示文案。
     *
     * @param statusEnum 住院状态码（事件或读模型 {@code status_enum}）
     * @param transferOrgId 转科目标科室（{@code ON_HOLD} 分流用，可空）
     * @param dischargeTypeEnum 出院类型（{@code ON_HOLD} 分流用，可空）
     * @return 展示文案；无法识别时返回 {@code null}
     */
    public static String resolveDisplayText(
        String statusEnum,
        Long transferOrgId,
        String dischargeTypeEnum) {
        if (statusEnum == null || statusEnum.isBlank()) {
            return null;
        }
        String code = statusEnum.trim();
        // ON_HOLD 须结合转科目标或出院类型分流，禁止单一「在院」文案。
        if (EncounterStatus.ON_HOLD.getCode().equals(code)) {
            if (transferOrgId != null) {
                return InpatientEncounterStatusDisplayConstants.ON_HOLD_TRANSFER;
            }
            if (dischargeTypeEnum != null && !dischargeTypeEnum.isBlank()) {
                return InpatientEncounterStatusDisplayConstants.ON_HOLD_DISCHARGE;
            }
            return null;
        }
        if (EncounterStatus.PLANNED.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.PLANNED;
        }
        if (EncounterStatus.REGISTERED.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.REGISTERED;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.IN_PROGRESS;
        }
        if (EncounterStatus.PENDING.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.PENDING;
        }
        if (EncounterStatus.COMPLETED.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.COMPLETED;
        }
        if (EncounterStatus.DISCHARGED.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.DISCHARGED;
        }
        if (EncounterStatus.CANCELLED.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.CANCELLED;
        }
        if (EncounterStatus.PRE_ADMISSION.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.PRE_ADMISSION;
        }
        if (EncounterStatus.ENTERED_IN_ERROR.getCode().equals(code)) {
            return InpatientEncounterStatusDisplayConstants.ENTERED_IN_ERROR;
        }
        return null;
    }

    /**
     * 从读模型当前列解析 IMP 状态展示文案。
     *
     * @param statusEnum 读模型 {@code status_enum}
     * @param transferOrgId 读模型 {@code transfer_org_id}
     * @param dischargeTypeEnum 读模型 {@code discharge_type_enum}
     * @return 展示文案；入参无效时返回 {@code null}
     */
    public static String resolveDisplayTextForReadModel(
        String statusEnum,
        Long transferOrgId,
        String dischargeTypeEnum) {
        return resolveDisplayText(statusEnum, transferOrgId, dischargeTypeEnum);
    }
}
