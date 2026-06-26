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

public interface TableNameConstants {
    /** 药品定义表名。 */
    String MED_MEDICATION_DEFINITION = "med_medication_definition";
    /** 药品申请表名。 */
    String MED_MEDICATION_REQUEST = "med_medication_request";

    /** 耗材定义表名。 */
    String ADM_DEVICE_DEFINITION = "adm_device_definition";

    /** 诊疗活动定义表名。 */
    String WKF_ACTIVITY_DEFINITION = "wkf_activity_definition";
    /** 术语概念表名。 */
    String TER_CONCEPT = "ter_concept";
    /** 医疗服务定义表名。 */
    String ADM_HEALTHCARE_SERVICE = "adm_healthcare_service";
    /** 服务申请表名。 */
    String WKF_SERVICE_REQUEST = "wkf_service_request";
    /** 耗材请求表名。 */
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
}
