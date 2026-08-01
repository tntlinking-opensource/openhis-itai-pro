/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
     * 位置错误码：层级编码（busNo）冲突。
     */
    String LOCATION_BUS_NO_CONFLICT = "LOCATION_006";

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
     * 机构错误码：层级编码（busNo）冲突。
     */
    String ORGANIZATION_BUS_NO_CONFLICT = "ORGANIZATION_006";

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

    /** 交账状态不允许当前写动作（非 SUBMITTED 等）。 */
    String CASHIER_SETTLEMENT_INVALID_STATUS = "CASHIER_SETTLEMENT_005";

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
     * 药库订货单：科室采购申请不可引用（未同意、类型不符或已被未删除订货占用，AS WHS_PO_106 / BR-WHS-PO-012）。
     */
    String WHS_PO_106 = "WHS_PO_106";
    /**
     * 药库订货单：机构上下文缺失或无权限（AS WHS_PO_101）。
     */
    String WHS_PO_101 = "WHS_PO_101";

    /** 科室采购申请单：参数或明细校验失败（AS DEP_CPR_001 / EX-DEP-CPR-001）。 */
    String DEP_CPR_001 = "DEP_CPR_001";
    /** 科室采购申请单：状态不允许提交、撤回、同意或驳回（AS DEP_CPR_003 / EX-DEP-CPR-003）。 */
    String DEP_CPR_003 = "DEP_CPR_003";
    /** 科室采购申请单：禁止编辑（AS DEP_CPR_004 / EX-DEP-CPR-004）。 */
    String DEP_CPR_004 = "DEP_CPR_004";
    /** 科室采购申请单：删除被拒绝（AS DEP_CPR_005 / EX-DEP-CPR-005）。 */
    String DEP_CPR_005 = "DEP_CPR_005";
    /** 科室采购申请单：机构上下文缺失或主数据加载失败（AS DEP_CPR_101 / EX-DEP-CPR-101）。 */
    String DEP_CPR_101 = "DEP_CPR_101";
    /** 科室采购申请单：列表或选品查询失败（AS DEP_CPR_102 / EX-DEP-CPR-102）。 */
    String DEP_CPR_102 = "DEP_CPR_102";
    /** 科室采购申请单：单据不存在或类型不匹配（AS DEP_CPR_103 / EX-DEP-CPR-103）。 */
    String DEP_CPR_103 = "DEP_CPR_103";
    /** 科室采购申请单：重复同意（AS DEP_CPR_104）。 */
    String DEP_CPR_104 = "DEP_CPR_104";
    /** 科室采购申请单：并发或乐观锁冲突（AS DEP_CPR_105 / EX-DEP-CPR-105）。 */
    String DEP_CPR_105 = "DEP_CPR_105";

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

    /** 库存期初：已关闭或业务封锁仍试图写期初（AS EX-BAS-INV-OPN-001）。 */
    String BAS_INV_OPN_001 = "BAS_INV_OPN_001";
    /** 库存期初：建单/行字段校验失败（AS EX-BAS-INV-OPN-002）。 */
    String BAS_INV_OPN_002 = "BAS_INV_OPN_002";
    /** 库存期初：编辑态非法（AS EX-BAS-INV-OPN-003）。 */
    String BAS_INV_OPN_003 = "BAS_INV_OPN_003";
    /** 库存期初：提交审批失败（AS EX-BAS-INV-OPN-004）。 */
    String BAS_INV_OPN_004 = "BAS_INV_OPN_004";
    /** 库存期初：撤回审批失败（AS EX-BAS-INV-OPN-005）。 */
    String BAS_INV_OPN_005 = "BAS_INV_OPN_005";
    /** 库存期初：同意闸门失败（已关闭/已封锁）（AS EX-BAS-INV-OPN-006）。 */
    String BAS_INV_OPN_006 = "BAS_INV_OPN_006";
    /** 库存期初：码已在院等冲突（AS EX-BAS-INV-OPN-007；不再用于码数硬拒）。 */
    String BAS_INV_OPN_007 = "BAS_INV_OPN_007";
    /** 库存期初：追溯码单内重复或格式非法（AS EX-BAS-INV-OPN-008）。 */
    String BAS_INV_OPN_008 = "BAS_INV_OPN_008";
    /** 库存期初：库存入账失败（AS EX-BAS-INV-OPN-009）。 */
    String BAS_INV_OPN_009 = "BAS_INV_OPN_009";
    /** 库存期初：驳回失败（AS EX-BAS-INV-OPN-010）。 */
    String BAS_INV_OPN_010 = "BAS_INV_OPN_010";
    /** 库存期初：删除失败（AS EX-BAS-INV-OPN-011）。 */
    String BAS_INV_OPN_011 = "BAS_INV_OPN_011";
    /** 库存期初：Excel 整单失败（AS EX-BAS-INV-OPN-012）。 */
    String BAS_INV_OPN_012 = "BAS_INV_OPN_012";
    /** 库存期初：关闭失败（AS EX-BAS-INV-OPN-013）。 */
    String BAS_INV_OPN_013 = "BAS_INV_OPN_013";
    /** 库存期初：关闭后禁止作废（AS EX-BAS-INV-OPN-014）。 */
    String BAS_INV_OPN_014 = "BAS_INV_OPN_014";
    /** 库存期初：作废回滚失败（AS EX-BAS-INV-OPN-015）。 */
    String BAS_INV_OPN_015 = "BAS_INV_OPN_015";
    /** 库存期初：复制失败（AS EX-BAS-INV-OPN-016）。 */
    String BAS_INV_OPN_016 = "BAS_INV_OPN_016";
    /** 库存期初：同库已有审核中单仍提交（AS EX-BAS-INV-OPN-017）。 */
    String BAS_INV_OPN_017 = "BAS_INV_OPN_017";
    /** 库存期初：关闭时仍有审核中单（AS EX-BAS-INV-OPN-018）。 */
    String BAS_INV_OPN_018 = "BAS_INV_OPN_018";
    /** 库存期初：提交时无明细行（AS EX-BAS-INV-OPN-019）。 */
    String BAS_INV_OPN_019 = "BAS_INV_OPN_019";
    /** 库存期初：页初始化主数据失败（AS EX-BAS-INV-OPN-101）。 */
    String BAS_INV_OPN_101 = "BAS_INV_OPN_101";
    /** 库存期初：列表查询失败（AS EX-BAS-INV-OPN-102）。 */
    String BAS_INV_OPN_102 = "BAS_INV_OPN_102";
    /** 库存期初：单据不存在或类型不符（AS EX-BAS-INV-OPN-103）。 */
    String BAS_INV_OPN_103 = "BAS_INV_OPN_103";
    /** 库存期初：选品检索失败（AS EX-BAS-INV-OPN-104）。 */
    String BAS_INV_OPN_104 = "BAS_INV_OPN_104";
    /** 库存期初：模板下载失败（AS EX-BAS-INV-OPN-105）。 */
    String BAS_INV_OPN_105 = "BAS_INV_OPN_105";

    /** 药库调出单：参数或明细校验失败（AS WHS_TR_001）。 */
    String WHS_TR_001 = "WHS_TR_001";
    /** 药库调出单：不支持关联药房请领上游（AS WHS_TR_002）。 */
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
    /** 药库调入单：不支持手工新建/审批（须由调出 agree 生成壳单）（AS WHS_TI_002）。 */
    String WHS_TI_002 = "WHS_TI_002";
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

    /** 药房调出单：参数或明细校验失败。 */
    String PHA_TR_001 = "PHA_TR_001";
    /** 药房调出单：不支持关联药房请领上游（AS PHA_TR_002）。 */
    String PHA_TR_002 = "PHA_TR_002";
    /** 药房调出单：状态不允许提交、撤回或驳回。 */
    String PHA_TR_003 = "PHA_TR_003";
    /** 药房调出单：禁止编辑。 */
    String PHA_TR_004 = "PHA_TR_004";
    /** 药房调出单：删除被拒绝。 */
    String PHA_TR_005 = "PHA_TR_005";
    /** 药房调出单：机构上下文缺失或无权限。 */
    String PHA_TR_101 = "PHA_TR_101";
    /** 药房调出单：列表或选品查询失败。 */
    String PHA_TR_102 = "PHA_TR_102";
    /** 药房调出单：单据不存在或类型不匹配。 */
    String PHA_TR_103 = "PHA_TR_103";
    /** 药房调出单：重复同意或调拨发放/库存编排失败。 */
    String PHA_TR_104 = "PHA_TR_104";
    /** 药房调出单：并发或乐观锁冲突。 */
    String PHA_TR_105 = "PHA_TR_105";

    /** 药房调入单：参数或明细校验失败。 */
    String PHA_TI_001 = "PHA_TI_001";
    /** 药房调入单：不支持手工新建/审批（须由调出 agree 生成壳单）。 */
    String PHA_TI_002 = "PHA_TI_002";
    /** 药房调入单：状态不允许。 */
    String PHA_TI_003 = "PHA_TI_003";
    /** 药房调入单：禁止编辑。 */
    String PHA_TI_004 = "PHA_TI_004";
    /** 药房调入单：删除被拒绝。 */
    String PHA_TI_005 = "PHA_TI_005";
    /** 药房调入单：机构上下文缺失或无权限。 */
    String PHA_TI_101 = "PHA_TI_101";
    /** 药房调入单：列表或选品查询失败。 */
    String PHA_TI_102 = "PHA_TI_102";
    /** 药房调入单：单据不存在或类型不匹配。 */
    String PHA_TI_103 = "PHA_TI_103";
    /** 药房调入单：重复同意或调入编排失败。 */
    String PHA_TI_104 = "PHA_TI_104";
    /** 药房调入单：并发或乐观锁冲突。 */
    String PHA_TI_105 = "PHA_TI_105";

    /** 科室调出单：参数或明细校验失败。 */
    String DEPT_TR_001 = "DEPT_TR_001";
    /** 科室调出单：不支持关联科室请领上游。 */
    String DEPT_TR_002 = "DEPT_TR_002";
    /** 科室调出单：状态不允许提交、撤回或驳回。 */
    String DEPT_TR_003 = "DEPT_TR_003";
    /** 科室调出单：禁止编辑。 */
    String DEPT_TR_004 = "DEPT_TR_004";
    /** 科室调出单：删除被拒绝。 */
    String DEPT_TR_005 = "DEPT_TR_005";
    /** 科室调出单：机构上下文缺失或无权限。 */
    String DEPT_TR_101 = "DEPT_TR_101";
    /** 科室调出单：列表或选品查询失败。 */
    String DEPT_TR_102 = "DEPT_TR_102";
    /** 科室调出单：单据不存在或类型不匹配。 */
    String DEPT_TR_103 = "DEPT_TR_103";
    /** 科室调出单：重复同意或调拨发放/库存编排失败。 */
    String DEPT_TR_104 = "DEPT_TR_104";
    /** 科室调出单：并发或乐观锁冲突。 */
    String DEPT_TR_105 = "DEPT_TR_105";

    /** 科室调入单：参数或明细校验失败。 */
    String DEPT_TI_001 = "DEPT_TI_001";
    /** 科室调入单：不支持手工新建/审批（须由调出 agree 生成壳单）。 */
    String DEPT_TI_002 = "DEPT_TI_002";
    /** 科室调入单：状态不允许。 */
    String DEPT_TI_003 = "DEPT_TI_003";
    /** 科室调入单：禁止编辑。 */
    String DEPT_TI_004 = "DEPT_TI_004";
    /** 科室调入单：删除被拒绝。 */
    String DEPT_TI_005 = "DEPT_TI_005";
    /** 科室调入单：机构上下文缺失或无权限。 */
    String DEPT_TI_101 = "DEPT_TI_101";
    /** 科室调入单：列表或选品查询失败。 */
    String DEPT_TI_102 = "DEPT_TI_102";
    /** 科室调入单：单据不存在或类型不匹配。 */
    String DEPT_TI_103 = "DEPT_TI_103";
    /** 科室调入单：重复同意或调入编排失败。 */
    String DEPT_TI_104 = "DEPT_TI_104";
    /** 科室调入单：并发或乐观锁冲突。 */
    String DEPT_TI_105 = "DEPT_TI_105";

    /** 科室入库单：参数或明细校验失败。 */
    String DEPT_IN_001 = "DEPT_IN_001";
    /** 科室入库单：不支持手工新建/改单/审批（须由药库出库 agree 生成壳单）。 */
    String DEPT_IN_002 = "DEPT_IN_002";
    /** 科室入库单：状态不允许。 */
    String DEPT_IN_003 = "DEPT_IN_003";
    /** 科室入库单：禁止编辑。 */
    String DEPT_IN_004 = "DEPT_IN_004";
    /** 科室入库单：删除被拒绝。 */
    String DEPT_IN_005 = "DEPT_IN_005";
    /** 科室入库单：机构上下文缺失或无权限。 */
    String DEPT_IN_101 = "DEPT_IN_101";
    /** 科室入库单：列表或选品查询失败。 */
    String DEPT_IN_102 = "DEPT_IN_102";
    /** 科室入库单：单据不存在或类型不匹配。 */
    String DEPT_IN_103 = "DEPT_IN_103";
    /** 科室入库单：重复同意或入库编排失败。 */
    String DEPT_IN_104 = "DEPT_IN_104";
    /** 科室入库单：并发或乐观锁冲突。 */
    String DEPT_IN_105 = "DEPT_IN_105";

    /** 科室退库单：参数或校验失败。 */
    String DEPT_RTW_001 = "DEPT_RTW_001";
    /** 科室退库单：来源入库单不可退。 */
    String DEPT_RTW_002 = "DEPT_RTW_002";
    /** 科室退库单：状态不允许。 */
    String DEPT_RTW_003 = "DEPT_RTW_003";
    /** 科室退库单：禁止编辑。 */
    String DEPT_RTW_004 = "DEPT_RTW_004";
    /** 科室退库单：删除被拒绝。 */
    String DEPT_RTW_005 = "DEPT_RTW_005";
    /** 科室退库单：机构上下文缺失或无权限。 */
    String DEPT_RTW_101 = "DEPT_RTW_101";
    /** 科室退库单：列表或可关联单查询失败。 */
    String DEPT_RTW_102 = "DEPT_RTW_102";
    /** 科室退库单：单据不存在或类型不匹配。 */
    String DEPT_RTW_103 = "DEPT_RTW_103";
    /** 科室退库单：重复同意或退库编排失败。 */
    String DEPT_RTW_104 = "DEPT_RTW_104";
    /** 科室退库单：非法传入乐观锁字段或并发冲突。 */
    String DEPT_RTW_105 = "DEPT_RTW_105";
    /** 科室退库单：与退库入库壳单配对（历史兼容）。 */
    String DEPT_RTW_020 = "DEPT_RTW_020";

    /** 库存明细：机构/权限上下文缺失或库房范围非法（AS EX-INV-DTL-101）。 */
    String WHS_DTL_101 = "WHS_DTL_101";
    /** 库存明细：页面筛选项加载失败（AS EX-INV-DTL-201）。 */
    String WHS_DTL_201 = "WHS_DTL_201";
    /** 库存明细：现库分页查询失败（AS EX-INV-DTL-202）。 */
    String WHS_DTL_202 = "WHS_DTL_202";
    /** 库存明细：现库底部汇总失败（AS EX-INV-DTL-203）。 */
    String WHS_DTL_203 = "WHS_DTL_203";
    /** 库存明细：备份分页失败（AS EX-INV-DTL-204）。 */
    String WHS_DTL_204 = "WHS_DTL_204";
    /** 库存明细：备份底部汇总失败（AS EX-INV-DTL-205）。 */
    String WHS_DTL_205 = "WHS_DTL_205";
    /** 库存明细：现库导出失败（AS EX-INV-DTL-206）。 */
    String WHS_DTL_206 = "WHS_DTL_206";
    /** 库存明细：备份导出失败（AS EX-INV-DTL-207）。 */
    String WHS_DTL_207 = "WHS_DTL_207";
    /** 库存明细：批量停供未更新任何行（AS EX-INV-DTL-208）。 */
    String WHS_DTL_208 = "WHS_DTL_208";
    /** 库存明细：批量恢复供应未更新任何行（AS EX-INV-DTL-209）。 */
    String WHS_DTL_209 = "WHS_DTL_209";
    /** 库存明细：包装→拆零倍数非法（AS EX-INV-DTL-210）。 */
    String WHS_DTL_210 = "WHS_DTL_210";
    /** 库存明细：定时快照落库失败（AS EX-INV-DTL-211）。 */
    String WHS_DTL_211 = "WHS_DTL_211";
    /** 库存明细：备份单号列表失败（AS EX-INV-DTL-212）。 */
    String WHS_DTL_212 = "WHS_DTL_212";
    /** 库存明细：现库行本库仍在库追溯码查询失败（AS EX-INV-DTL-213）。 */
    String WHS_DTL_213 = "WHS_DTL_213";
    /** 库存明细：并发或乐观锁冲突（AS-SDD §6.2）。 */
    String WHS_DTL_105 = "WHS_DTL_105";

    /** 库房期初期末明细：页面初始化失败（AS EX-INV-SET-201）。 */
    String INV_SET_201 = "INV_SET_201";
    /** 库房期初期末明细：未选择库房（AS EX-INV-SET-202）。 */
    String INV_SET_202 = "INV_SET_202";
    /** 库房期初期末明细：未选择期初备份单号（AS EX-INV-SET-203）。 */
    String INV_SET_203 = "INV_SET_203";
    /** 库房期初期末明细：期初备份无该库位数据（AS EX-INV-SET-204）。 */
    String INV_SET_204 = "INV_SET_204";
    /** 库房期初期末明细：查询或导出装配失败（AS EX-INV-SET-205）。 */
    String INV_SET_205 = "INV_SET_205";
    /** 库房期初期末明细：无权查看该库房（AS EX-INV-SET-206）。 */
    String INV_SET_206 = "INV_SET_206";

    /** 护士工作量：页面初始化失败（AS EX-RPT-NWL-101）。 */
    String RPT_NWL_101 = "RPT_NWL_101";
    /** 护士工作量：未选门诊/住院或日期非法（AS EX-RPT-NWL-102）。 */
    String RPT_NWL_102 = "RPT_NWL_102";
    /** 护士工作量：汇总或明细查询失败（AS EX-RPT-NWL-104）。 */
    String RPT_NWL_104 = "RPT_NWL_104";
    /** 护士工作量：导出失败或超上限（AS EX-RPT-NWL-105）。 */
    String RPT_NWL_105 = "RPT_NWL_105";
    /** 护士工作量：无明细可导出（AS EX-RPT-NWL-201）。 */
    String RPT_NWL_201 = "RPT_NWL_201";

    /** 单据详情报表：页面初始化失败（AS EX-INV-DDC-201）。 */
    String INV_DDC_201 = "INV_DDC_201";
    /** 单据详情报表：单据类型非法或不在本域白名单（AS EX-INV-DDC-202）。 */
    String INV_DDC_202 = "INV_DDC_202";
    /** 单据详情报表：分页查询失败（AS EX-INV-DDC-203）。 */
    String INV_DDC_203 = "INV_DDC_203";
    /** 单据详情报表：操作单位数量换算失败（AS EX-INV-DDC-204）。 */
    String INV_DDC_204 = "INV_DDC_204";
    /** 单据详情报表：导出失败或超行上限（AS EX-INV-DDC-205）。 */
    String INV_DDC_205 = "INV_DDC_205";
    /** 单据详情报表：合计计算失败（AS EX-INV-DDC-206）。 */
    String INV_DDC_206 = "INV_DDC_206";
    /** 单据详情报表：审批时间区间非法（AS EX-INV-DDC-207）。 */
    String INV_DDC_207 = "INV_DDC_207";

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

    /** 退库入库单：参数或校验失败（AS WHS_RSI_001）。 */
    String WHS_RSI_001 = "WHS_RSI_001";
    /** 退库入库单：来源单据不可引用（AS WHS_RSI_002）。 */
    String WHS_RSI_002 = "WHS_RSI_002";
    /** 退库入库单：状态不允许（AS WHS_RSI_003）。 */
    String WHS_RSI_003 = "WHS_RSI_003";
    /** 退库入库单：禁止编辑（AS WHS_RSI_004）。 */
    String WHS_RSI_004 = "WHS_RSI_004";
    /** 退库入库单：删除被拒绝（AS WHS_RSI_005）。 */
    String WHS_RSI_005 = "WHS_RSI_005";
    /** 退库入库单：不支持手工新建/审批（须由药房/科室退库 agree 生成壳单）（AS WHS_RSI_006）。 */
    String WHS_RSI_006 = "WHS_RSI_006";
    /** 退库入库单：机构上下文缺失或无权限（AS WHS_RSI_101）。 */
    String WHS_RSI_101 = "WHS_RSI_101";
    /** 退库入库单：列表查询失败（AS WHS_RSI_102）。 */
    String WHS_RSI_102 = "WHS_RSI_102";
    /** 退库入库单：单据不存在或类型不匹配（AS WHS_RSI_103）。 */
    String WHS_RSI_103 = "WHS_RSI_103";
    /** 退库入库单：重复同意或编排失败（AS WHS_RSI_104，历史兼容）。 */
    String WHS_RSI_104 = "WHS_RSI_104";
    /** 退库入库单：非法传入乐观锁字段或并发冲突（AS WHS_RSI_105）。 */
    String WHS_RSI_105 = "WHS_RSI_105";
    /**
     * 退库入库单：与药房退库互斥（历史兼容，AS WHS_RSI_020）。
     */
    String WHS_RSI_020 = "WHS_RSI_020";

    /** 药房请领单：参数或明细校验失败（AS PHA_QL_001）。 */
    String PHA_QL_001 = "PHA_QL_001";
    /** 药房请领单：状态不允许提交、撤回或驳回（AS PHA_QL_003）。 */
    String PHA_QL_003 = "PHA_QL_003";
    /** 药房请领单：禁止编辑（AS PHA_QL_004）。 */
    String PHA_QL_004 = "PHA_QL_004";
    /** 药房请领单：删除被拒绝（下游出库引用等，AS PHA_QL_005）。 */
    String PHA_QL_005 = "PHA_QL_005";
    /** 药房请领单：机构上下文缺失或无权限（AS PHA_QL_101）。 */
    String PHA_QL_101 = "PHA_QL_101";
    /** 药房请领单：列表或选品查询失败（AS PHA_QL_102）。 */
    String PHA_QL_102 = "PHA_QL_102";
    /** 药房请领单：单据不存在或类型不匹配（AS PHA_QL_103）。 */
    String PHA_QL_103 = "PHA_QL_103";
    /** 药房请领单：重复同意或状态不允许同意（AS PHA_QL_104）。 */
    String PHA_QL_104 = "PHA_QL_104";
    /** 药房请领单：并发编辑冲突（AS PHA_QL_105）。 */
    String PHA_QL_105 = "PHA_QL_105";

    /** 药房入库单：参数或明细校验失败（AS PHA_IN_001）。 */
    String PHA_IN_001 = "PHA_IN_001";
    /** 药房入库单：不支持手工新建/改单/审批（须由药库出库 agree 生成壳单）（AS PHA_IN_002）。 */
    String PHA_IN_002 = "PHA_IN_002";
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

    /** 科室药房请领单：参数或明细校验失败。 */
    String DEP_QL_001 = "DEP_QL_001";
    /** 科室药房请领单：状态不允许提交、撤回或驳回。 */
    String DEP_QL_003 = "DEP_QL_003";
    /** 科室药房请领单：禁止编辑。 */
    String DEP_QL_004 = "DEP_QL_004";
    /** 科室药房请领单：删除被拒绝（下游出库引用等）。 */
    String DEP_QL_005 = "DEP_QL_005";
    /** 科室药房请领单：机构上下文缺失或无权限。 */
    String DEP_QL_101 = "DEP_QL_101";
    /** 科室药房请领单：列表或选品查询失败。 */
    String DEP_QL_102 = "DEP_QL_102";
    /** 科室药房请领单：单据不存在或类型不匹配。 */
    String DEP_QL_103 = "DEP_QL_103";
    /** 科室药房请领单：重复同意或状态不允许同意。 */
    String DEP_QL_104 = "DEP_QL_104";
    /** 科室药房请领单：并发编辑冲突。 */
    String DEP_QL_105 = "DEP_QL_105";

    /** 科室领用单：参数或明细校验失败（AS DEP_IS_001）。 */
    String DEP_IS_001 = "DEP_IS_001";
    /** 科室领用单：科室药房位置非法、越权或无上级科室（AS DEP_IS_002）。 */
    String DEP_IS_002 = "DEP_IS_002";
    /** 科室领用单：状态不允许提交、撤回或驳回（AS DEP_IS_003）。 */
    String DEP_IS_003 = "DEP_IS_003";
    /** 科室领用单：禁止编辑（AS DEP_IS_004）。 */
    String DEP_IS_004 = "DEP_IS_004";
    /** 科室领用单：删除被拒绝（AS DEP_IS_005）。 */
    String DEP_IS_005 = "DEP_IS_005";
    /** 科室领用单：机构上下文缺失或无权限（AS DEP_IS_101）。 */
    String DEP_IS_101 = "DEP_IS_101";
    /** 科室领用单：列表或选品查询失败（AS DEP_IS_102）。 */
    String DEP_IS_102 = "DEP_IS_102";
    /** 科室领用单：单据不存在或类型不匹配（AS DEP_IS_103）。 */
    String DEP_IS_103 = "DEP_IS_103";
    /** 科室领用单：重复同意或发放/库存编排失败（AS DEP_IS_104）。 */
    String DEP_IS_104 = "DEP_IS_104";
    /** 科室领用单：并发或乐观锁冲突（AS DEP_IS_105）。 */
    String DEP_IS_105 = "DEP_IS_105";

    /** 科室退领单：参数/源单/明细/可退余量校验失败（AS DEP_TL_001）。 */
    String DEP_TL_001 = "DEP_TL_001";
    /** 科室退领单：科室药房越权或非法（AS DEP_TL_002）。 */
    String DEP_TL_002 = "DEP_TL_002";
    /** 科室退领单：状态不允许提交/撤回/驳回（AS DEP_TL_003）。 */
    String DEP_TL_003 = "DEP_TL_003";
    /** 科室退领单：禁止编辑（AS DEP_TL_004）。 */
    String DEP_TL_004 = "DEP_TL_004";
    /** 科室退领单：删除被拒绝（AS DEP_TL_005）。 */
    String DEP_TL_005 = "DEP_TL_005";
    /** 科室退领单：机构上下文缺失或无权限（AS DEP_TL_101）。 */
    String DEP_TL_101 = "DEP_TL_101";
    /** 科室退领单：列表或可关联领用单查询失败（AS DEP_TL_102）。 */
    String DEP_TL_102 = "DEP_TL_102";
    /** 科室退领单：单据不存在或类型不匹配（AS DEP_TL_103）。 */
    String DEP_TL_103 = "DEP_TL_103";
    /** 科室退领单：重复同意或发放/库存编排失败（AS DEP_TL_104）。 */
    String DEP_TL_104 = "DEP_TL_104";
    /** 科室退领单：并发或乐观锁冲突（AS DEP_TL_105）。 */
    String DEP_TL_105 = "DEP_TL_105";

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

    /** 药房退库单：参数或校验失败（AS PHA_RTW_001）。 */
    String PHA_RTW_001 = "PHA_RTW_001";
    /** 药房退库单：来源入库单不可退（AS PHA_RTW_002）。 */
    String PHA_RTW_002 = "PHA_RTW_002";
    /** 药房退库单：状态不允许（AS PHA_RTW_003）。 */
    String PHA_RTW_003 = "PHA_RTW_003";
    /** 药房退库单：禁止编辑（AS PHA_RTW_004）。 */
    String PHA_RTW_004 = "PHA_RTW_004";
    /** 药房退库单：删除被拒绝（AS PHA_RTW_005）。 */
    String PHA_RTW_005 = "PHA_RTW_005";
    /** 药房退库单：机构上下文缺失或无权限（AS PHA_RTW_101）。 */
    String PHA_RTW_101 = "PHA_RTW_101";
    /** 药房退库单：列表或可关联单查询失败（AS PHA_RTW_102）。 */
    String PHA_RTW_102 = "PHA_RTW_102";
    /** 药房退库单：单据不存在或类型不匹配（AS PHA_RTW_103）。 */
    String PHA_RTW_103 = "PHA_RTW_103";
    /** 药房退库单：重复同意或退库编排失败（AS PHA_RTW_104）。 */
    String PHA_RTW_104 = "PHA_RTW_104";
    /** 药房退库单：非法传入乐观锁字段或并发冲突（AS PHA_RTW_105）。 */
    String PHA_RTW_105 = "PHA_RTW_105";
    /** 药房退库单：与退库入库壳单配对（历史兼容，AS PHA_RTW_020）。 */
    String PHA_RTW_020 = "PHA_RTW_020";

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
     * 号源时段错误码：并发占号冲突（乐观锁或末号源竞态）。
     */
    String SLOT_CONFLICT = "SLOT_003";

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
     * 追溯码主档错误码：主档不存在。
     */
    String TRACE_NUMBER_NOT_FOUND = "TRACE_NUMBER_001";
    /**
     * 追溯码主档错误码：不在院或剩余不足（EX-TRC-201）。
     */
    String TRACE_NUMBER_NOT_IN_HOSPITAL = "TRACE_NUMBER_002";
    /**
     * 追溯码主档错误码：单位不可换算（EX-TRC-202）。
     */
    String TRACE_NUMBER_INVALID_UNIT = "TRACE_NUMBER_003";
    /**
     * 追溯码主档错误码：院内调拨未成对（EX-TRC-206）。
     */
    String TRACE_NUMBER_TRANSFER_NOT_PAIRED = "TRACE_NUMBER_004";
    /**
     * 追溯码主档错误码：同 bus_no 重复流水（幂等 Gate）。
     */
    String TRACE_NUMBER_DUPLICATE_BUS_NO = "TRACE_NUMBER_005";
    /**
     * 追溯码主档错误码：参数非法。
     */
    String TRACE_NUMBER_INVALID_ARGUMENT = "TRACE_NUMBER_006";
    /**
     * 追溯码主档错误码：本期仅支持药品追溯。
     */
    String TRACE_NUMBER_MEDICATION_ONLY = "TRACE_NUMBER_007";
    /**
     * 追溯码批量扫码错误码：单次超限（EX-TRC-207）。
     */
    String TRACE_NUMBER_BATCH_LIMIT_EXCEEDED = "TRACE_NUMBER_008";
    /**
     * 追溯码批量扫码错误码：请求内重复（EX-TRC-208 类）。
     */
    String TRACE_NUMBER_DUPLICATE_IN_REQUEST = "TRACE_NUMBER_009";

    /**
     * 追溯码报表错误码：初始化失败（EX-TRC-RPT-201）。
     */
    String TRC_RPT_201 = "TRC_RPT_201";
    /**
     * 追溯码报表错误码：指定库房范围未选库房（EX-TRC-RPT-202）。
     */
    String TRC_RPT_202 = "TRC_RPT_202";
    /**
     * 追溯码报表错误码：列表查询失败（EX-TRC-RPT-203）。
     */
    String TRC_RPT_203 = "TRC_RPT_203";
    /**
     * 追溯码报表错误码：追溯码不存在（EX-TRC-RPT-204）。
     */
    String TRC_RPT_204 = "TRC_RPT_204";
    /**
     * 追溯码报表错误码：导出失败（EX-TRC-RPT-205）。
     */
    String TRC_RPT_205 = "TRC_RPT_205";
    /**
     * 追溯码报表错误码：导出截断（EX-TRC-RPT-206）。
     */
    String TRC_RPT_206 = "TRC_RPT_206";
    /**
     * 追溯码报表错误码：查询参数缺失（EX-TRC-RPT-101）。
     */
    String TRC_RPT_101 = "TRC_RPT_101";

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
     * 财务审批角色缺失：财务审批族供应单或调价单 agree/reject 时当前用户无 {@code ROLE_FINANCE_APPROVER} 系统角色。
     */
    String FINANCE_APPROVAL_ROLE_REQUIRED = "FINANCE_APPROVAL_001";

    /**
     * 财务审批错误码：筛选参数非法、documentKind/type 非法、缺必填等。
     */
    String FINANCE_APPROVAL_INVALID_ARGUMENT = "FINANCE_APPROVAL_002";

    /**
     * 财务审批错误码：单据不存在或非待审态不可审驳。
     */
    String FINANCE_APPROVAL_INVALID_STATUS = "FINANCE_APPROVAL_003";

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
     * 价格调整错误码：该项目已有待审批调价单。
     */
    String PRICE_ADJUSTMENT_ITEM_PENDING = "PRICE_ADJUSTMENT_004";
    /**
     * 价格调整错误码：存在在途医嘱/发药/护士业务，禁止提交调价。
     */
    String PRICE_ADJUSTMENT_ITEM_IN_TRANSIT = "PRICE_ADJUSTMENT_005";
    /**
     * 价格调整错误码：库存锁定冲突（已被其他业务单锁定）。
     */
    String PRICE_ADJUSTMENT_LOCK_CONFLICT = "PRICE_ADJUSTMENT_006";
    /**
     * 价格调整错误码：同一调价单内项目表不一致。
     */
    String PRICE_ADJUSTMENT_ITEM_TABLE_MISMATCH = "PRICE_ADJUSTMENT_007";

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

    /** 临床观察定义（OD）错误码：参数非法。 */
    String OBSERVATION_DEFINITION_INVALID_ARGUMENT = "OBSERVATION_DEFINITION_001";
    /** 临床观察定义（OD）错误码：资源不存在。 */
    String OBSERVATION_DEFINITION_NOT_FOUND = "OBSERVATION_DEFINITION_002";
    /** 临床观察定义（OD）错误码：编码冲突。 */
    String OBSERVATION_DEFINITION_CODE_CONFLICT = "OBSERVATION_DEFINITION_003";
    /** 临床观察定义（OD）错误码：状态不允许。 */
    String OBSERVATION_DEFINITION_INVALID_STATUS = "OBSERVATION_DEFINITION_004";
    /** 临床观察定义（OD）错误码：分类编码非法。 */
    String OBSERVATION_DEFINITION_INVALID_CATEGORY = "OBSERVATION_DEFINITION_005";

    /** 临床观察实例错误码：参数非法。 */
    String OBSERVATION_INVALID_ARGUMENT = "OBSERVATION_001";
    /** 临床观察实例错误码：资源不存在。 */
    String OBSERVATION_NOT_FOUND = "OBSERVATION_002";
    /** 临床观察实例错误码：状态不允许。 */
    String OBSERVATION_INVALID_STATUS = "OBSERVATION_003";
    /** 临床观察实例错误码：观察定义已停用。 */
    String OBSERVATION_DEFINITION_INACTIVE = "OBSERVATION_004";
    /** 临床观察实例错误码：服务申请不属于当前科室。 */
    String OBSERVATION_SR_NOT_IN_DEPT = "OBSERVATION_005";

    /** 临床总检报告错误码：参数非法。 */
    String DIAGNOSTIC_REPORT_INVALID_ARGUMENT = "DIAGNOSTIC_REPORT_001";
    /** 临床总检报告错误码：资源不存在。 */
    String DIAGNOSTIC_REPORT_NOT_FOUND = "DIAGNOSTIC_REPORT_002";
    /** 临床总检报告错误码：状态不允许。 */
    String DIAGNOSTIC_REPORT_INVALID_STATUS = "DIAGNOSTIC_REPORT_003";

    /** 报告读侧索引错误码：参数非法。 */
    String DIAGNOSTIC_REPORT_INDEX_INVALID_ARGUMENT = "DIAGNOSTIC_REPORT_INDEX_001";

    /** 总检诊台：分科未完成（AS-PEIS-MAS-001）。 */
    String DEPT_NOT_COMPLETE = "DEPT_NOT_COMPLETE";
    /** 总检诊台：已总检定稿不可再改。 */
    String MASTER_ALREADY_FINAL = "MASTER_ALREADY_FINAL";

    /** 报告打印/发放：总检报告未定稿（AS-PEIS-REP-001/002）。 */
    String REPORT_NOT_FINAL = "REPORT_NOT_FINAL";

    /** 报告发放错误码：参数非法。 */
    String PEIS_GRANT_INVALID_ARGUMENT = "PEIS_GRANT_001";
    /** 报告发放错误码：记录不存在。 */
    String PEIS_GRANT_NOT_FOUND = "GRANT_NOT_FOUND";
    /** 报告发放错误码：重复登记。 */
    String PEIS_GRANT_ALREADY_REGISTERED = "GRANT_ALREADY_REGISTERED";
    /** 报告发放错误码：状态不可签收/邮寄。 */
    String PEIS_GRANT_NOT_PENDING = "GRANT_NOT_PENDING";
    /** 报告发放错误码：已签收不可再打印。 */
    String PEIS_GRANT_ALREADY_SIGNED = "ALREADY_SIGNED";

    /** 复查管理错误码：参数非法。 */
    String PEIS_RECHECK_INVALID_ARGUMENT = "PEIS_RECHECK_001";
    /** 复查管理错误码：任务不存在。 */
    String PEIS_RECHECK_NOT_FOUND = "PEIS_RECHECK_002";
    /** 复查管理错误码：已有未关闭复查。 */
    String PEIS_RECHECK_ALREADY_OPEN = "RECHECK_ALREADY_OPEN";
    /** 复查管理错误码：状态不允许。 */
    String PEIS_RECHECK_INVALID_STATUS = "PEIS_RECHECK_003";
    /** 复查管理错误码：原检未完成/无定稿报告。 */
    String PEIS_RECHECK_ENCOUNTER_NOT_FINISHED = "ENCOUNTER_NOT_FINISHED";

    /** 项目↔小项绑定（AOL）错误码：参数非法。 */
    String ACTIVITY_OBSERVATION_LINK_INVALID_ARGUMENT = "ACTIVITY_OBSERVATION_LINK_001";
    /** 项目↔小项绑定（AOL）错误码：项目 AD 不存在或不可绑定。 */
    String ACTIVITY_OBSERVATION_LINK_ACTIVITY_NOT_FOUND = "ACTIVITY_OBSERVATION_LINK_002";
    /** 项目↔小项绑定（AOL）错误码：小项 OD 不存在。 */
    String ACTIVITY_OBSERVATION_LINK_OD_NOT_FOUND = "ACTIVITY_OBSERVATION_LINK_003";
    /** 项目↔小项绑定（AOL）错误码：入参小项重复。 */
    String ACTIVITY_OBSERVATION_LINK_DUPLICATE_OD = "ACTIVITY_OBSERVATION_LINK_004";

    /** PEIS 模块参数错误码：参数非法。 */
    String PEIS_CONFIG_INVALID_ARGUMENT = "PEIS_CONFIG_001";
    /** PEIS 模块参数错误码：配置键前缀禁止占用。 */
    String PEIS_CONFIG_KEY_FORBIDDEN = "PEIS_CONFIG_002";
    /** PEIS 模块参数错误码：配置键未知或不在白名单。 */
    String PEIS_CONFIG_KEY_UNKNOWN = "PEIS_CONFIG_003";
    /** PEIS 模块参数错误码：资源不存在。 */
    String PEIS_CONFIG_NOT_FOUND = "PEIS_CONFIG_004";

    /** PEIS 常见结果错误码：参数非法。 */
    String PEIS_COMMON_RESULT_INVALID_ARGUMENT = "PEIS_COMMON_RESULT_001";
    /** PEIS 常见结果错误码：资源不存在。 */
    String PEIS_COMMON_RESULT_NOT_FOUND = "PEIS_COMMON_RESULT_002";
    /** PEIS 常见结果错误码：名称冲突。 */
    String PEIS_COMMON_RESULT_DISPLAY_CONFLICT = "PEIS_COMMON_RESULT_003";

    /** PEIS 服务申请执行扩展错误码：参数非法。 */
    String PEIS_SR_EXT_INVALID_ARGUMENT = "PEIS_SR_EXT_001";
    /** PEIS 服务申请执行扩展错误码：资源不存在。 */
    String PEIS_SR_EXT_NOT_FOUND = "PEIS_SR_EXT_002";

    /** PEIS 就诊扩展错误码：参数非法。 */
    String PEIS_ENC_EXT_INVALID_ARGUMENT = "PEIS_ENC_EXT_001";
    /** PEIS 就诊扩展错误码：资源不存在。 */
    String PEIS_ENC_EXT_NOT_FOUND = "PEIS_ENC_EXT_002";
    /** PEIS 就诊扩展错误码：已存在。 */
    String PEIS_ENC_EXT_ALREADY_EXISTS = "PEIS_ENC_EXT_003";

    /** PEIS 登记错误码：套餐未启用。 */
    String PEIS_REG_PACKAGE_INACTIVE = "PEIS_REG_001";
    /** PEIS 登记错误码：患者不存在。 */
    String PEIS_REG_PATIENT_NOT_FOUND = "PEIS_REG_002";
    /** PEIS 登记错误码：就诊已在检。 */
    String PEIS_REG_ENCOUNTER_ALREADY_ACTIVE = "PEIS_REG_003";
    /** PEIS 登记错误码：参数非法。 */
    String PEIS_REG_INVALID_ARGUMENT = "PEIS_REG_004";
    /** PEIS 登记错误码：项目已执行不可减项。 */
    String PEIS_REG_SR_ALREADY_EXECUTED = "PEIS_REG_005";
    /** PEIS 登记错误码：就诊状态不允许调整项目。 */
    String PEIS_REG_ENCOUNTER_STATUS_INVALID = "PEIS_REG_006";

    /** 手术定义错误码：参数非法。 */
    String PROCEDURE_DEFINITION_INVALID_ARGUMENT = "PROCEDURE_DEFINITION_001";
    /** 手术定义错误码：资源不存在。 */
    String PROCEDURE_DEFINITION_NOT_FOUND = "PROCEDURE_DEFINITION_002";
    /** 手术定义错误码：编码冲突。 */
    String PROCEDURE_DEFINITION_CODE_CONFLICT = "PROCEDURE_DEFINITION_003";

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
     * 就诊诊断绑定：{@code encounter_diagnosis_ids} 非法（非本就诊、软删、格式错误等）。
     */
    String ENCOUNTER_DIAGNOSIS_BINDING_INVALID = "ENC_DX_BIND_001";

    /**
     * 就诊诊断绑定：普通族与慢病族混绑。
     */
    String ENCOUNTER_DIAGNOSIS_BINDING_FAMILY_MIXED = "ENC_DX_BIND_002";

    /**
     * 就诊诊断绑定：主诊未入选或未置于 ids 串首位。
     */
    String ENCOUNTER_DIAGNOSIS_BINDING_PRIMARY_REQUIRED = "ENC_DX_BIND_003";

    /**
     * 就诊诊断绑定：已被医嘱/账单引用，禁止改内容或删除。
     */
    String ENCOUNTER_DIAGNOSIS_BINDING_REFERENCED = "ENC_DX_BIND_004";

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
