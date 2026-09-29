/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;


/**
 * TableNameConstants：health-domain-shared 跨领域共享常量。
 */
public interface TableNameConstants {
/** TableName 共享常量：medication definition，值 {@code med_medication_definition}。 */
    String MED_MEDICATION_DEFINITION = "med_medication_definition";
/** TableName 共享常量：medication request，值 {@code med_medication_request}。 */
    String MED_MEDICATION_REQUEST = "med_medication_request";

/** TableName 共享常量：device definition，值 {@code adm_device_definition}。 */
    String ADM_DEVICE_DEFINITION = "adm_device_definition";

    /** 诊疗活动定义表名。 */
    String WKF_ACTIVITY_DEFINITION = "wkf_activity_definition";
/** TableName 共享常量：concept，值 {@code ter_concept}。 */
    String TER_CONCEPT = "ter_concept";
    /** 医疗服务定义表名。 */
    String ADM_HEALTHCARE_SERVICE = "adm_healthcare_service";
/** TableName 共享常量：service request，值 {@code wkf_service_request}。 */
    String WKF_SERVICE_REQUEST = "wkf_service_request";
/** TableName 共享常量：device request，值 {@code wkf_device_request}。 */
    String WKF_DEVICE_REQUEST = "wkf_device_request";
    /** 药品发放表名（收费明细 service_table 锚点，DM-ADM-011 BR-CHG-001）。 */
    String MED_MEDICATION_DISPENSE = "med_medication_dispense";
    /** 耗材发放表名（收费明细 service_table 锚点）。 */
    String WKF_DEVICE_DISPENSE = "wkf_device_dispense";
    /** 就诊表名（收费明细 service_table 锚点，DM-ADM-011 / BR-CHG-001）。 */
    String ADM_ENCOUNTER = "adm_encounter";
    /** 身体结构表名（服务申请明细 target_table 锚点）。 */
    String DIA_BODY_STRUCTURE = "dia_body_structure";
    /** 生命体征单据主表名。 */
    String DIA_VITAL_SIGN = "dia_vital_sign";
    /** 生命体征明细表名。 */
    String DIA_VITAL_SIGN_DETAIL = "dia_vital_sign_detail";
    /** 标本定义表名（服务申请明细 target_table 锚点）。 */
    String ADM_SPECIMEN_DEFINITION = "adm_specimen_definition";
    /** 检验/科室设备台账表名。 */
    String ADM_DEVICE = "adm_device";
    /** 标本合管配置表名。 */
    String CUS_SPECIMEN_TUBE_MERGE = "cus_specimen_tube_merge";
    /** 收费折扣配置表名（DM-CUS-016）。 */
    String CUS_CHARGE_DISCOUNT_CONFIG = "cus_charge_discount_config";
    /** 收费明细自动生成规则表名（滚费床位长期医嘱 based_on 锚点，AS-IP-NUR-013 BR-IP-ARG-019）。 */
    String CUS_CHARGE_ITEM_AUTO_GEN = "cus_charge_item_auto_gen";
    /** 体检就诊扩展表名（历史团检套餐锚点，方案 A 前）。 */
    String PEIS_ENCOUNTER_EXT = "peis_encounter_ext";
    /** 团检合同表名（团检套餐费 service 锚点，AS-PE-FIN-001 方案 A）。 */
    String PEIS_GROUP_CONTRACT = "peis_group_contract";
}
