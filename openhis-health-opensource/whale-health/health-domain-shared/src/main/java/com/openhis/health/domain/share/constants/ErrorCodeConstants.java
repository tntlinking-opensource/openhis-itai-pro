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

/**
 * 错误码常量。
 *
 * <p>统一承载跨领域可复用的业务错误码字符串常量，供 {@code BusinessException} 等异常抛出使用。
 * 该类仅包含常量，不允许引入业务逻辑。</p>
 */
public interface ErrorCodeConstants {

    /**
     * 患者错误码：参数非法。
     */
    String PATIENT_INVALID_ARGUMENT = "PATIENT_001";
    /**
     * 患者错误码：资源不存在。
     */
    String PATIENT_NOT_FOUND = "PATIENT_002";
    /**
     * 患者错误码：状态不允许。
     */
    String PATIENT_INVALID_STATUS = "PATIENT_003";
    /**
     * 患者错误码：身份证冲突。
     */
    String PATIENT_ID_CARD_CONFLICT = "PATIENT_004";
    /**
     * 患者错误码：标识冲突。
     */
    String PATIENT_IDENTIFIER_CONFLICT = "PATIENT_005";

    /**
     * 就诊错误码：参数非法。
     */
    String ENCOUNTER_INVALID_ARGUMENT = "ENCOUNTER_001";
    /**
     * 就诊错误码：资源不存在。
     */
    String ENCOUNTER_NOT_FOUND = "ENCOUNTER_002";
    /**
     * 就诊错误码：状态不允许。
     */
    String ENCOUNTER_INVALID_STATUS = "ENCOUNTER_003";
    /**
     * 就诊错误码：业务号冲突。
     */
    String ENCOUNTER_BUS_NO_CONFLICT = "ENCOUNTER_004";
    /**
     * 就诊错误码：规则冲突。
     */
    String ENCOUNTER_RULE_CONFLICT = "ENCOUNTER_005";

    /**
     * 位置错误码：参数非法。
     */
    String LOCATION_INVALID_ARGUMENT = "LOCATION_001";
    /**
     * 位置错误码：资源不存在。
     */
    String LOCATION_NOT_FOUND = "LOCATION_002";
    /**
     * 位置错误码：状态不允许。
     */
    String LOCATION_INVALID_STATUS = "LOCATION_003";
    /**
     * 位置错误码：同级名称冲突。
     */
    String LOCATION_NAME_CONFLICT = "LOCATION_004";
    /**
     * 位置错误码：规则冲突。
     */
    String LOCATION_RULE_CONFLICT = "LOCATION_005";

    /**
     * 机构错误码：参数非法。
     */
    String ORGANIZATION_INVALID_ARGUMENT = "ORGANIZATION_001";
    /**
     * 机构错误码：资源不存在。
     */
    String ORGANIZATION_NOT_FOUND = "ORGANIZATION_002";
    /**
     * 机构错误码：状态不允许。
     */
    String ORGANIZATION_INVALID_STATUS = "ORGANIZATION_003";
    /**
     * 机构错误码：重名冲突。
     */
    String ORGANIZATION_NAME_CONFLICT = "ORGANIZATION_004";
    /**
     * 机构错误码：规则冲突。
     */
    String ORGANIZATION_RULE_CONFLICT = "ORGANIZATION_005";

    /**
     * 医务人员错误码：参数非法。
     */
    String PRACTITIONER_INVALID_ARGUMENT = "PRACTITIONER_001";
    /**
     * 医务人员错误码：资源不存在。
     */
    String PRACTITIONER_NOT_FOUND = "PRACTITIONER_002";
    /**
     * 医务人员错误码：状态不允许。
     */
    String PRACTITIONER_INVALID_STATUS = "PRACTITIONER_003";
    /**
     * 医务人员错误码：工号冲突。
     */
    String PRACTITIONER_BUS_NO_CONFLICT = "PRACTITIONER_004";
    /**
     * 医务人员错误码：规则冲突。
     */
    String PRACTITIONER_RULE_CONFLICT = "PRACTITIONER_005";

    /**
     * 岗位角色错误码：参数非法。
     */
    String PRACTITIONER_ROLE_INVALID_ARGUMENT = "PRACTITIONER_ROLE_001";
    /**
     * 岗位角色错误码：资源不存在。
     */
    String PRACTITIONER_ROLE_NOT_FOUND = "PRACTITIONER_ROLE_002";
    /**
     * 岗位角色错误码：状态不允许。
     */
    String PRACTITIONER_ROLE_INVALID_STATUS = "PRACTITIONER_ROLE_003";
    /**
     * 岗位角色错误码：唯一性冲突。
     */
    String PRACTITIONER_ROLE_CONFLICT = "PRACTITIONER_ROLE_004";
    /**
     * 岗位角色错误码：规则冲突。
     */
    String PRACTITIONER_ROLE_RULE_CONFLICT = "PRACTITIONER_ROLE_005";

    /**
     * 数据管辖错误码：目标超出岗位管辖范围。
     */
    String MANAGED_SCOPE_OUT_OF_SCOPE = "MANAGED_SCOPE_001";
    /**
     * 数据管辖错误码：未配置对应岗位管辖范围。
     */
    String MANAGED_SCOPE_NOT_CONFIGURED = "MANAGED_SCOPE_002";

    /**
     * 医疗服务错误码：参数非法。
     */
    String HEALTHCARE_SERVICE_INVALID_ARGUMENT = "HEALTHCARE_SERVICE_001";
    /**
     * 医疗服务错误码：资源不存在。
     */
    String HEALTHCARE_SERVICE_NOT_FOUND = "HEALTHCARE_SERVICE_002";
    /**
     * 医疗服务错误码：状态不允许。
     */
    String HEALTHCARE_SERVICE_INVALID_STATUS = "HEALTHCARE_SERVICE_003";
    /**
     * 医疗服务错误码：规则冲突。
     */
    String HEALTHCARE_SERVICE_RULE_CONFLICT = "HEALTHCARE_SERVICE_004";

    /**
     * 器材定义错误码：参数非法。
     */
    String DEVICE_DEFINITION_INVALID_ARGUMENT = "DEVICE_DEFINITION_001";
    /**
     * 器材定义错误码：资源不存在。
     */
    String DEVICE_DEFINITION_NOT_FOUND = "DEVICE_DEFINITION_002";
    /**
     * 器材定义错误码：状态不允许。
     */
    String DEVICE_DEFINITION_INVALID_STATUS = "DEVICE_DEFINITION_003";
    /**
     * 器材定义错误码：规则冲突。
     */
    String DEVICE_DEFINITION_RULE_CONFLICT = "DEVICE_DEFINITION_004";

    /**
     * 账户错误码：参数非法。
     */
    String ACCOUNT_INVALID_ARGUMENT = "ACCOUNT_001";
    /**
     * 账户错误码：资源不存在。
     */
    String ACCOUNT_NOT_FOUND = "ACCOUNT_002";
    /**
     * 账户错误码：状态不允许。
     */
    String ACCOUNT_INVALID_STATUS = "ACCOUNT_003";
    /**
     * 账户错误码：规则冲突。
     */
    String ACCOUNT_RULE_CONFLICT = "ACCOUNT_004";

    /**
     * 收费项定价错误码：参数非法。
     */
    String CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT = "CHARGE_ITEM_DEFINITION_001";
    /**
     * 收费项定价错误码：资源不存在。
     */
    String CHARGE_ITEM_DEFINITION_NOT_FOUND = "CHARGE_ITEM_DEFINITION_002";
    /**
     * 收费项定价错误码：状态不允许。
     */
    String CHARGE_ITEM_DEFINITION_INVALID_STATUS = "CHARGE_ITEM_DEFINITION_003";
    /**
     * 收费项定价错误码：规则冲突。
     */
    String CHARGE_ITEM_DEFINITION_RULE_CONFLICT = "CHARGE_ITEM_DEFINITION_004";

