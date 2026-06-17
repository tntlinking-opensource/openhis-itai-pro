/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

public interface TerminologyCodeSystemNameConstants {

    /** 药品分类编码体系。 */
    String MEDICATION_CATEGORY_CODE = "medication_category_code";
    /** 诊疗项目目录类别（与 {@code activity_category_code} 种子一致）。 */
    String ACTIVITY_CATEGORY_CODE = "activity_category_code";
    /** 发布状态（启用/停用等）。 */
    String PUBLICATION_STATUS = "publication_status";
    /** 药品领域维度。 */
    String MEDICATION_DOMAIN = "medication_domain";
    /** 是否类标记（与前端 yes/no 维度对齐）。 */
    String YES_NO_FLAG = "yes_no_flag";
    /** 限制使用枚举。 */
    String RESTRICTED_ENUM = "restricted_enum";
    /** 部位属性枚举。 */
    String PART_ATTRIBUTE_ENUM = "part_attribute_enum";
    /** 临时医嘱拆分属性枚举。 */
    String THO_PART_ATTRIBUTE_ENUM = "tho_part_attribute_enum";
    /** 医保等级（收费项等级）。 */
    String CHRGITM_LV = "chrgitm_lv";
    /** 药学专业分类编码。 */
    String MED_CATEGORY_CODE = "med_category_code";
    /** 体系类别（药理/管理等）。 */
    String SYSTEM_CATEGORIES = "system_categories";
    /** 药品性质（术语字典）。 */
    String MEDICINE_PROPERTIES = "medicine_properties";
    /** 剂型编码体系。 */
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
    /** 剂量来源口径（录入维度）。 */
    String DOSE_FROM_CODE = "dose_from_code";
    /** 处方标志术语。 */
    String RX_FLAG = "rx_flag";
    /** 用药说明条目术语。 */
    String DOSAGE_INSTRUCTION = "dosage_instruction";
    /** 计量单位术语（包装/剂量等单位下拉）。 */
    String UNIT_CODE = "unit_code";
    /**
     * 疾病目录来源分类（编码体系名 {@code condition_definition_source}）。
     * <p>种子见 {@code health-infrastructure/src/main/resources/db/migration/health/V1__Init.sql}
     * （{@code ter_code_system.id=485} {@code name=condition_definition_source} 及对应 {@code ter_concept}），
     * 概念项（code → display）：{@code general_dx}→疾病与诊断；{@code chronic_spec}→特慢病诊断目录；
     * {@code disease_pkg}→按病种目录；{@code paid_dx}→付费诊断；{@code day_surgery_dx}→日间手术治疗病种；
     * {@code tcm_dx}→中医诊断；{@code tcm_syndrome}→中医证候目录；{@code tumor_morph}→肿瘤形态学目录。</p>
     */
    String CONDITION_DEFINITION_SOURCE = "condition_definition_source";
    /** 诊断类型编码。 */
    String DIAG_TYPE = "diag_type";
    /** 住院患者疾病诊断类型代码（与 {@code adm_encounter_diagnosis.diag_type_code} 展示口径一致）。 */
    String IPT_DISE_TYPE_CODE = "ipt_dise_type_code";

    /** 采购计量单位编码体系（耗材包装）。 */
    String PURCHASE_UNIT = "purchase_unit";
    /** 销售单位术语。 */
    String SALES_UNIT = "sales_unit";
    /** 拆零/最小单位术语。 */
    String SPLIT_UNIT = "split_unit";
    /** 库存单位术语。 */
    String STOCK_UNIT = "stock_unit";
    /** 剂量单位术语（耗材侧可与药品共用码表）。 */
    String DOSE_UNIT = "dose_unit";
    /** 器材/耗材种类术语（与 {@code adm_device_definition.type_code} 对齐）。 */
    String DEVICE_TYPE_CODE = "device_type_code";
    /** 器材分类术语（与 {@code adm_device_definition.category_code} 对齐）。 */
    String DEVICE_CATEGORY_CODE = "device_category_code";
    /** 入院方式（与 {@code adm_encounter.in_way_code} 对齐）。 */
    String IN_WAY_CODE = "in_way_code";
    /** 入院类型（与 {@code adm_encounter.admit_source_code} 对齐）。 */
    String ADMIT_SOURCE_CODE = "admit_source_code";
}
