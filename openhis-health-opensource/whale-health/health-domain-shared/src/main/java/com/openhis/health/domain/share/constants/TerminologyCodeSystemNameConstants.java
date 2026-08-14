/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;


/**
 * TerminologyCodeSystemNameConstants：health-domain-shared 跨领域共享常量。
 */
public interface TerminologyCodeSystemNameConstants {

    /** 药品分类编码体系。 */
    String MEDICATION_CATEGORY_CODE = "medication_category_code";
    /** 诊疗项目目录类别（与 {@code activity_category_code} 种子一致）。 */
    String ACTIVITY_CATEGORY_CODE = "activity_category_code";
    /** 发布状态（启用/停用等）。 */
    String PUBLICATION_STATUS = "publication_status";
/** TerminologyCodeSystemName 共享常量：domain，值 {@code medication_domain}。 */
    String MEDICATION_DOMAIN = "medication_domain";
    /** 是否类标记（与前端 yes/no 维度对齐）。 */
    String YES_NO_FLAG = "yes_no_flag";
/** TerminologyCodeSystemName 共享常量：enum，值 {@code restricted_enum}。 */
    String RESTRICTED_ENUM = "restricted_enum";
/** TerminologyCodeSystemName 共享常量：attribute enum，值 {@code part_attribute_enum}。 */
    String PART_ATTRIBUTE_ENUM = "part_attribute_enum";
    /** 临时医嘱拆分属性枚举。 */
    String THO_PART_ATTRIBUTE_ENUM = "tho_part_attribute_enum";
    /** 医保等级（收费项等级）。 */
    String CHRGITM_LV = "chrgitm_lv";
    /** 病案结算类型（三大目录共用，术语 {@code mr_settlement_type_code}）。 */
    String MR_SETTLEMENT_TYPE_CODE = "mr_settlement_type_code";
    /** 体系类别（药理/管理等）。 */
    String SYSTEM_CATEGORIES = "system_categories";
    /** 药品性质（术语字典）。 */
    String MEDICINE_PROPERTIES = "medicine_properties";
/** TerminologyCodeSystemName 共享常量：form code，值 {@code dose_form_code}。 */
    String DOSE_FORM_CODE = "dose_form_code";
    /** 医保类别（药品侧 chrgitm 口径）。 */
    String MED_CHRGITM_TYPE = "med_chrgitm_type";
    /** 给药途径/用法术语。 */
    String METHOD_CODE = "method_code";
    /** 中药处方用法术语。 */
    String TCM_METHOD_CODE = "tcm_method_code";
    /** 财务类型编码（药品与耗材共用）。 */
    String FIN_TYPE_CODE = "fin_type_code";
    /** 抗生素分级类型等。 */
    String ANTIBIOTIC_TYPE_CODE = "antibiotic_type_code";
    /** DDD 分类术语。 */
    String DDD_CODE = "ddd_code";
    /** DDD（限定日剂量）计量单位术语（与计量 {@code unit_code}、剂量 {@code dose_unit} 分离）。 */
    String DDD_UNIT = "ddd_unit";
    /** 剂量来源口径（录入维度）。 */
    String DOSE_FROM_CODE = "dose_from_code";
/** TerminologyCodeSystemName 共享常量：flag，值 {@code rx_flag}。 */
    String RX_FLAG = "rx_flag";
    /** 用药说明条目术语。 */
    String DOSAGE_INSTRUCTION = "dosage_instruction";
    /** 计量单位术语（包装/剂量等单位下拉）。 */
    String UNIT_CODE = "unit_code";
    /**
     * 疾病目录来源分类（编码体系名 {@code condition_definition_source}）。
     * <p>概念项（code → display，对齐医保 13xx 文档名）：
     * {@code hospital_dx}→院内诊断；
     * {@code general_dx}→疾病与诊断目录（1307）；{@code chronic_spec}→门诊慢特病种目录（1309）；
     * {@code disease_pkg}→按病种付费病种目录（1310）；{@code paid_dx}→同 1310 历史别名（侧栏停用）；
     * {@code day_surgery_dx}→日间手术治疗病种目录（1311）；{@code tumor_morph}→肿瘤形态学目录（1313）；
     * {@code tcm_dx}→中医疾病目录（1314）；{@code tcm_syndrome}→中医证候目录（1315）；
     * {@code clinical_v2}→国家临床版2.0；{@code peis_dx}→体检诊断（不进医生站候选）。</p>
     */
    String CONDITION_DEFINITION_SOURCE = "condition_definition_source";

    /**
     * 手术目录来源分类（{@code procedure_definition_source}）：
     * {@code clinical_proc_v3}→国家临床版手术3.0；{@code general_op}→手术操作目录（1308）。
     */
    String PROCEDURE_DEFINITION_SOURCE = "procedure_definition_source";
/** TerminologyCodeSystemName 共享常量：type，值 {@code diag_type}。 */
    String DIAG_TYPE = "diag_type";
    /** 住院患者疾病诊断类型代码（与 {@code adm_encounter_diagnosis.diag_type_code} 展示口径一致）。 */
    String IPT_DISE_TYPE_CODE = "ipt_dise_type_code";

    /** 剂量单位术语（耗材侧可与药品共用码表）。 */
    String DOSE_UNIT = "dose_unit";
    /** 器材/耗材种类术语（与 {@code adm_device_definition.type_code} 对齐）。 */
    String DEVICE_TYPE_CODE = "device_type_code";
    /** 器材分类术语（与 {@code adm_device_definition.category_code} 对齐）。 */
    String DEVICE_CATEGORY_CODE = "device_category_code";
    /** 入院途径（与 {@code adm_encounter.in_way_code}、医保 adm_way 1/2/3/9 对齐）。 */
    String IN_WAY_CODE = "in_way_code";
    /** 入院类型（与 {@code adm_encounter.admit_source_code} 对齐）。 */
    String ADMIT_SOURCE_CODE = "admit_source_code";
    /** 入院时情况/患者病情（与 {@code adm_encounter.priority_enum} 展示口径一致，术语 adm_cond_code）。 */
    String ADM_COND_CODE = "adm_cond_code";
    /** 生命体征项编码体系（护士站/体温单体征下拉）。 */
    String VITAL_SIGNS_CODE = "vital_signs_code";
    /** 护理记录意识状态编码体系。 */
    String CONSCIOUSNESS_CODE = "consciousness_code";
    /** 收银支付渠道（477 pay_enum，ADR-PAY-001）。 */
    String PAY_ENUM = "pay_enum";
    /** 体温单特殊事件编码体系。 */
    String SPECIAL_EVENT_CODE = "special_event_code";
    /** 库存范围（数量等于 0 / 数量大于 0；种子 {@code inventory_range}）。 */
    String INVENTORY_RANGE = "inventory_range";

    /**
     * 处方类别代码（国家标准术语 355，{@code med_medication_request.rx_type_code}）。
     */
    String RX_TYPE_CODE = "rx_type_code";
}