    /**
     * 收费明细错误码：参数非法。
     */
    String CHARGE_ITEM_INVALID_ARGUMENT = "CHARGE_ITEM_001";
    /**
     * 收费明细错误码：资源不存在。
     */
    String CHARGE_ITEM_NOT_FOUND = "CHARGE_ITEM_002";
    /**
     * 收费明细错误码：状态不允许。
     */
    String CHARGE_ITEM_INVALID_STATUS = "CHARGE_ITEM_003";
    /**
     * 收费明细错误码：规则冲突。
     */
    String CHARGE_ITEM_RULE_CONFLICT = "CHARGE_ITEM_004";

    /**
     * 发票错误码：参数非法。
     */
    String INVOICE_INVALID_ARGUMENT = "INVOICE_001";
    /**
     * 发票错误码：资源不存在。
     */
    String INVOICE_NOT_FOUND = "INVOICE_002";
    /**
     * 发票错误码：状态不允许。
     */
    String INVOICE_INVALID_STATUS = "INVOICE_003";
    /**
     * 发票错误码：规则冲突。
     */
    String INVOICE_RULE_CONFLICT = "INVOICE_004";

    /**
     * 治疗周期错误码：参数非法。
     */
    String EPISODE_OF_CARE_INVALID_ARGUMENT = "EPISODE_OF_CARE_001";
    /**
     * 治疗周期错误码：资源不存在。
     */
    String EPISODE_OF_CARE_NOT_FOUND = "EPISODE_OF_CARE_002";
    /**
     * 治疗周期错误码：状态不允许。
     */
    String EPISODE_OF_CARE_INVALID_STATUS = "EPISODE_OF_CARE_003";
    /**
     * 治疗周期错误码：规则冲突。
     */
    String EPISODE_OF_CARE_RULE_CONFLICT = "EPISODE_OF_CARE_004";

    /**
     * 药品定义错误码：参数非法。
     */
    String MEDICATION_DEFINITION_INVALID_ARGUMENT = "MEDICATION_DEFINITION_001";
    /**
     * 药品定义错误码：资源不存在。
     */
    String MEDICATION_DEFINITION_NOT_FOUND = "MEDICATION_DEFINITION_002";
    /**
     * 药品定义错误码：状态不允许。
     */
    String MEDICATION_DEFINITION_INVALID_STATUS = "MEDICATION_DEFINITION_003";
    /**
     * 药品定义错误码：规则冲突。
     */
    String MEDICATION_DEFINITION_RULE_CONFLICT = "MEDICATION_DEFINITION_004";

    /**
     * 身体部位错误码：参数非法。
     */
    String BODY_STRUCTURE_INVALID_ARGUMENT = "BODY_STRUCTURE_001";
    /**
     * 身体部位错误码：资源不存在。
     */
    String BODY_STRUCTURE_NOT_FOUND = "BODY_STRUCTURE_002";
    /**
     * 身体部位错误码：状态不允许。
     */
    String BODY_STRUCTURE_INVALID_STATUS = "BODY_STRUCTURE_003";
    /**
     * 身体部位错误码：业务编码冲突。
     */
    String BODY_STRUCTURE_BUS_NO_CONFLICT = "BODY_STRUCTURE_004";
    /**
     * 身体部位错误码：规则冲突。
     */
    String BODY_STRUCTURE_RULE_CONFLICT = "BODY_STRUCTURE_005";

    /**
     * 生命体征错误码：参数非法。
     */
    String VITAL_SIGN_INVALID_ARGUMENT = "VITAL_SIGN_001";
    /**
     * 生命体征错误码：资源不存在。
     */
    String VITAL_SIGN_NOT_FOUND = "VITAL_SIGN_002";
    /**
     * 生命体征错误码：体征编码未启用或不存在。
     */
    String VITAL_SIGN_INVALID_CODE = "VITAL_SIGN_003";

    /**
     * 费用性质错误码：参数非法。
     */
    String CONTRACT_INVALID_ARGUMENT = "CONTRACT_001";
    /**
     * 费用性质错误码：资源不存在。
     */
    String CONTRACT_NOT_FOUND = "CONTRACT_002";
    /**
     * 费用性质错误码：状态不允许。
     */
    String CONTRACT_INVALID_STATUS = "CONTRACT_003";
    /**
     * 费用性质错误码：业务编码冲突。
     */
    String CONTRACT_BUS_NO_CONFLICT = "CONTRACT_004";
    /**
     * 费用性质错误码：规则冲突。
     */
    String CONTRACT_RULE_CONFLICT = "CONTRACT_005";

    /**
     * 支付流水错误码：参数非法。
     */
    String PAYMENT_RECONCILIATION_INVALID_ARGUMENT = "PAYMENT_RECONCILIATION_001";
    /**
     * 支付流水错误码：资源不存在。
     */
    String PAYMENT_RECONCILIATION_NOT_FOUND = "PAYMENT_RECONCILIATION_002";
    /**
     * 支付流水错误码：状态不允许。
     */
    String PAYMENT_RECONCILIATION_INVALID_STATUS = "PAYMENT_RECONCILIATION_003";
    /**
     * 支付流水错误码：规则冲突。
     */
    String PAYMENT_RECONCILIATION_RULE_CONFLICT = "PAYMENT_RECONCILIATION_004";
    /**
     * 支付流水明细错误码：明细不存在。
     */
    String PAYMENT_REC_DETAIL_NOT_FOUND = "PAYMENT_RECONCILIATION_005";
    /**
     * 支付流水明细错误码：明细不属于该结算单。
     */
    String PAYMENT_REC_DETAIL_NOT_MATCH = "PAYMENT_RECONCILIATION_006";

    /**
     * 收费员交账错误码：参数非法。
     */
    String CASHIER_SETTLEMENT_INVALID_ARGUMENT = "CASHIER_SETTLEMENT_001";
    /**
     * 收费员交账错误码：无可交账流水。
     */
    String CASHIER_SETTLEMENT_EMPTY_CANDIDATES = "CASHIER_SETTLEMENT_002";
    /**
     * 收费员交账错误码：并发挂载冲突。
     */
    String CASHIER_SETTLEMENT_CONCURRENT_CONFLICT = "CASHIER_SETTLEMENT_003";
    /**
     * 收费员交账错误码：单号生成冲突。
     */
    String CASHIER_SETTLEMENT_BUS_NO_CONFLICT = "CASHIER_SETTLEMENT_004";

    /**
     * 药品发放错误码：参数非法。
     */
    String MEDICATION_DISPENSE_INVALID_ARGUMENT = "MEDICATION_DISPENSE_001";
    /**
     * 药品发放错误码：资源不存在。
     */
    String MEDICATION_DISPENSE_NOT_FOUND = "MEDICATION_DISPENSE_002";
    /**
     * 药品发放错误码：状态不允许。
     */
    String MEDICATION_DISPENSE_INVALID_STATUS = "MEDICATION_DISPENSE_003";
    /**
     * 药品发放错误码：规则冲突。
     */
    String MEDICATION_DISPENSE_RULE_CONFLICT = "MEDICATION_DISPENSE_004";

