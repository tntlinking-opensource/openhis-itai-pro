/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.share;

import java.util.Set;

/**
 * 前置软件 receive 路径段常量（P10-C/D scheme 参考实现）。
 *
 * <p><b>职责：</b>集中维护 20 种 EMR/base 资源在 receive URL 中的路径段，与 bridge {@code Emr*Mapper.resource()} 对齐。</p>
 * <p><b>实现位置：</b>本类（gateway {@code scheme.push.share}）</p>
 * <p><b>调用方：</b>bridge {@code EmrPayloadMapperRegistry}、gateway Client 组装 receive 路径</p>
 * <p><b>架构边界：</b>gateway 纯常量，不依赖 bridge/application；bridge mapper 引用本常量而非硬编码字符串。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
public final class CrbEmrResource {

    /** receive 路径段：患者基本信息（JSON key 与常量同名）。 */
    public static final String EMR_PATIENT_INFO = "emrPatientInfo";
    /** receive 路径段：诊疗活动信息。 */
    public static final String EMR_ACTIVITY_INFO = "emrActivityInfo";
    /** receive 路径段：入院信息。 */
    public static final String EMR_ADMISSION_INFO = "emrAdmissionInfo";
    /** receive 路径段：入院记录。 */
    public static final String EMR_ADMISSION_RECORD = "emrAdmissionRecord";
    /** receive 路径段：日常病程。 */
    public static final String EMR_DAILY_COURSE = "emrDailyCourse";
    /** receive 路径段：死亡信息。 */
    public static final String EMR_DEATH_INFO = "emrDeathInfo";
    /** receive 路径段：出院信息。 */
    public static final String EMR_DISCHARGE_INFO = "emrDischargeInfo";
    /** receive 路径段：检查报告。 */
    public static final String EMR_EX_CLINICAL = "emrExClinical";
    /** receive 路径段：检查报告明细。 */
    public static final String EMR_EX_CLINICAL_ITEM = "emrExClinicalItem";
    /** receive 路径段：检验报告。 */
    public static final String EMR_EX_LAB = "emrExLab";
    /** receive 路径段：检验报告明细。 */
    public static final String EMR_EX_LAB_ITEM = "emrExLabItem";
    /** receive 路径段：首次病程。 */
    public static final String EMR_FIRST_COURSE = "emrFirstCourse";
    /** receive 路径段：传染病报告。 */
    public static final String EMR_INF_REPORT = "emrInfReport";
    /** receive 路径段：医嘱。 */
    public static final String EMR_ORDER = "emrOrder";
    /** receive 路径段：医嘱明细。 */
    public static final String EMR_ORDER_ITEM = "emrOrderItem";
    /** receive 路径段：门诊观察。 */
    public static final String EMR_OUTPATIENT_OBS = "emrOutpatientObs";
    /** receive 路径段：门（急）诊病历。 */
    public static final String EMR_OUTPATIENT_RECORD = "emrOutpatientRecord";
    /** receive 路径段：生命体征记录。 */
    public static final String EMR_VITAL_SIGNS_RECORD = "emrVitalSignsRecord";
    /** receive 路径段：科室基础数据。 */
    public static final String BASE_DEPT = "baseDept";
    /** receive 路径段：用户基础数据。 */
    public static final String BASE_USER = "baseUser";

    /** 已注册 mapper 的 resource 集合（P10-C/D 参考实现覆盖 {@link #EMR_PATIENT_INFO}）。 */
    public static final Set<String> KNOWN_RESOURCES = Set.of(
            EMR_PATIENT_INFO,
            EMR_ACTIVITY_INFO,
            EMR_ADMISSION_INFO,
            EMR_ADMISSION_RECORD,
            EMR_DAILY_COURSE,
            EMR_DEATH_INFO,
            EMR_DISCHARGE_INFO,
            EMR_EX_CLINICAL,
            EMR_EX_CLINICAL_ITEM,
            EMR_EX_LAB,
            EMR_EX_LAB_ITEM,
            EMR_FIRST_COURSE,
            EMR_INF_REPORT,
            EMR_ORDER,
            EMR_ORDER_ITEM,
            EMR_OUTPATIENT_OBS,
            EMR_OUTPATIENT_RECORD,
            EMR_VITAL_SIGNS_RECORD,
            BASE_DEPT,
            BASE_USER
    );

    private CrbEmrResource() {
    }
}
