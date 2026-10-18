/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 医技工作站·医技执行（AS-MT-EXE-001）常量与异常码。
 *
 * <p>异常码与 AS §5 对齐；就诊 scope 与可见性轴口径见可见性 DD / 协同 DD。</p>
 */
public interface MedicalTechExecutionConstants {

    /** 就诊类别 scope：门诊。 */
    String ENCOUNTER_SCOPE_OUTPATIENT = "OUTPATIENT";

    /** 就诊类别 scope：住院。 */
    String ENCOUNTER_SCOPE_INPATIENT = "INPATIENT";

    /**
     * 住院队列在院态筛选：在院（默认；{@code IN_PROGRESS}/{@code ON_HOLD}/{@code PENDING}）。
     */
    String INPATIENT_STAY_FILTER_IN_HOSPITAL = "IN_HOSPITAL";

    /**
     * 住院队列在院态筛选：不在院（已清床离院 {@code DISCHARGED}）。
     */
    String INPATIENT_STAY_FILTER_NOT_IN_HOSPITAL = "NOT_IN_HOSPITAL";

    /**
     * 执行态筛选：待执行（队列整单分桶 + 执行单行级收窄；门诊另含待发耗材）。
     *
     * <p>对齐 AS-MT-EXE-001 **BR-MT-EXE-023**（IF-01 队列 / IF-02 右侧项目）。</p>
     */
    String EXECUTION_QUEUE_FILTER_PENDING = "PENDING";

    /**
     * 执行态筛选：已执行（队列整单分桶 + 执行单行级收窄；门诊另要求非待发耗材；住院不看耗材）。
     *
     * <p>对齐 AS-MT-EXE-001 **BR-MT-EXE-023**（IF-01 队列 / IF-02 右侧项目）。</p>
     */
    String EXECUTION_QUEUE_FILTER_EXECUTED = "EXECUTED";

    /** 医技执行完成结果摘要占位（满足完成门禁，与护士站同构）。 */
    String MEDICAL_TECH_PROCEDURE_RESULT_TEXT = "医技执行完成";

    /** 队列查询失败或非 TECH 登录（IF-MT-EXE-01）。 */
    String EX_MT_EXE_101 = "EX-MT-EXE-101";

    /** 执行单查询失败或就诊对本站不可见（IF-MT-EXE-02）。 */
    String EX_MT_EXE_102 = "EX-MT-EXE-102";

    /** 执行记录查询失败（IF-MT-EXE-03 / 03B）。 */
    String EX_MT_EXE_103 = "EX-MT-EXE-103";

    /**
     * 门诊诊疗未计费（IF-MT-EXE-04）。
     *
     * <p>亦用于本站归属校验失败等写前拒绝（与护士站「未计费」同族提示）。</p>
     */
    String EX_MT_EXE_201 = "EX-MT-EXE-201";

    /**
     * 预留：住院执行侧同态（软落地后不再使用）。
     *
     * <p>产品口径：执行不要求先后；IF-04 不抛本码。出院仅卡医技相关费用确费（EX-IP-ATD-309）。</p>
     */
    String EX_MT_EXE_202 = "EX-MT-EXE-202";

    /** 执行次数已满（IF-MT-EXE-04）。 */
    String EX_MT_EXE_203 = "EX-MT-EXE-203";

    /** 耗材库存不足（IF-MT-EXE-04）。 */
    String EX_MT_EXE_204 = "EX-MT-EXE-204";

    /** 发放态并发冲突（IF-MT-EXE-04）。 */
    String EX_MT_EXE_205 = "EX-MT-EXE-205";

    /**
     * 住院就诊已办理出院待结算 / 已结算 / 已离院，禁止医技批量执行或取消执行（IF-MT-EXE-04/05）。
     *
     * <p>与护士站 {@code EX-IP-EXE-307}、医生站 {@code EX-IP-ORD-107} 同轴（BR-IP-FLW-008）。</p>
     */
    String EX_MT_EXE_206 = "EX-MT-EXE-206";

    /**
     * 住院待结算轨禁止医技执行/取消的用户提示（与护士站/医生站文案对齐）。
     */
    String MSG_ENCOUNTER_PENDING_SETTLEMENT_EXECUTION_FORBIDDEN =
        "患者已办理出院或已结算，不可变更医嘱及费用";

    /** 取消目标非法（IF-MT-EXE-05）。 */
    String EX_MT_EXE_211 = "EX-MT-EXE-211";

    /** 取消已对冲（IF-MT-EXE-05）。 */
    String EX_MT_EXE_212 = "EX-MT-EXE-212";

    /** 耗材取消态不符（IF-MT-EXE-05）。 */
    String EX_MT_EXE_213 = "EX-MT-EXE-213";

    /**
     * 预留：医技取消同态（软落地后不再使用）。
     *
     * <p>IF-05 不抛本码；护士取消同态亦已废止。</p>
     */
    String EX_MT_EXE_214 = "EX-MT-EXE-214";
}