    /**
     * 门诊发药（AS-OP-PHA-001）：初始化元数据不可用（EX-OPP-101）。
     */
    String EX_OPP_101_INIT_METADATA_FAILED = "EX-OPP-101";
    /**
     * 门诊发药：队列查询参数非法或查询失败（EX-OPP-102）。
     */
    String EX_OPP_102_QUEUE_QUERY_FAILED = "EX-OPP-102";
    /**
     * 门诊发药：就诊维度明细加载失败（EX-OPP-103）。
     */
    String EX_OPP_103_DISPENSE_SLIP_LOAD_FAILED = "EX-OPP-103";
    /**
     * 门诊发药：追溯码已出库（EX-OPP-201）。
     */
    String EX_OPP_201_TRACE_CODE_ALREADY_OUTBOUND = "EX-OPP-201";
    /**
     * 门诊发药：调配药师校验失败（EX-OPP-202）。
     */
    String EX_OPP_202_PREPARER_INVALID = "EX-OPP-202";
    /**
     * 门诊发药：发放状态不允许当前动作（EX-OPP-203）。
     */
    String EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED = "EX-OPP-203";
    /**
     * 门诊发药：数量与医嘱开立口径不一致（EX-OPP-204）。
     */
    String EX_OPP_204_QUANTITY_MISMATCH = "EX-OPP-204";
    /**
     * 门诊发药：候选库房或库存不满足（EX-OPP-205）。
     */
    String EX_OPP_205_STOCK_OR_LOCATION_INVALID = "EX-OPP-205";
    /**
     * 门诊发药：医保上传参数缺失（EX-OPP-206）。
     */
    String EX_OPP_206_MEDICARE_PARAM_MISSING = "EX-OPP-206";
    /**
     * 门诊发药：医保上传部分失败（EX-OPP-207）。
     */
    String EX_OPP_207_MEDICARE_PARTIAL_FAILURE = "EX-OPP-207";
    /**
     * 门诊发药：作废原因缺失或状态不可作废（EX-OPP-208）。
     */
    String EX_OPP_208_CANCEL_NOT_ALLOWED = "EX-OPP-208";

    /**
     * 门诊药房退药（AS-OP-PHA-002）：元数据或列表/明细加载失败（EX-PHA-002-101）。
     */
    String EX_PHA_002_101_LOAD_FAILED = "EX-PHA-002-101";
    /**
     * 门诊药房退药：当前发放状态不允许退药/拒绝（EX-PHA-002-201）。
     */
    String EX_PHA_002_201_STATUS_NOT_ALLOWED = "EX-PHA-002-201";
    /**
     * 门诊药房退药：重复退药或已拒绝（EX-PHA-002-202）。
     */
    String EX_PHA_002_202_DUPLICATE_OR_CLOSED = "EX-PHA-002-202";
    /**
     * 门诊药房退药：库存账面定位失败（EX-PHA-002-203）。
     */
    String EX_PHA_002_203_INVENTORY_MISSING = "EX-PHA-002-203";
    /**
     * 门诊药房退药：未选择退药项（EX-PHA-002-301）。
     */
    String EX_PHA_002_301_ITEMS_EMPTY = "EX-PHA-002-301";
    /**
     * 门诊药房退药：拒绝退药原因未填写（EX-PHA-002-302）。
     */
    String EX_PHA_002_302_DECLINE_REASON_REQUIRED = "EX-PHA-002-302";
    /**
     * 门诊药房退药：确认退药成功提示码（EX-PHA-002-410）。
     */
    String EX_PHA_002_410_CONFIRM_OK = "EX-PHA-002-410";
    /**
     * 门诊药房退药：拒绝退药成功提示码（EX-PHA-002-411）。
     */
    String EX_PHA_002_411_DECLINE_OK = "EX-PHA-002-411";

    /**
     * 住院药房退药（AS-IP-PHA-003）：元数据加载失败（EX-IPR-101）。
     */
    String EX_IPR_101_LOAD_FAILED = "EX-IPR-101";
    /**
     * 住院药房退药：队列查询失败或分页非法（EX-IPR-102）。
     */
    String EX_IPR_102_QUERY_FAILED = "EX-IPR-102";
    /**
     * 住院药房退药：明细加载失败或就诊无效（EX-IPR-103）。
     */
    String EX_IPR_103_DETAIL_FAILED = "EX-IPR-103";
    /**
     * 住院药房退药：当前状态不允许确认退药（EX-IPR-201）。
     */
    String EX_IPR_201_STATUS_NOT_ALLOWED = "EX-IPR-201";
    /**
     * 住院药房退药：已退药重复确认（EX-IPR-202）。
     */
    String EX_IPR_202_DUPLICATE_REFUND = "EX-IPR-202";
    /**
     * 住院药房退药：批号或库存项缺失（EX-IPR-203）。
     */
    String EX_IPR_203_INVENTORY_MISSING = "EX-IPR-203";
    /**
     * 住院药房退药：未选择退药项（EX-IPR-301）。
     */
    String EX_IPR_301_ITEMS_EMPTY = "EX-IPR-301";

    /**
     * 药品申请错误码：参数非法。
     */
    String MEDICATION_REQUEST_INVALID_ARGUMENT = "MEDICATION_REQUEST_001";
    /**
     * 药品申请错误码：资源不存在。
     */
    String MEDICATION_REQUEST_NOT_FOUND = "MEDICATION_REQUEST_002";
    /**
     * 药品申请错误码：状态不允许。
     */
    String MEDICATION_REQUEST_INVALID_STATUS = "MEDICATION_REQUEST_003";
    /**
     * 药品申请错误码：规则冲突。
     */
    String MEDICATION_REQUEST_RULE_CONFLICT = "MEDICATION_REQUEST_004";

    /**
     * 耗材发放错误码：参数非法。
     */
    String DEVICE_DISPENSE_INVALID_ARGUMENT = "DEVICE_DISPENSE_001";
    /**
     * 耗材发放错误码：资源不存在。
     */
    String DEVICE_DISPENSE_NOT_FOUND = "DEVICE_DISPENSE_002";
    /**
     * 耗材发放错误码：状态不允许。
     */
    String DEVICE_DISPENSE_INVALID_STATUS = "DEVICE_DISPENSE_003";
    /**
     * 耗材发放错误码：规则冲突。
     */
    String DEVICE_DISPENSE_RULE_CONFLICT = "DEVICE_DISPENSE_004";

    /**
     * 诊疗项目定义错误码：参数非法。
     */
    String ACTIVITY_DEFINITION_INVALID_ARGUMENT = "ACTIVITY_DEFINITION_001";
    /**
     * 诊疗项目定义错误码：资源不存在。
     */
    String ACTIVITY_DEFINITION_NOT_FOUND = "ACTIVITY_DEFINITION_002";

    /**
     * 预约队列错误码：参数非法。
     */
    String APPOINTMENT_INVALID_ARGUMENT = "APPOINTMENT_001";
    /**
     * 预约队列错误码：资源不存在。
     */
    String APPOINTMENT_NOT_FOUND = "APPOINTMENT_002";
    /**
     * 预约队列错误码：规则冲突。
     */
    String APPOINTMENT_RULE_CONFLICT = "APPOINTMENT_004";

    /**
     * 耗材请求错误码：参数非法。
     */
    String DEVICE_REQUEST_INVALID_ARGUMENT = "DEVICE_REQUEST_001";
    /**
     * 耗材请求错误码：资源不存在。
     */
    String DEVICE_REQUEST_NOT_FOUND = "DEVICE_REQUEST_002";
    /**
     * 耗材请求错误码：规则冲突。
     */
    String DEVICE_REQUEST_RULE_CONFLICT = "DEVICE_REQUEST_004";

    /**
     * 服务申请错误码：参数非法。
     */
    String SERVICE_REQUEST_INVALID_ARGUMENT = "SERVICE_REQUEST_001";
    /**
     * 服务申请错误码：资源不存在。
     */
    String SERVICE_REQUEST_NOT_FOUND = "SERVICE_REQUEST_002";

