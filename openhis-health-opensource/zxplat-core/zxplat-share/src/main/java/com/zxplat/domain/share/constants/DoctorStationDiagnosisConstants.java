/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

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

    /** 术语 {@code condition_definition_source}：院内诊断（医院自维护，非医保目录）。 */
    String SOURCE_HOSPITAL_DX = "hospital_dx";

    /** 术语 {@code condition_definition_source}：疾病与诊断目录（医保 1307）。 */
    String SOURCE_GENERAL_DX = "general_dx";

    /** 术语：门诊慢特病种目录（医保 1309）。 */
    String SOURCE_CHRONIC_SPEC = "chronic_spec";

    /** 术语：按病种付费病种目录（医保 1310）。 */
    String SOURCE_DISEASE_PKG = "disease_pkg";

    /** 术语：按病种付费病种目录历史别名（与 {@link #SOURCE_DISEASE_PKG} 同属 1310；侧栏已停用）。 */
    String SOURCE_PAID_DX = "paid_dx";

    /** 术语：日间手术治疗病种目录（医保 1311）。 */
    String SOURCE_DAY_SURGERY_DX = "day_surgery_dx";

    /** 术语：中医疾病目录（医保 1314）。 */
    String SOURCE_TCM_DX = "tcm_dx";

    /** 术语：中医证候目录（医保 1315）。 */
    String SOURCE_TCM_SYNDROME = "tcm_syndrome";

    /** 术语：肿瘤形态学目录（医保 1313）。 */
    String SOURCE_TUMOR_MORPH = "tumor_morph";

    /**
     * 术语：国家临床版 2.0（国临码）。
     *
     * <p>病案首页与<strong>住院</strong>医生站西医选码真源；门诊可通过系统设置
     * {@code doctorStationWesternDiagnosisSources} 显式纳入（见
     * {@link #OUTPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCE_ALLOWLIST}）。</p>
     */
    String SOURCE_CLINICAL_V2 = "clinical_v2";

    /**
     * 住院医生站西医诊断检索目录来源（仅国临）。
     *
     * <p>住院固定国临；上传/结算清单再经目录对照出 {@code yb_no}。</p>
     */
    List<String> INPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCES = List.of(SOURCE_CLINICAL_V2);

    /**
     * 术语：体检诊断（PEIS 诊断建议库；不进医生站西医/中医候选）。
     * <p>与 {@link #SOURCE_HOSPITAL_DX} 隔离，避免体检口语化模板污染医生站开诊断。</p>
     */
    String SOURCE_PEIS_DX = "peis_dx";

    /**
     * 目录来源枚举串：西医侧分组（医保/院内等，不含国临），与术语码一致。
     *
     * <p>用于中西医标签判定及「是否强制按医保目录展示」等逻辑；
     * 门诊检索可配置白名单见 {@link #OUTPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCE_ALLOWLIST}。
     * {@link #SOURCE_PEIS_DX} 不在此集合；{@link #SOURCE_CLINICAL_V2} 在
     * {@link #resolveDiagnosisKindFromSource} 中单独判为西医。</p>
     */
    List<String> CONDITION_SOURCE_CODES_WESTERN = List.of(
        SOURCE_HOSPITAL_DX,
        SOURCE_GENERAL_DX,
        SOURCE_CHRONIC_SPEC,
        SOURCE_DISEASE_PKG,
        SOURCE_PAID_DX,
        SOURCE_DAY_SURGERY_DX,
        SOURCE_TUMOR_MORPH);

    /**
     * 门诊系统设置 {@code doctorStationWesternDiagnosisSources} 可配置的西医检索来源白名单。
     *
     * <p>在 {@link #CONDITION_SOURCE_CODES_WESTERN} 基础上允许 {@link #SOURCE_CLINICAL_V2}，
     * 以便院点将门诊西医检索切到国临；非法码仍被解析器丢弃。</p>
     */
    List<String> OUTPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCE_ALLOWLIST = List.of(
        SOURCE_HOSPITAL_DX,
        SOURCE_GENERAL_DX,
        SOURCE_CHRONIC_SPEC,
        SOURCE_DISEASE_PKG,
        SOURCE_PAID_DX,
        SOURCE_DAY_SURGERY_DX,
        SOURCE_TUMOR_MORPH,
        SOURCE_CLINICAL_V2);

    /**
     * 门诊医生站西医诊断检索默认目录来源（未配置或配置非法时回落）。
     *
     * <p>院内诊断 + 疾病与诊断目录(1307)；国临等需在系统设置中显式加入。</p>
     */
    List<String> DEFAULT_WESTERN_DIAGNOSIS_SEARCH_SOURCES = List.of(
        SOURCE_HOSPITAL_DX,
        SOURCE_GENERAL_DX);

    /**
     * 目录来源枚举串：中医侧分组。
     */
    List<String> CONDITION_SOURCE_CODES_TCM = List.of(SOURCE_TCM_DX, SOURCE_TCM_SYNDROME);

    /**
     * 由持久化 {@code source_enum} 推导中西医维度标签。
     *
     * <p>门诊西医白名单（{@link #CONDITION_SOURCE_CODES_WESTERN}）与住院国临
     * （{@link #SOURCE_CLINICAL_V2}）均标 {@link #DIAGNOSIS_KIND_TAG_WESTERN}；
     * 中医码标 {@link #DIAGNOSIS_KIND_TAG_TCM}；其余返回 {@code null}。</p>
     *
     * @param sourceEnumRaw 目录来源码
     * @return {@link #DIAGNOSIS_KIND_TAG_WESTERN} / {@link #DIAGNOSIS_KIND_TAG_TCM} / {@code null}
     */
    static String resolveDiagnosisKindFromSource(String sourceEnumRaw) {
        // Gate：空串无法归类
        if (sourceEnumRaw == null || sourceEnumRaw.isBlank()) {
            return null;
        }
        String trimmed = sourceEnumRaw.trim();
        // 业务分支：门诊西医白名单或住院国临 → WESTERN
        if (CONDITION_SOURCE_CODES_WESTERN.contains(trimmed)
            || SOURCE_CLINICAL_V2.equals(trimmed)) {
            return DIAGNOSIS_KIND_TAG_WESTERN;
        }
        // 业务分支：中医疾病/证候 → TCM
        if (CONDITION_SOURCE_CODES_TCM.contains(trimmed)) {
            return DIAGNOSIS_KIND_TAG_TCM;
        }
        return null;
    }

    /**
     * 西医医保目录反查国临时使用的医保主码；国临/中医/无法解析时返回 {@code null}。
     *
     * @param sourceEnumRaw 目录来源
     * @param ybNoRaw       目录 {@code yb_no}
     * @param conditionCodeRaw 目录主码 {@code condition_code}
     * @return 医保主码或 null
     */
    static String resolveWesternInsuranceYbKey(
            String sourceEnumRaw, String ybNoRaw, String conditionCodeRaw) {
        String source = null;
        if (sourceEnumRaw != null && !sourceEnumRaw.isBlank()) {
            source = sourceEnumRaw.trim();
        }
        // Gate：国临自身不反查
        if (SOURCE_CLINICAL_V2.equals(source)) {
            return null;
        }
        // Gate：中医目录不反查
        if (source != null && CONDITION_SOURCE_CODES_TCM.contains(source)) {
            return null;
        }
        if (ybNoRaw != null && !ybNoRaw.isBlank()) {
            return ybNoRaw.trim();
        }
        if (conditionCodeRaw != null && !conditionCodeRaw.isBlank()) {
            return conditionCodeRaw.trim();
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

    /** IF-DOR-06B：备案码命中列 — 医保编码。 */
    String OPSP_MATCH_FIELD_YB_NO = "yb_no";

    /** IF-DOR-06B：备案码命中列 — 诊断目录编码。 */
    String OPSP_MATCH_FIELD_CONDITION_CODE = "condition_code";

    /**
     * IF-DOR-06B 行映射状态（与 AS-OP-DOR-002 / AS-SDD 一致）。
     */
    interface OpspFilingMatchStatus {
        /** 已映射到唯一启用目录，可勾选导入。 */
        String MATCHED = "matched";
        /** 未找到院内目录。 */
        String UNMATCHED = "unmatched";
        /** 备案已过期。 */
        String EXPIRED = "expired";
        /** 当前就诊已开立该定义。 */
        String ALREADY_ON_ENCOUNTER = "alreadyOnEncounter";
        /** 同优先级多条目录，禁止静默写入。 */
        String AMBIGUOUS = "ambiguous";
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
