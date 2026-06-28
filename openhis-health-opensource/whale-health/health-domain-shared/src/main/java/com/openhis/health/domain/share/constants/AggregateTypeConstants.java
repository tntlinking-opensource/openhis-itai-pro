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

public interface AggregateTypeConstants {

    /**
     * 聚合类型短码（用于 BUSNO 前缀）。
     */
    String PATIENT = "PA";

    /**
     * 就诊聚合类型名称（门诊等业务号前缀）。
     */
    String ENCOUNTER = "EN";

    /**
     * 住院就诊聚合类型名称（住院号 busNo 前缀，与 {@link #ENCOUNTER} 分序列）。
     */
    String INPATIENT_ENCOUNTER = "ZY";

    /**
     * 位置聚合类型名称。
     */
    String LOCATION = "LO";

    /**
     * 机构聚合类型名称。
     */
    String ORGANIZATION = "OR";

    /**
     * 医务人员聚合类型名称。
     */
    String PRACTITIONER = "PR";

    /**
     * 岗位角色聚合类型名称。
     */
    String PRACTITIONER_ROLE = "PRL";

    /**
     * 医疗服务聚合类型名称。
     */
    String HEALTHCARE_SERVICE = "HS";

    /**
     * 器材定义聚合类型名称。
     */
    String DEVICE_DEFINITION = "DD";

    /**
     * 就诊账户聚合类型名称。
     */
    String ACCOUNT = "AC";

    /**
     * 收费项定价聚合类型名称。
     */
    String CHARGE_ITEM_DEFINITION = "CID";

    /**
     * 收费明细聚合类型名称。
     */
    String CHARGE_ITEM = "CI";

    /**
     * 票据发票聚合类型名称。
     */
    String INVOICE = "IV";

    /**
     * 治疗周期聚合类型名称。
     */
    String EPISODE_OF_CARE = "EC";

    /**
     * 身体部位聚合类型名称。
     */
    String BODY_STRUCTURE = "BS";

    /**
     * 生命体征聚合类型名称。
     */
    String VITAL_SIGN = "VS";

    /**
     * 费用性质聚合类型名称。
     */
    String CONTRACT = "CT";

    /**
     * 药品定义聚合类型名称。
     */
    String MEDICATION_DEFINITION = "MD";

    /**
     * 药品发放聚合类型名称。
     */
    String MEDICATION_DISPENSE = "MDP";

    /**
     * 药品申请聚合类型名称（{@code med_medication_request.bus_no} 按日序号前缀）。
     */
    String MEDICATION_REQUEST = "MR";

    /**
     * 门诊处方分方单元号前缀（{@code med_medication_request.prescription_no}，与 {@link #MEDICATION_REQUEST} 分序列）。
     */
    String COMPOSITE_PRESCRIPTION = "CP";

    /**
     * 检验/检查/治疗申请单号前缀（{@code wkf_service_request.prescription_no}，与 {@link #SERVICE_REQUEST} 分序列）。
     */
    String REQUISITION_APPLICATION = "AR";

    /**
     * 耗材发放聚合类型名称。
     */
    String DEVICE_DISPENSE = "DDP";

    /** 诊疗项目定义聚合类型名称。 */
    String ACTIVITY_DEFINITION = "AD";
    /** 预约队列聚合类型名称。 */
    String APPOINTMENT = "AP";
    /** 耗材请求聚合类型名称。 */
    String DEVICE_REQUEST = "DR";
    /** 服务申请聚合类型名称。 */
    String SERVICE_REQUEST = "SR";
    /** 供应发放聚合类型名称。 */
    String SUPPLY_DELIVERY = "SD";
    /** 供应请求聚合类型名称。 */
    String SUPPLY_REQUEST = "SQ";
    /** 库存项聚合类型名称。 */
    String INVENTORY_ITEM = "II";

    /**
     * 库存明细备份快照批次单号前缀（DM-DOC-001 · {@code ABU}+{@code yyyyMMdd}+序号）。
     */
    String INVENTORY_DETAIL_SNAPSHOT_BACKUP = "ABU";
    /** 号源日程聚合类型名称。 */
    String SCHEDULE = "SC";
    /** 号源时段聚合类型名称。 */
    String SLOT = "SL";

    /**
     * 执行科室规则聚合类型名称。
     */
    String ACTIVITY_EXECUTION_LOCATION = "AEL";

    /**
     * 频次聚合类型名称。
     */
    String FREQUENCY = "FQ";

    /**
     * 供应商聚合类型名称。
     */
    String SUPPLIER = "SP";

    /**
     * 术语映射聚合类型名称。
     */
    String TERMINOLOGY_MAPPING = "TM";

    /**
     * 追溯码记录聚合类型名称。
     */
    String TRACE_NUMBER_RECORD = "TNR";

    /**
     * 画布打印模板聚合类型名称（busNo 前缀 PT）。
     */
    String PRINT_TEMPLATE = "PT";

    /**
     * 取药科室模板聚合类型名称。
     */
    String SERVICE_LOCATION_TEMPLATE = "SLT";

    /**
     * 诊断常用模板聚合类型名称。
     */
    String CONDITION_TEMPLATE = "CDT";

    /**
     * 收费明细自动生成规则聚合类型名称。
     */
    String CHARGE_ITEM_AUTO_GEN = "CAG";

    /**
     * 就诊历史里程碑聚合类型名称。
     */
    String ENCOUNTER_HISTORY = "EH";

    /**
     * 诊疗耗材绑定聚合类型名称。
     */
    String SERVICE_DEVICE_TEMPLATE = "SDT";

    /**
     * 价格调整聚合类型名称。
     */
    String PRICE_ADJUSTMENT = "PAD";

    /**
     * 组套模板聚合类型名称。
     */
    String ORDER_TEMPLATE = "OT";

    /**
     * 过敏与不耐受聚合类型名称。
     */
    String ALLERGY_INTOLERANCE = "AI";

    /**
     * 诊断定义聚合类型名称。
     */
    String CONDITION_DEFINITION = "CDF";

    /**
     * 诊断记录聚合类型名称。
     */
    String CONDITION = "CD";

    /**
     * 执行记录聚合类型名称。
     */
    String PROCEDURE = "PD";

    /**
     * 支付流水聚合类型名称。
     */
    String PAYMENT_RECONCILIATION = "PYR";

    /**
     * 收费员交账聚合类型名称（交账单 busNo 前缀）。
     */
    String CASHIER_SETTLEMENT = "CS";

    /**
     * 医保协同单聚合类型名称。
     */
    String YB_COORDINATION = "YBC";

    /**
     * 样本定义聚合类型名称。
     */
    String SPECIMEN_DEFINITION = "SPD";

    /**
     * 病案主档聚合类型（领域事件 {@code aggregateType}）。
     *
     * <p>值 {@code "case_record"} 为 Wave 1 定稿，与短码 {@code "CR"} 并存语义；事件载荷已发布，暂不改为短码以免破坏订阅方匹配。</p>
     */
    String CASE_RECORD = "case_record";
}