    /**
     * 供应发放错误码：参数非法。
     */
    String SUPPLY_DELIVERY_INVALID_ARGUMENT = "SUPPLY_DELIVERY_001";
    /**
     * 供应发放错误码：资源不存在。
     */
    String SUPPLY_DELIVERY_NOT_FOUND = "SUPPLY_DELIVERY_002";

    /**
     * 供应请求错误码：参数非法。
     */
    String SUPPLY_REQUEST_INVALID_ARGUMENT = "SUPPLY_REQUEST_001";
    /**
     * 供应请求错误码：资源不存在。
     */
    String SUPPLY_REQUEST_NOT_FOUND = "SUPPLY_REQUEST_002";

    /**
     * 药库订货单：参数或明细校验失败（AS WHS_PO_001）。
     */
    String WHS_PO_001 = "WHS_PO_001";
    /**
     * 药库订货单：供应商无效或不可用（AS WHS_PO_002）。
     */
    String WHS_PO_002 = "WHS_PO_002";
    /**
     * 药库订货单：状态不允许提交或撤回（AS WHS_PO_003）。
     */
    String WHS_PO_003 = "WHS_PO_003";
    /**
     * 药库订货单：单据当前不可编辑（AS WHS_PO_004）。
     */
    String WHS_PO_004 = "WHS_PO_004";
    /**
     * 药库订货单：删除被下游引用阻断（AS WHS_PO_005，跨域门控）。
     */
    String WHS_PO_005 = "WHS_PO_005";
    /**
     * 药库订货单：列表或选品查询失败（AS WHS_PO_102）。
     */
    String WHS_PO_102 = "WHS_PO_102";
    /**
     * 药库订货单：单据不存在或类型不匹配（AS WHS_PO_103）。
     */
    String WHS_PO_103 = "WHS_PO_103";
    /**
     * 药库订货单：审批重复同意（AS WHS_PO_104）。
     */
    String WHS_PO_104 = "WHS_PO_104";
    /**
     * 药库订货单：并发或乐观锁冲突（AS WHS_PO_105）。
     */
    String WHS_PO_105 = "WHS_PO_105";
    /**
     * 药库订货单：机构上下文缺失或无权限（AS WHS_PO_101）。
     */
    String WHS_PO_101 = "WHS_PO_101";

    /** 药库进货单：建单或复制校验失败（AS WHS_IN_001）。 */
    String WHS_IN_001 = "WHS_IN_001";
    /** 药库进货单：订货单不可引用（AS WHS_IN_002）。 */
    String WHS_IN_002 = "WHS_IN_002";
    /** 药库进货单：状态不允许（AS WHS_IN_003）。 */
    String WHS_IN_003 = "WHS_IN_003";
    /** 药库进货单：禁止编辑（AS WHS_IN_004）。 */
    String WHS_IN_004 = "WHS_IN_004";
    /** 药库进货单：删除被下游引用阻断（AS WHS_IN_005）。 */
    String WHS_IN_005 = "WHS_IN_005";
    /** 药库进货单：机构上下文缺失或无权限（AS WHS_IN_101）。 */
    String WHS_IN_101 = "WHS_IN_101";
    /** 药库进货单：列表或选品查询失败（AS WHS_IN_102）。 */
    String WHS_IN_102 = "WHS_IN_102";
    /** 药库进货单：单据不存在或类型不匹配（AS WHS_IN_103）。 */
    String WHS_IN_103 = "WHS_IN_103";
    /** 药库进货单：重复同意或入库编排失败（AS WHS_IN_104）。 */
    String WHS_IN_104 = "WHS_IN_104";
    /** 药库进货单：并发或乐观锁冲突（AS WHS_IN_105）。 */
    String WHS_IN_105 = "WHS_IN_105";

    /** 药库出库单：参数或明细校验失败（AS WHS_SO_001 / EX-WHS-SO-*）。 */
    String WHS_SO_001 = "WHS_SO_001";
    /** 药库出库单：关联请领单据不可用（AS WHS_SO_002）。 */
    String WHS_SO_002 = "WHS_SO_002";
    /** 药库出库单：状态不允许提交、撤回或驳回（AS WHS_SO_003）。 */
    String WHS_SO_003 = "WHS_SO_003";
    /** 药库出库单：禁止编辑（AS WHS_SO_004）。 */
    String WHS_SO_004 = "WHS_SO_004";
    /** 药库出库单：删除被拒绝（AS WHS_SO_005）。 */
    String WHS_SO_005 = "WHS_SO_005";
    /** 药库出库单：机构上下文缺失或无权限（AS WHS_SO_101）。 */
    String WHS_SO_101 = "WHS_SO_101";
    /** 药库出库单：列表或选品查询失败（AS WHS_SO_102）。 */
    String WHS_SO_102 = "WHS_SO_102";
    /** 药库出库单：单据不存在或类型不匹配（AS WHS_SO_103）。 */
    String WHS_SO_103 = "WHS_SO_103";
    /** 药库出库单：重复同意或出库发放/扣库编排失败（AS WHS_SO_104）。 */
    String WHS_SO_104 = "WHS_SO_104";
    /** 药库出库单：非法传入乐观锁字段 rowVersion（AS WHS_SO_105）。 */
    String WHS_SO_105 = "WHS_SO_105";

    /** 药库盘库单：参数或明细校验失败（AS EX-WHS-ST-001）。 */
    String WHS_ST_001 = "WHS_ST_001";
    /** 药库盘库单：盘点库房非法（AS EX-WHS-ST-002）。 */
    String WHS_ST_002 = "WHS_ST_002";
    /** 药库盘库单：状态不允许提交、撤回或驳回（AS EX-WHS-ST-003）。 */
    String WHS_ST_003 = "WHS_ST_003";
    /** 药库盘库单：禁止编辑（AS EX-WHS-ST-004）。 */
    String WHS_ST_004 = "WHS_ST_004";
    /** 药库盘库单：删除被拒绝（AS EX-WHS-ST-005）。 */
    String WHS_ST_005 = "WHS_ST_005";
    String WHS_ST_101 = "WHS_ST_101";
    /** 药库盘库单：列表或选品查询失败（AS EX-WHS-ST-102）。 */
    String WHS_ST_102 = "WHS_ST_102";
    /** 药库盘库单：单据不存在或类型不匹配（AS EX-WHS-ST-103）。 */
    String WHS_ST_103 = "WHS_ST_103";
    /** 药库盘库单：重复同意或盘点发放/库存编排失败（AS EX-WHS-ST-104）。 */
    String WHS_ST_104 = "WHS_ST_104";
    /** 药库盘库单：并发或乐观锁冲突（AS EX-WHS-ST-105）。 */
    String WHS_ST_105 = "WHS_ST_105";

    /** 药库损益单：参数或明细校验失败（AS WHS_GL_001）。 */
    String WHS_GL_001 = "WHS_GL_001";
    /** 药库损益单：损益库房非法（AS WHS_GL_002）。 */
    String WHS_GL_002 = "WHS_GL_002";
    /** 药库损益单：状态不允许提交、撤回或驳回（AS WHS_GL_003）。 */
    String WHS_GL_003 = "WHS_GL_003";
    /** 药库损益单：禁止编辑（AS WHS_GL_004）。 */
    String WHS_GL_004 = "WHS_GL_004";
    /** 药库损益单：删除被拒绝（AS WHS_GL_005）。 */
    String WHS_GL_005 = "WHS_GL_005";
    /** 药库损益单：机构上下文缺失或无权限（AS WHS_GL_101）。 */
    String WHS_GL_101 = "WHS_GL_101";
    /** 药库损益单：列表或选品查询失败（AS WHS_GL_102）。 */
    String WHS_GL_102 = "WHS_GL_102";
    /** 药库损益单：单据不存在或类型不匹配（AS WHS_GL_103）。 */
    String WHS_GL_103 = "WHS_GL_103";
    /** 药库损益单：重复同意或损益发放/库存编排失败（AS WHS_GL_104）。 */
    String WHS_GL_104 = "WHS_GL_104";
    /** 药库损益单：并发或乐观锁冲突（AS WHS_GL_105）。 */
    String WHS_GL_105 = "WHS_GL_105";

