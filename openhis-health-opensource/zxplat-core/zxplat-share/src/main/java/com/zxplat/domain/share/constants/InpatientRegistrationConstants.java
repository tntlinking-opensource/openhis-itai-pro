/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院登记读侧列表筛选常量（对齐 AS-IP-FIN-001 / SDD-GAP-001）。
 */
public interface InpatientRegistrationConstants {

    /** 待登记：{@code status_enum = PLANNED}。 */
    String LIST_STATUS_PLANNED = "PLANNED";

    /** 待入科：{@code status_enum = REGISTERED}。 */
    String LIST_STATUS_WAIT_ADMISSION = "WAIT_ADMISSION";

    /** 已入科：{@code status_enum = IN_PROGRESS}。 */
    String LIST_STATUS_ADMITTED = "ADMITTED";

    /** 预入院：{@code status_enum = PRE_ADMISSION}。 */
    String LIST_STATUS_PRE_ADMISSION = "PRE_ADMISSION";

    /** 暂离（待转科/待出院）：{@code status_enum = ON_HOLD}。 */
    String LIST_STATUS_ON_HOLD = "ON_HOLD";

    /** 待结算：{@code status_enum = PENDING}。 */
    String LIST_STATUS_PENDING = "PENDING";

    /** 已结算：{@code status_enum = COMPLETED}。 */
    String LIST_STATUS_COMPLETED = "COMPLETED";

    /** 已出院：{@code status_enum = DISCHARGED}。 */
    String LIST_STATUS_DISCHARGED = "DISCHARGED";

    /** 取消登记：{@code status_enum = CANCELLED}。 */
    String LIST_STATUS_CANCELLED = "CANCELLED";

    /** 错误录入：{@code status_enum = ENTERED_IN_ERROR}。 */
    String LIST_STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    /** 缺省列表筛选（待登记）；仅作历史常量，登记列表未传状态时查全部登记口径。 */
    String DEFAULT_LIST_STATUS = LIST_STATUS_PLANNED;

    /**
     * 登记列表筛选码 → 持久化 {@code status_enum}。
     *
     * @param registrationListStatus 筛选码
     * @return 就诊状态码；无法识别时返回 null
     */
    static String toEncounterStatusCode(String registrationListStatus) {
        if (registrationListStatus == null || registrationListStatus.isBlank()) {
            return null;
        }
        String code = registrationListStatus.trim();
        if (LIST_STATUS_WAIT_ADMISSION.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.REGISTERED.getCode();
        }
        if (LIST_STATUS_ADMITTED.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.IN_PROGRESS.getCode();
        }
        if (LIST_STATUS_PLANNED.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.PLANNED.getCode();
        }
        if (LIST_STATUS_PRE_ADMISSION.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.PRE_ADMISSION.getCode();
        }
        if (LIST_STATUS_ON_HOLD.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.ON_HOLD.getCode();
        }
        if (LIST_STATUS_PENDING.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.PENDING.getCode();
        }
        if (LIST_STATUS_COMPLETED.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.COMPLETED.getCode();
        }
        if (LIST_STATUS_DISCHARGED.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.DISCHARGED.getCode();
        }
        if (LIST_STATUS_CANCELLED.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.CANCELLED.getCode();
        }
        if (LIST_STATUS_ENTERED_IN_ERROR.equals(code)) {
            return com.zxplat.domain.share.enums.EncounterStatus.ENTERED_IN_ERROR.getCode();
        }
        return null;
    }

    /**
     * 是否合法的登记列表筛选码。
     *
     * @param registrationListStatus 筛选码
     * @return 合法则为 true
     */
    static boolean isValidRegistrationListStatus(String registrationListStatus) {
        return toEncounterStatusCode(registrationListStatus) != null;
    }

    /** 门诊转住院默认入院途径码（与医保 adm_way / {@code in_way_code} code=2「门诊」对齐）。 */
    String DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER = "2";

    /** 登记必填项缺失（AS-IP-FIN-001 IF-IP-REG-03）。 */
    String EX_IP_REG_001 = "EX-IP-REG-001";

    /** 科室/病区/费用性质无效。 */
    String EX_IP_REG_002 = "EX-IP-REG-002";

    /** 就诊状态不允许登记。 */
    String EX_IP_REG_003 = "EX-IP-REG-003";

    /** 预交金收款失败（整单回滚）。 */
    String EX_IP_REG_004 = "EX-IP-REG-004";

    /** 取消时预交金余额大于 0。 */
    String EX_IP_REG_005 = "EX-IP-REG-005";

    /** 已分床或重复取消。 */
    String EX_IP_REG_006 = "EX-IP-REG-006";

    /** 列表查询参数无效。 */
    String EX_IP_REG_101 = "EX-IP-REG-101";

    /** 就诊不存在或读模型未投影。 */
    String EX_IP_REG_102 = "EX-IP-REG-102";

    /** 已是医保入院，无需转医保。 */
    String EX_IP_REG_201 = "EX-IP-REG-201";

    /** 就诊状态不允许转医保。 */
    String EX_IP_REG_202 = "EX-IP-REG-202";

    /** 存在已结算费用，不可转医保。 */
    String EX_IP_REG_203 = "EX-IP-REG-203";

    /** 转医保须先读卡获取人员编号。 */
    String EX_IP_REG_204 = "EX-IP-REG-204";

    /** 转医保须选择有医保编码的主治医生。 */
    String EX_IP_REG_205 = "EX-IP-REG-205";

    /** 医保扩展未启用或费用性质非第三方合同。 */
    String EX_IP_REG_206 = "EX-IP-REG-206";

    /** 就诊状态或已结算费用不允许更正入院科室。 */
    String EX_IP_REG_207 = "EX-IP-REG-207";

    /** 更正入院科室时床位不属于目标病区，或待入科误传床位。 */
    String EX_IP_REG_208 = "EX-IP-REG-208";

    /** 已医保入院但无法发送住院信息变更（缺科室医保码、人员编号或身份证）。 */
    String EX_IP_REG_209 = "EX-IP-REG-209";

    /** 当前不是医保入院，或缺少个人现金户，不能转自费。 */
    String EX_IP_REG_210 = "EX-IP-REG-210";

    /** 医保平台撤销入院或费用明细失败，本地费用未改挂。 */
    String EX_IP_REG_211 = "EX-IP-REG-211";
}
