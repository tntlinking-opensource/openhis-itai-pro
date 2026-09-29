/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 医嘱生成来源枚举。
 *
 * <p>标识医嘱请求（药品/服务/器械）及收费明细由何种业务场景产生，对应库表字段
 * {@code generate_source_enum}。持久化与事件载荷身份一律使用 {@link #getCode()} 英文编码
 *（与 DM-MED-003 §4.5 约定一致，禁止数字串作身份）。</p>
 *
 * <p>{@link #getSortOrder()} 仅用于展示排序，不得作为库表或业务分支的身份依据。</p>
 */
@Getter
public enum GenerateSource implements EnumInterface {

    /** 医生在医生站等业务端开立医嘱。 */
    DOCTOR_PRESCRIPTION(0L, "doctor-prescription", "医生开立", "医生开立医嘱或处方。", 1L),

    /** 护士划价（GenerateSource，持久化编码 {@code nurse-pricing}）。 */
    NURSE_PRICING(0L, "nurse-pricing", "护士划价", "护士站划价录入产生。", 2L),

    /**
     * 医技划价（GenerateSource，持久化编码 {@code medical-tech-pricing}）。
     *
     * <p>医技工作站耗材划价/冲销专用；与护士划价分码，费用明细来源列展示「医技划价」/
     * 「医技冲销」。</p>
     */
    MEDICAL_TECH_PRICING(0L, "medical-tech-pricing", "医技划价", "医技工作站耗材划价录入产生。", 3L),

    /** 挂号登记时自动产生。 */
    REGISTRATION_REGISTER(0L, "registration", "挂号登记", "挂号登记流程自动带出。", 4L),

    /** 医嘱执行环节产生。 */
    ORDER_EXECUTE(0L, "order-execute", "医嘱执行", "医嘱执行、补记等执行侧产生。", 5L),

    /** 住院自动滚费产生。 */
    AUTO_ROLL_FEES(0L, "auto-roll-fees", "自动滚费", "住院自动滚费任务产生。", 6L),

    /** 团检套餐懒生成挂费（AS-PE-FIN-001）。 */
    PHYSICAL_EXAM_GROUP(0L, "physical-exam-group", "团检套餐", "团检收费页按合同/套餐懒生成。", 7L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    GenerateSource(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