    /** 药库调出单：参数或明细校验失败（AS WHS_TR_001）。 */
    String WHS_TR_001 = "WHS_TR_001";
    /** 药库调出单：来源请领不可引用（AS WHS_TR_002）。 */
    String WHS_TR_002 = "WHS_TR_002";
    /** 药库调出单：状态不允许提交、撤回或驳回（AS WHS_TR_003）。 */
    String WHS_TR_003 = "WHS_TR_003";
    /** 药库调出单：禁止编辑（AS WHS_TR_004）。 */
    String WHS_TR_004 = "WHS_TR_004";
    /** 药库调出单：删除被拒绝（AS WHS_TR_005）。 */
    String WHS_TR_005 = "WHS_TR_005";
    /** 药库调出单：机构上下文缺失或无权限（AS WHS_TR_101）。 */
    String WHS_TR_101 = "WHS_TR_101";
    /** 药库调出单：列表或选品查询失败（AS WHS_TR_102）。 */
    String WHS_TR_102 = "WHS_TR_102";
    /** 药库调出单：单据不存在或类型不匹配（AS WHS_TR_103）。 */
    String WHS_TR_103 = "WHS_TR_103";
    /** 药库调出单：重复同意或调拨发放/库存编排失败（AS WHS_TR_104）。 */
    String WHS_TR_104 = "WHS_TR_104";
    /** 药库调出单：并发或乐观锁冲突（AS WHS_TR_105）。 */
    String WHS_TR_105 = "WHS_TR_105";

    /** 药库调入单：参数或明细校验失败（AS WHS_TI_001）。 */
    String WHS_TI_001 = "WHS_TI_001";
    /** 药库调入单：状态不允许（AS WHS_TI_003）。 */
    String WHS_TI_003 = "WHS_TI_003";
    /** 药库调入单：禁止编辑（AS WHS_TI_004）。 */
    String WHS_TI_004 = "WHS_TI_004";
    /** 药库调入单：删除被拒绝（AS WHS_TI_005）。 */
    String WHS_TI_005 = "WHS_TI_005";
    /** 药库调入单：机构上下文缺失或无权限（AS WHS_TI_101）。 */
    String WHS_TI_101 = "WHS_TI_101";
    /** 药库调入单：列表或选品查询失败（AS WHS_TI_102）。 */
    String WHS_TI_102 = "WHS_TI_102";
    /** 药库调入单：单据不存在或类型不匹配（AS WHS_TI_103）。 */
    String WHS_TI_103 = "WHS_TI_103";
    /** 药库调入单：重复同意或调入编排失败（AS WHS_TI_104）。 */
    String WHS_TI_104 = "WHS_TI_104";
    /** 药库调入单：并发或乐观锁冲突（AS WHS_TI_105）。 */
    String WHS_TI_105 = "WHS_TI_105";

    /** 库存明细：机构/权限上下文缺失或库房范围非法（AS EX-INV-DTL-101）。 */
    String WHS_DTL_101 = "WHS_DTL_101";
    /** 库存明细：页面筛选项加载失败（AS EX-INV-DTL-201）。 */
    String WHS_DTL_201 = "WHS_DTL_201";
    /** 库存明细：现库分页查询失败（AS EX-INV-DTL-202）。 */
    String WHS_DTL_202 = "WHS_DTL_202";
    /** 库存明细：现库底部汇总失败（AS EX-INV-DTL-203）。 */
    String WHS_DTL_203 = "WHS_DTL_203";
    /** 库存明细：现库导出失败（AS EX-INV-DTL-206）。 */
    String WHS_DTL_206 = "WHS_DTL_206";
    /** 库存明细：批量停供未更新任何行（AS EX-INV-DTL-208）。 */
    String WHS_DTL_208 = "WHS_DTL_208";
    /** 库存明细：批量恢复供应未更新任何行（AS EX-INV-DTL-209）。 */
    String WHS_DTL_209 = "WHS_DTL_209";
    /** 库存明细：包装→拆零倍数非法（AS EX-INV-DTL-210）。 */
    String WHS_DTL_210 = "WHS_DTL_210";
    /** 库存明细：并发或乐观锁冲突（AS-SDD §6.2）。 */
    String WHS_DTL_105 = "WHS_DTL_105";

    /** 药库退货单：参数或余量校验失败（AS WHS_RT_001）。 */
    String WHS_RT_001 = "WHS_RT_001";
    /** 药库退货单：来源进货单不可退（AS WHS_RT_002）。 */
    String WHS_RT_002 = "WHS_RT_002";
    /** 药库退货单：状态不允许（AS WHS_RT_003）。 */
    String WHS_RT_003 = "WHS_RT_003";
    /** 药库退货单：禁止编辑（AS WHS_RT_004）。 */
    String WHS_RT_004 = "WHS_RT_004";
    /** 药库退货单：删除被拒绝（AS WHS_RT_005）。 */
    String WHS_RT_005 = "WHS_RT_005";
    /** 药库退货单：机构上下文缺失或无权限（AS WHS_RT_101）。 */
    String WHS_RT_101 = "WHS_RT_101";
    /** 药库退货单：列表或选品查询失败（AS WHS_RT_102）。 */
    String WHS_RT_102 = "WHS_RT_102";
    /** 药库退货单：单据不存在或类型不匹配（AS WHS_RT_103）。 */
    String WHS_RT_103 = "WHS_RT_103";
    /** 药库退货单：重复同意或退货发放/扣库失败（AS WHS_RT_104）。 */
    String WHS_RT_104 = "WHS_RT_104";
    /** 药库退货单：非法传入乐观锁字段（AS WHS_RT_105）。 */
    String WHS_RT_105 = "WHS_RT_105";

    /** 药库退库单（下游退回）：参数或校验失败（AS WHS_RFD_001）。 */
    String WHS_RFD_001 = "WHS_RFD_001";
    /** 药库退库单：来源发放单不可退（AS WHS_RFD_002）。 */
    String WHS_RFD_002 = "WHS_RFD_002";
    /** 药库退库单：状态不允许（AS WHS_RFD_003）。 */
    String WHS_RFD_003 = "WHS_RFD_003";
    /** 药库退库单：禁止编辑（AS WHS_RFD_004）。 */
    String WHS_RFD_004 = "WHS_RFD_004";
    /** 药库退库单：删除被拒绝（AS WHS_RFD_005）。 */
    String WHS_RFD_005 = "WHS_RFD_005";
    /** 药库退库单：机构上下文缺失或无权限（AS WHS_RFD_101）。 */
    String WHS_RFD_101 = "WHS_RFD_101";
    /** 药库退库单：列表或可关联单查询失败（AS WHS_RFD_102）。 */
    String WHS_RFD_102 = "WHS_RFD_102";
    /** 药库退库单：单据不存在或类型不匹配（AS WHS_RFD_103）。 */
    String WHS_RFD_103 = "WHS_RFD_103";
    /** 药库退库单：重复同意或退库发放/库存入账失败（AS WHS_RFD_104）。 */
    String WHS_RFD_104 = "WHS_RFD_104";
    /** 药库退库单：非法传入乐观锁字段或并发冲突（AS WHS_RFD_105）。 */
    String WHS_RFD_105 = "WHS_RFD_105";
    /**
     * 药库退库单：与药房退库互斥，或须先完成药房退库（AS WHS_RFD_020 / RTN-SDD-R-001 对偶调整）。
     */
    String WHS_RFD_020 = "WHS_RFD_020";

