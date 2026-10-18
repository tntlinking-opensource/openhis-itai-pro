/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

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
     * <p>医技工作站诊疗/耗材划价与冲销专用；与护士划价分码，费用明细来源列展示「医技划价」/
     * 「医技冲销」。</p>
     */
    MEDICAL_TECH_PRICING(0L, "medical-tech-pricing", "医技划价", "医技工作站诊疗或耗材划价录入产生。", 3L),

    /** 挂号登记时自动产生。 */
    REGISTRATION_REGISTER(0L, "registration", "挂号登记", "挂号登记流程自动带出。", 4L),

    /** 医嘱执行环节产生。 */
    ORDER_EXECUTE(0L, "order-execute", "医嘱执行", "医嘱执行、补记等执行侧产生。", 5L),

    /** 住院自动滚费产生。 */
    AUTO_ROLL_FEES(0L, "auto-roll-fees", "自动滚费", "住院自动滚费任务产生。", 6L),

    /** 团检套餐懒生成挂费（AS-PE-FIN-001）。 */
    PHYSICAL_EXAM_GROUP(0L, "physical-exam-group", "团检套餐", "团检收费页按合同/套餐懒生成。", 7L),

    /**
     * 麻醉执行（GenerateSource，持久化编码 {@code anesthesia-execute}）。
     *
     * <p>麻醉申请（AA）项目在护士站执行入账；费用明细来源列展示「麻醉执行」。
     * 与 {@link #ORDER_EXECUTE} 分码，便于麻醉统计与来源识别。</p>
     */
    ANESTHESIA_EXECUTE(0L, "anesthesia-execute", "麻醉执行", "麻醉申请项目执行入账产生。", 8L),

    /**
     * 麻醉划价（GenerateSource，持久化编码 {@code anesthesia-pricing}）。
     *
     * <p>手麻计费中挂靠麻醉申请父项目的护士划价入账；费用明细来源列展示「麻醉划价」。
     * 与 {@link #NURSE_PRICING} 分码；上游 Request 仍可落 nurse-pricing 以复用划价列表/冲销。</p>
     */
    ANESTHESIA_PRICING(0L, "anesthesia-pricing", "麻醉划价", "手麻计费挂麻醉申请父项的划价产生。", 9L);

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

    /**
     * 是否护士站划价族（普通护士划价或麻醉划价）。
     *
     * @return true=护士划价或麻醉划价
     */
    public boolean isNurseStationPricingFamily() {
        return this == NURSE_PRICING || this == ANESTHESIA_PRICING;
    }

    /**
     * 是否执行入账族（普通医嘱执行或麻醉执行）。
     *
     * @return true=医嘱执行或麻醉执行
     */
    public boolean isOrderExecuteFamily() {
        return this == ORDER_EXECUTE || this == ANESTHESIA_EXECUTE;
    }

    /**
     * 按持久化 code 判定是否护士站划价族。
     *
     * @param generateSourceEnum 生成来源 code；可空
     * @return true=护士划价或麻醉划价
     */
    public static boolean isNurseStationPricingFamilyCode(String generateSourceEnum) {
        if (generateSourceEnum == null || generateSourceEnum.isEmpty()) {
            return false;
        }
        if (NURSE_PRICING.code.equals(generateSourceEnum)) {
            return true;
        }
        return ANESTHESIA_PRICING.code.equals(generateSourceEnum);
    }

    /**
     * 按持久化 code 判定是否执行入账族。
     *
     * @param generateSourceEnum 生成来源 code；可空
     * @return true=医嘱执行或麻醉执行
     */
    public static boolean isOrderExecuteFamilyCode(String generateSourceEnum) {
        if (generateSourceEnum == null || generateSourceEnum.isEmpty()) {
            return false;
        }
        if (ORDER_EXECUTE.code.equals(generateSourceEnum)) {
            return true;
        }
        return ANESTHESIA_EXECUTE.code.equals(generateSourceEnum);
    }

}
