/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.List;

/**
 * 门诊医生站「诊断录入」跨层共享常量：中西医筛选入参、目录来源分组、快照空串、验证状态码等。
 *
 * <p>与 {@code DiagnosisJsonSnapshotParser}（读侧 JSON 数组元素）、
 * {@code EncounterDiagnosisSnapshotAssembler#buildSnapshot}（写侧物化）契约一致；修改须双端对齐。</p>
 */
public interface DoctorStationDiagnosisConstants {

    /**
     * 医生站分页检索入参：筛选「西医」类目录来源（与术语/目录编码约定一致）。
     */
    String DIAGNOSIS_KIND_FILTER_WESTERN = "1";

    /**
     * 医生站分页检索入参：筛选「中医」类目录来源。
     */
    String DIAGNOSIS_KIND_FILTER_TCM = "2";

    /**
     * 无诊断快照时的 JSON 字面量（空数组）。
     */
    String DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY = "[]";

    /**
     * 中西医维度标签：西医（持久化于快照 {@code diagnosisKind}）。
     */
    String DIAGNOSIS_KIND_TAG_WESTERN = "WESTERN";

    /**
     * 中西医维度标签：中医。
     */
    String DIAGNOSIS_KIND_TAG_TCM = "TCM";

    /**
     * 主诊标记：是（与就诊诊断关联 {@code primaryFlag} 及 UI 整型口径一致）。
     */
    int MAIN_DIAGNOSIS_FLAG_YES = 1;

/** DoctorStationDiagnosis 共享常量：diagnosis flag no，值 {@code 0}。 */
    int MAIN_DIAGNOSIS_FLAG_NO = 0;

    /**
     * 多条诊断合并为一行摘要时的展示分隔符（住院登记/护士站列表卡片）。
     */
    String DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR = "；";

    /**
     * 主诊断在列表/详情展示行中的短标签（前缀 {@code [主]}）。
     */
    String MAIN_DIAGNOSIS_DISPLAY_TAG = "主";

    /** 术语 {@code condition_definition_source}：疾病与诊断（西医通用）。 */
    String SOURCE_GENERAL_DX = "general_dx";

    /** 术语：特慢病诊断目录。 */
    String SOURCE_CHRONIC_SPEC = "chronic_spec";

    /** 术语：按病种目录。 */
    String SOURCE_DISEASE_PKG = "disease_pkg";

/** DoctorStationDiagnosis 共享常量：paid dx，值 {@code paid_dx}。 */
    String SOURCE_PAID_DX = "paid_dx";

    /** 术语：日间手术治疗病种。 */
    String SOURCE_DAY_SURGERY_DX = "day_surgery_dx";

    /** 术语：中医诊断（病名）。 */
    String SOURCE_TCM_DX = "tcm_dx";

    /** 术语：中医证候目录。 */
    String SOURCE_TCM_SYNDROME = "tcm_syndrome";

    /** 术语：肿瘤形态学目录。 */
    String SOURCE_TUMOR_MORPH = "tumor_morph";

    /**
     * 目录来源枚举串：西医侧分组，与 {@code cln_condition_definition.source_enum} 及 V58 术语码一致。
     */
    List<String> CONDITION_SOURCE_CODES_WESTERN = List.of(
        SOURCE_GENERAL_DX,
        SOURCE_CHRONIC_SPEC,
        SOURCE_DISEASE_PKG,
        SOURCE_PAID_DX,
        SOURCE_DAY_SURGERY_DX,
        SOURCE_TUMOR_MORPH);

    /**
     * 目录来源枚举串：中医侧分组。
     */
    List<String> CONDITION_SOURCE_CODES_TCM = List.of(SOURCE_TCM_DX, SOURCE_TCM_SYNDROME);

    /**
     * 由持久化 {@code source_enum} 推导中西医维度标签。
     *
     * @param sourceEnumRaw 目录来源码
     * @return {@link #DIAGNOSIS_KIND_TAG_WESTERN} / {@link #DIAGNOSIS_KIND_TAG_TCM} / {@code null}
     */
    static String resolveDiagnosisKindFromSource(String sourceEnumRaw) {
        if (sourceEnumRaw == null || sourceEnumRaw.isBlank()) {
            return null;
        }
        String trimmed = sourceEnumRaw.trim();
        if (CONDITION_SOURCE_CODES_WESTERN.contains(trimmed)) {
            return DIAGNOSIS_KIND_TAG_WESTERN;
        }
        if (CONDITION_SOURCE_CODES_TCM.contains(trimmed)) {
            return DIAGNOSIS_KIND_TAG_TCM;
        }
        return null;
    }

    /**
     * 界面展示：医保目录命中。
     */
    String YB_FLAG_TEXT_YES = "是";

    /**
     * 界面展示：非医保目录或未命中。
     */
    String YB_FLAG_TEXT_NO = "否";

    /**
     * 界面展示：医保标识列「是」的持久化整型（与历史接口约定一致）。
     */
    int YB_FLAG_INT_YES = 1;

    /**
     * 界面展示：医保标识列「否」的持久化整型。
     */
    int YB_FLAG_INT_NO = 0;

    /**
     * FHIR Condition.verificationStatus 持久化码（与域命令/库列一致）。
     */
    interface ConditionVerificationStatusCode {
/** DoctorStationDiagnosisConstants：UNCONFIRMED，值 {@code UNCONFIRMED}。 */
        String UNCONFIRMED = "UNCONFIRMED";
/** DoctorStationDiagnosisConstants：PROVISIONAL，值 {@code PROVISIONAL}。 */
        String PROVISIONAL = "PROVISIONAL";
/** DoctorStationDiagnosisConstants：DIFFERENTIAL，值 {@code DIFFERENTIAL}。 */
        String DIFFERENTIAL = "DIFFERENTIAL";
/** DoctorStationDiagnosisConstants：CONFIRMED，值 {@code CONFIRMED}。 */
        String CONFIRMED = "CONFIRMED";
/** DoctorStationDiagnosisConstants：REFUTED，值 {@code REFUTED}。 */
        String REFUTED = "REFUTED";
/** DoctorStationDiagnosisConstants：ENTERED_IN_ERROR，值 {@code ENTERED_IN_ERROR}。 */
        String ENTERED_IN_ERROR = "ENTERED_IN_ERROR";
    }

    /**
     * 医生站 UI 层验证状态整数码（与前端表格/快照 JSON 一致）。
     */
    interface VerificationUiCode {
/** DoctorStationDiagnosisConstants：UNCONFIRMED，值 {@code 1}。 */
        int UNCONFIRMED = 1;
/** DoctorStationDiagnosisConstants：PROVISIONAL，值 {@code 2}。 */
        int PROVISIONAL = 2;
/** DoctorStationDiagnosisConstants：DIFFERENTIAL，值 {@code 3}。 */
        int DIFFERENTIAL = 3;
/** DoctorStationDiagnosisConstants：CONFIRMED，值 {@code 4}。 */
        int CONFIRMED = 4;
/** DoctorStationDiagnosisConstants：REFUTED，值 {@code 5}。 */
        int REFUTED = 5;
/** DoctorStationDiagnosisConstants：ENTERED_IN_ERROR，值 {@code 6}。 */
        int ENTERED_IN_ERROR = 6;
    }
}