    /** 药房请领单：参数或明细校验失败（AS PHA_QL_001）。 */
    String PHA_QL_001 = "PHA_QL_001";
    /** 药房请领单：状态不允许提交、撤回或驳回（AS PHA_QL_003）。 */
    String PHA_QL_003 = "PHA_QL_003";
    /** 药房请领单：禁止编辑（AS PHA_QL_004）。 */
    String PHA_QL_004 = "PHA_QL_004";
    /** 药房请领单：机构上下文缺失或无权限（AS PHA_QL_101）。 */
    String PHA_QL_101 = "PHA_QL_101";
    /** 药房请领单：列表或选品查询失败（AS PHA_QL_102）。 */
    String PHA_QL_102 = "PHA_QL_102";
    /** 药房请领单：单据不存在或类型不匹配（AS PHA_QL_103）。 */
    String PHA_QL_103 = "PHA_QL_103";
    /** 药房请领单：重复同意或出库/入库草稿编排失败（AS PHA_QL_104）。 */
    String PHA_QL_104 = "PHA_QL_104";

    /** 药房入库单：参数或明细校验失败（AS PHA_IN_001）。 */
    String PHA_IN_001 = "PHA_IN_001";
    /** 药房入库单：状态不允许（AS PHA_IN_003）。 */
    String PHA_IN_003 = "PHA_IN_003";
    /** 药房入库单：禁止编辑（AS PHA_IN_004）。 */
    String PHA_IN_004 = "PHA_IN_004";
    /** 药房入库单：删除被拒绝（AS PHA_IN_005）。 */
    String PHA_IN_005 = "PHA_IN_005";
    /** 药房入库单：机构上下文缺失或无权限（AS PHA_IN_101）。 */
    String PHA_IN_101 = "PHA_IN_101";
    /** 药房入库单：列表或选品查询失败（AS PHA_IN_102）。 */
    String PHA_IN_102 = "PHA_IN_102";
    /** 药房入库单：单据不存在或类型不匹配（AS PHA_IN_103）。 */
    String PHA_IN_103 = "PHA_IN_103";
    /** 药房入库单：重复同意或入库编排失败（AS PHA_IN_104）。 */
    String PHA_IN_104 = "PHA_IN_104";
    /** 药房入库单：并发或乐观锁冲突（AS PHA_IN_105）。 */
    String PHA_IN_105 = "PHA_IN_105";

    /** 药房盘点单：参数或明细校验失败（AS EX-PHA-ST-001）。 */
    String PHA_ST_001 = "PHA_ST_001";
    /** 药房盘点单：盘点药房非法（AS EX-PHA-ST-002）。 */
    String PHA_ST_002 = "PHA_ST_002";
    /** 药房盘点单：状态不允许提交、撤回或驳回（AS EX-PHA-ST-003）。 */
    String PHA_ST_003 = "PHA_ST_003";
    /** 药房盘点单：禁止编辑（AS EX-PHA-ST-004）。 */
    String PHA_ST_004 = "PHA_ST_004";
    /** 药房盘点单：删除被拒绝（AS EX-PHA-ST-005）。 */
    String PHA_ST_005 = "PHA_ST_005";
    /** 药房盘点单：机构上下文缺失或无权限（AS EX-PHA-ST-101）。 */
    String PHA_ST_101 = "PHA_ST_101";
    /** 药房盘点单：列表或选品查询失败（AS EX-PHA-ST-102）。 */
    String PHA_ST_102 = "PHA_ST_102";
    /** 药房盘点单：单据不存在或类型不匹配（AS EX-PHA-ST-103）。 */
    String PHA_ST_103 = "PHA_ST_103";
    /** 药房盘点单：重复同意或盘点发放/库存编排失败（AS EX-PHA-ST-104）。 */
    String PHA_ST_104 = "PHA_ST_104";
    /** 药房盘点单：并发或乐观锁冲突（AS EX-PHA-ST-105）。 */
    String PHA_ST_105 = "PHA_ST_105";

    /** 药房损益单：参数或明细校验失败（AS PHA_GL_001）。 */
    String PHA_GL_001 = "PHA_GL_001";
    /** 药房损益单：损益药房非法（AS PHA_GL_002）。 */
    String PHA_GL_002 = "PHA_GL_002";
    /** 药房损益单：状态不允许提交、撤回或驳回（AS PHA_GL_003）。 */
    String PHA_GL_003 = "PHA_GL_003";
    /** 药房损益单：禁止编辑（AS PHA_GL_004）。 */
    String PHA_GL_004 = "PHA_GL_004";
    /** 药房损益单：删除被拒绝（AS PHA_GL_005）。 */
    String PHA_GL_005 = "PHA_GL_005";
    /** 药房损益单：机构上下文缺失或无权限（AS PHA_GL_101）。 */
    String PHA_GL_101 = "PHA_GL_101";
    /** 药房损益单：列表或选品查询失败（AS PHA_GL_102）。 */
    String PHA_GL_102 = "PHA_GL_102";
    /** 药房损益单：单据不存在或类型不匹配（AS PHA_GL_103）。 */
    String PHA_GL_103 = "PHA_GL_103";
    /** 药房损益单：重复同意或损益发放/库存编排失败（AS PHA_GL_104）。 */
    String PHA_GL_104 = "PHA_GL_104";
    /** 药房损益单：并发或乐观锁冲突（AS PHA_GL_105）。 */
    String PHA_GL_105 = "PHA_GL_105";

    /** 科室盘点单：参数或明细校验失败（AS EX-DEP-ST-001）。 */
    String DEP_ST_001 = "DEP_ST_001";
    /** 科室盘点单：盘点科室非法（AS EX-DEP-ST-002）。 */
    String DEP_ST_002 = "DEP_ST_002";
    /** 科室盘点单：状态不允许提交、撤回或驳回（AS EX-DEP-ST-003）。 */
    String DEP_ST_003 = "DEP_ST_003";
    /** 科室盘点单：禁止编辑（AS EX-DEP-ST-004）。 */
    String DEP_ST_004 = "DEP_ST_004";
    /** 科室盘点单：删除被拒绝（AS EX-DEP-ST-005）。 */
    String DEP_ST_005 = "DEP_ST_005";
    /** 科室盘点单：机构上下文缺失或无权限（AS EX-DEP-ST-101）。 */
    String DEP_ST_101 = "DEP_ST_101";
    /** 科室盘点单：列表或选品查询失败（AS EX-DEP-ST-102）。 */
    String DEP_ST_102 = "DEP_ST_102";
    /** 科室盘点单：单据不存在或类型不匹配（AS EX-DEP-ST-103）。 */
    String DEP_ST_103 = "DEP_ST_103";
    /** 科室盘点单：重复同意或盘点发放/库存编排失败（AS EX-DEP-ST-104）。 */
    String DEP_ST_104 = "DEP_ST_104";
    /** 科室盘点单：并发或乐观锁冲突（AS EX-DEP-ST-105）。 */
    String DEP_ST_105 = "DEP_ST_105";

    /** 科室损益单：参数或明细校验失败（AS DEP_GL_001）。 */
    String DEP_GL_001 = "DEP_GL_001";
    /** 科室损益单：损益科室非法（AS DEP_GL_002）。 */
    String DEP_GL_002 = "DEP_GL_002";
    /** 科室损益单：状态不允许提交、撤回或驳回（AS DEP_GL_003）。 */
    String DEP_GL_003 = "DEP_GL_003";
    /** 科室损益单：禁止编辑（AS DEP_GL_004）。 */
    String DEP_GL_004 = "DEP_GL_004";
    /** 科室损益单：删除被拒绝（AS DEP_GL_005）。 */
    String DEP_GL_005 = "DEP_GL_005";
    /** 科室损益单：机构上下文缺失或无权限（AS DEP_GL_101）。 */
    String DEP_GL_101 = "DEP_GL_101";
    /** 科室损益单：列表或选品查询失败（AS DEP_GL_102）。 */
    String DEP_GL_102 = "DEP_GL_102";
    /** 科室损益单：单据不存在或类型不匹配（AS DEP_GL_103）。 */
    String DEP_GL_103 = "DEP_GL_103";
    /** 科室损益单：重复同意或损益发放/库存编排失败（AS DEP_GL_104）。 */
    String DEP_GL_104 = "DEP_GL_104";
    /** 科室损益单：并发或乐观锁冲突（AS DEP_GL_105）。 */
    String DEP_GL_105 = "DEP_GL_105";

    /** 科室退库单：参数或校验失败（AS DEP_RT_001）。 */
    String DEP_RT_001 = "DEP_RT_001";
    /** 科室退库单：来源单据不可退（AS DEP_RT_002）。 */
    String DEP_RT_002 = "DEP_RT_002";
    /** 科室退库单：状态不允许（AS DEP_RT_003）。 */
    String DEP_RT_003 = "DEP_RT_003";
    /** 科室退库单：禁止编辑（AS DEP_RT_004）。 */
    String DEP_RT_004 = "DEP_RT_004";
    /** 科室退库单：删除被拒绝（AS DEP_RT_005）。 */
    String DEP_RT_005 = "DEP_RT_005";
    /** 科室退库单：机构上下文缺失或无权限（AS DEP_RT_101）。 */
    String DEP_RT_101 = "DEP_RT_101";
    /** 科室退库单：列表或可关联单查询失败（AS DEP_RT_102）。 */
    String DEP_RT_102 = "DEP_RT_102";
    /** 科室退库单：单据不存在或类型不匹配（AS DEP_RT_103）。 */
    String DEP_RT_103 = "DEP_RT_103";
    /** 科室退库单：重复同意或退库编排失败（AS DEP_RT_104）。 */
    String DEP_RT_104 = "DEP_RT_104";
    /** 科室退库单：并发或乐观锁冲突（AS DEP_RT_105）。 */
    String DEP_RT_105 = "DEP_RT_105";

    /** 药房退库单：参数或校验失败（AS PHA_RU_001）。 */
    String PHA_RU_001 = "PHA_RU_001";
    /** 药房退库单：来源入库单不可退（AS PHA_RU_002）。 */
    String PHA_RU_002 = "PHA_RU_002";
    /** 药房退库单：状态不允许（AS PHA_RU_003）。 */
    String PHA_RU_003 = "PHA_RU_003";
    /** 药房退库单：禁止编辑（AS PHA_RU_004）。 */
    String PHA_RU_004 = "PHA_RU_004";
    /** 药房退库单：删除被拒绝（AS PHA_RU_005）。 */
    String PHA_RU_005 = "PHA_RU_005";
    /** 药房退库单：机构上下文缺失或无权限（AS PHA_RU_101）。 */
    String PHA_RU_101 = "PHA_RU_101";
    /** 药房退库单：列表或可关联单查询失败（AS PHA_RU_102）。 */
    String PHA_RU_102 = "PHA_RU_102";
    /** 药房退库单：单据不存在或类型不匹配（AS PHA_RU_103）。 */
    String PHA_RU_103 = "PHA_RU_103";
    /** 药房退库单：重复同意或退库编排失败（AS PHA_RU_104）。 */
    String PHA_RU_104 = "PHA_RU_104";
    /** 药房退库单：非法传入乐观锁字段或并发冲突（AS PHA_RU_105）。 */
    String PHA_RU_105 = "PHA_RU_105";
    /** 药房退库单：与药库退库（下游）互斥（AS PHA_RU_020 / PHA-RU-SDD-R-001）。 */
    String PHA_RU_020 = "PHA_RU_020";

    /**
     * 库存项错误码：参数非法。
     */
    String INVENTORY_ITEM_INVALID_ARGUMENT = "INVENTORY_ITEM_001";
    /**
     * 库存项错误码：资源不存在。
     */
    String INVENTORY_ITEM_NOT_FOUND = "INVENTORY_ITEM_002";

    /**
     * 号源日程错误码：参数非法。
     */
    String SCHEDULE_INVALID_ARGUMENT = "SCHEDULE_001";
    /**
     * 号源日程错误码：资源不存在。
     */
    String SCHEDULE_NOT_FOUND = "SCHEDULE_002";

    /**
     * 号源时段错误码：参数非法。
     */
    String SLOT_INVALID_ARGUMENT = "SLOT_001";
    /**
     * 号源时段错误码：资源不存在。
     */
    String SLOT_NOT_FOUND = "SLOT_002";

    /**
     * 执行科室规则错误码：参数非法。
     */
    String ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT = "ACTIVITY_EXECUTION_LOCATION_001";
    /**
     * 执行科室规则错误码：资源不存在。
     */
    String ACTIVITY_EXECUTION_LOCATION_NOT_FOUND = "ACTIVITY_EXECUTION_LOCATION_002";
    /**
     * 执行科室规则错误码：规则冲突。
     */
    String ACTIVITY_EXECUTION_LOCATION_RULE_CONFLICT = "ACTIVITY_EXECUTION_LOCATION_003";

    /**
     * 频次错误码：参数非法。
     */
    String FREQUENCY_INVALID_ARGUMENT = "FREQUENCY_001";
    /**
     * 频次错误码：资源不存在。
     */
    String FREQUENCY_NOT_FOUND = "FREQUENCY_002";
    /**
     * 频次错误码：状态不允许。
     */
    String FREQUENCY_INVALID_STATUS = "FREQUENCY_003";
    /**
     * 频次错误码：编码冲突。
     */
    String FREQUENCY_RATE_CODE_CONFLICT = "FREQUENCY_004";
    /**
     * 频次错误码：并发冲突。
     */
    String FREQUENCY_CONCURRENCY_CONFLICT = "FREQUENCY_005";

    /**
     * 样本定义错误码：参数非法。
     */
    String SPECIMEN_DEFINITION_INVALID_ARGUMENT = "SPECIMEN_DEFINITION_001";
    /**
     * 样本定义错误码：资源不存在。
     */
    String SPECIMEN_DEFINITION_NOT_FOUND = "SPECIMEN_DEFINITION_002";
    /**
     * 样本定义错误码：状态不允许。
     */
    String SPECIMEN_DEFINITION_INVALID_STATUS = "SPECIMEN_DEFINITION_003";
    /**
     * 样本定义错误码：业务编码冲突。
     */
    String SPECIMEN_DEFINITION_BUS_NO_CONFLICT = "SPECIMEN_DEFINITION_004";
    /**
     * 样本定义错误码：规则冲突。
     */
    String SPECIMEN_DEFINITION_RULE_CONFLICT = "SPECIMEN_DEFINITION_005";

    /**
     * 供应商错误码：参数非法。
     */
    String SUPPLIER_INVALID_ARGUMENT = "SUPPLIER_001";
    /**
     * 供应商错误码：资源不存在。
     */
    String SUPPLIER_NOT_FOUND = "SUPPLIER_002";
    /**
     * 供应商错误码：状态不允许。
     */
    String SUPPLIER_INVALID_STATUS = "SUPPLIER_003";
    /**
     * 供应商错误码：业务编号冲突。
     */
    String SUPPLIER_BUS_NO_CONFLICT = "SUPPLIER_004";
    /**
     * 供应商错误码：信用代码冲突。
     */
    String SUPPLIER_CREDIT_CODE_CONFLICT = "SUPPLIER_005";

    /**
     * 术语映射错误码：参数非法。
     */
    String TERMINOLOGY_MAPPING_INVALID_ARGUMENT = "TERMINOLOGY_MAPPING_001";
    /**
     * 术语映射错误码：资源不存在。
     */
    String TERMINOLOGY_MAPPING_NOT_FOUND = "TERMINOLOGY_MAPPING_002";
    /**
     * 术语映射错误码：状态不允许。
     */
    String TERMINOLOGY_MAPPING_INVALID_STATUS = "TERMINOLOGY_MAPPING_003";
    /**
     * 术语映射错误码：身份键冲突。
     */
    String TERMINOLOGY_MAPPING_IDENTITY_CONFLICT = "TERMINOLOGY_MAPPING_004";

    /**
     * 追溯码记录错误码：参数非法。
     */
    String TRACE_NUMBER_RECORD_INVALID_ARGUMENT = "TRACE_NUMBER_RECORD_001";
    /**
     * 追溯码记录错误码：码不在库或与药品/批号/库房不匹配。
     */
    String TRACE_NUMBER_RECORD_NOT_IN_STOCK_OR_MISMATCH = "TRACE_NUMBER_RECORD_002";
    /**
     * 追溯码记录错误码：同单重复追溯码。
     */
    String TRACE_NUMBER_RECORD_DUPLICATE_IN_REQUEST = "TRACE_NUMBER_RECORD_003";

    /**
     * 取药科室模板错误码：参数非法。
     */
    String SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT = "SERVICE_LOCATION_TEMPLATE_001";

    /**
     * 诊断常用模板错误码：参数非法。
     */
    String CONDITION_TEMPLATE_INVALID_ARGUMENT = "CONDITION_TEMPLATE_001";
    /**
     * 诊断常用模板错误码：资源不存在。
     */
    String CONDITION_TEMPLATE_NOT_FOUND = "CONDITION_TEMPLATE_002";

    /**
     * 自动计费规则错误码：参数非法。
     */
    String CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT = "CHARGE_ITEM_AUTO_GEN_001";
    /**
     * 自动计费规则错误码：资源不存在。
     */
    String CHARGE_ITEM_AUTO_GEN_NOT_FOUND = "CHARGE_ITEM_AUTO_GEN_002";
    /**
     * 自动计费规则错误码：状态不允许。
     */
    String CHARGE_ITEM_AUTO_GEN_INVALID_STATUS = "CHARGE_ITEM_AUTO_GEN_003";
    /**
     * 自动计费规则错误码：组合键冲突。
     */
    String CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT = "CHARGE_ITEM_AUTO_GEN_004";

    /**
     * 就诊历史错误码：参数非法。
     */
    String ENCOUNTER_HISTORY_INVALID_ARGUMENT = "ENCOUNTER_HISTORY_001";
    /**
     * 就诊历史错误码：资源不存在。
     */
    String ENCOUNTER_HISTORY_NOT_FOUND = "ENCOUNTER_HISTORY_002";

    /**
     * 诊疗耗材绑定错误码：参数非法。
     */
    String SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT = "SERVICE_DEVICE_TEMPLATE_001";
    /**
     * 诊疗耗材绑定错误码：资源不存在。
     */
    String SERVICE_DEVICE_TEMPLATE_NOT_FOUND = "SERVICE_DEVICE_TEMPLATE_002";
    /**
     * 诊疗耗材绑定错误码：状态不允许。
     */
    String SERVICE_DEVICE_TEMPLATE_INVALID_STATUS = "SERVICE_DEVICE_TEMPLATE_003";

    /**
     * 价格调整错误码：参数非法。
     */
    String PRICE_ADJUSTMENT_INVALID_ARGUMENT = "PRICE_ADJUSTMENT_001";
    /**
     * 价格调整错误码：资源不存在。
     */
    String PRICE_ADJUSTMENT_NOT_FOUND = "PRICE_ADJUSTMENT_002";
    /**
     * 价格调整错误码：状态不允许。
     */
    String PRICE_ADJUSTMENT_INVALID_STATUS = "PRICE_ADJUSTMENT_003";

    /**
     * 组套模板错误码：参数非法。
     */
    String ORDER_TEMPLATE_INVALID_ARGUMENT = "ORDER_TEMPLATE_001";
    /**
     * 组套模板错误码：资源不存在。
     */
    String ORDER_TEMPLATE_NOT_FOUND = "ORDER_TEMPLATE_002";
    /**
     * 组套模板错误码：状态不允许。
     */
    String ORDER_TEMPLATE_INVALID_STATUS = "ORDER_TEMPLATE_003";
    /**
     * 组套模板错误码：模板编码冲突。
     */
    String ORDER_TEMPLATE_CODE_CONFLICT = "ORDER_TEMPLATE_004";

    /**
     * 过敏与不耐受错误码：参数非法。
     */
    String ALLERGY_INTOLERANCE_INVALID_ARGUMENT = "ALLERGY_INTOLERANCE_001";
    /**
     * 过敏与不耐受错误码：资源不存在。
     */
    String ALLERGY_INTOLERANCE_NOT_FOUND = "ALLERGY_INTOLERANCE_002";
    /**
     * 过敏与不耐受错误码：规则冲突。
     */
    String ALLERGY_INTOLERANCE_RULE_CONFLICT = "ALLERGY_INTOLERANCE_003";

    /**
     * 诊断定义错误码：参数非法。
     */
    String CONDITION_DEFINITION_INVALID_ARGUMENT = "CONDITION_DEFINITION_001";
    /**
     * 诊断定义错误码：资源不存在。
     */
    String CONDITION_DEFINITION_NOT_FOUND = "CONDITION_DEFINITION_002";
    /**
     * 诊断定义错误码：编码冲突。
     */
    String CONDITION_DEFINITION_CODE_CONFLICT = "CONDITION_DEFINITION_003";
    /**
     * 诊断定义错误码：状态不允许。
     */
    String CONDITION_DEFINITION_INVALID_STATUS = "CONDITION_DEFINITION_004";

    /**
     * 诊断记录错误码：参数非法。
     */
    String CONDITION_INVALID_ARGUMENT = "CONDITION_001";
    /**
     * 诊断记录错误码：资源不存在。
     */
    String CONDITION_NOT_FOUND = "CONDITION_002";
    /**
     * 诊断记录错误码：规则冲突。
     */
    String CONDITION_RULE_CONFLICT = "CONDITION_003";

    /**
     * 执行记录错误码：参数非法。
     */
    String PROCEDURE_INVALID_ARGUMENT = "PROCEDURE_001";
    /**
     * 执行记录错误码：资源不存在。
     */
    String PROCEDURE_NOT_FOUND = "PROCEDURE_002";
    /**
     * 执行记录错误码：状态不允许。
     */
    String PROCEDURE_INVALID_STATUS = "PROCEDURE_003";
    /**
     * 执行记录错误码：规则冲突。
     */
    String PROCEDURE_RULE_CONFLICT = "PROCEDURE_004";

    /**
     * 画布打印错误码：资源不存在。
     */
    String PRT_NOT_FOUND = "PRT-404";
    /**
     * 画布打印错误码：参数或版式非法。
     */
    String PRT_INVALID = "PRT-422";
}
