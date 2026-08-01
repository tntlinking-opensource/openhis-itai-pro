/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 医生站医嘱开立应用层常量（门诊 AS-OP-DOR-003 / 住院 AS-IP-DOR-003 共用，与 AS-SDD §2.3 对齐）。
 */
public interface DoctorStationAdviceConstants {

    /** 暂存批次行操作：新建。 */
    String LINE_OP_CREATE = "CREATE";

    /** 暂存批次行操作：更新。 */
    String LINE_OP_UPDATE = "UPDATE";

    /** 暂存批次行操作：删除。 */
    String LINE_OP_DELETE = "DELETE";

    /** 成功响应占位文案（与网关/前端约定无关的技术性 ACK）。 */
    String RESPONSE_MESSAGE_OK = "OK";

    /**
     * 滴速展示用术语编码体系名（与 {@code med_medication_request.speed_code} 对齐）。
     *
     * <p>若租户未种子化该 code_system，列表装配将得到空映射，滴速展示名为空——属数据侧缺口而非装配器静默写死文案。</p>
     */
    String TERMINOLOGY_SPEED_CODE_SYSTEM_NAME = "speed_code";

    /**
     * IF-DOR-10 医嘱统一列表：单价/总价/推算金额在 API 中的小数位数（与库存 decimal 元一致，仅做标度展示）。
     */
    int ADVICE_LIST_CHARGE_DISPLAY_DECIMAL_SCALE = 6;

    /** IF-DOR-12 / IF-IP-DOR-12：用法来源绑定（与 {@link com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode#USAGE} 同码；目标可为诊疗或耗材）。 */
    String BIND_TYPE_USAGE = "USAGE";

    /** IF-DOR-12 / IF-IP-DOR-12：诊疗来源绑定（与 {@link com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode#ACTIVITY} 同码；目标可为诊疗或耗材）。 */
    String BIND_TYPE_ACTIVITY = "ACTIVITY";

    /**
     * IF-DOR-12 / IF-IP-DOR-12：单次绑定查询返回行数上限（B.2 规模约束，禁止无界拉平）。
     */
    int ORDER_BIND_LIST_MAX_ROWS = 200;

    /** IF-DOR/IF-IP-DOR-10：列表窄列摘要——处方号前缀（后接 {@code prescriptionNo}）。 */
    String ADVICE_LIST_PRESCRIPTION_LABEL_PREFIX = "处方 ";

    /** IF-DOR-10：门诊列表窄列——药品行无展示名时的回落文案。 */
    String ADVICE_LIST_MEDICATION_NARROW_FALLBACK_OUTPATIENT = "药品申请";

    /** IF-DOR-10：门诊列表窄列——诊疗行无展示名时的回落文案。 */
    String ADVICE_LIST_SERVICE_NARROW_FALLBACK_OUTPATIENT = "诊疗申请";

    /** IF-DOR-10：门诊列表窄列——耗材行无展示名时的回落文案。 */
    String ADVICE_LIST_DEVICE_NARROW_FALLBACK_OUTPATIENT = "耗材申请";

    /** IF-IP-DOR-10：住院列表窄列——药品行无展示名时的回落文案。 */
    String ADVICE_LIST_MEDICATION_NARROW_FALLBACK_INPATIENT = "药品医嘱";

    /** IF-IP-DOR-10：住院列表窄列——诊疗行无展示名时的回落文案。 */
    String ADVICE_LIST_SERVICE_NARROW_FALLBACK_INPATIENT = "诊疗医嘱";

    /** IF-IP-DOR-10 / IF-IP-DOR-21：出院医嘱列表主展示名。 */
    String ADVICE_LIST_DISCHARGE_DISPLAY_NAME_INPATIENT = "出院医嘱";

    /** IF-IP-DOR-10：转科医嘱列表主展示名。 */
    String ADVICE_LIST_TRANSFER_DISPLAY_NAME_INPATIENT = "转科医嘱";

    /** 出院方式术语编码体系（与 {@code ter_code_system.name=out_way_code} 对齐）。 */
    String TERMINOLOGY_OUT_WAY_CODE_SYSTEM_NAME = "out_way_code";

    /** 患者/就诊门控：须先选中有效就诊。 */
    String MSG_SELECT_PATIENT_FIRST = "请先选择患者";

    /** 患者/就诊门控：请求患者 id 与就诊读模型不一致。 */
    String MSG_PATIENT_ENCOUNTER_MISMATCH = "患者标识与就诊读模型不一致";

    /** 患者/就诊门控：当前就诊状态不允许变更医嘱。 */
    String MSG_ENCOUNTER_NOT_EDITABLE_FOR_ADVICE = "当前患者不可变更医嘱";

    /** 请求校验：就诊标识缺失。 */
    String MSG_ENCOUNTER_ID_REQUIRED = "就诊标识不能为空";

    /** 请求校验：患者标识缺失。 */
    String MSG_PATIENT_ID_REQUIRED = "患者标识不能为空";

/** DoctorStationAdvice 共享常量：operator not resolved，值 {@code 未获取到当前操作人}。 */
    String MSG_OPERATOR_NOT_RESOLVED = "未获取到当前操作人";

    /** 暂存：就诊未绑定自费账户，无法生成费用草稿。 */
    String MSG_SELF_PAY_ACCOUNT_REQUIRED_FOR_DRAFT_CHARGE = "就诊未解析到自费账户，无法生成费用草稿";

    /** 签发：就诊未绑定自费账户，无法完成费用编排。 */
    String MSG_SELF_PAY_ACCOUNT_REQUIRED_FOR_SIGN_CHARGE = "就诊未解析到自费账户，无法完成签发费用编排";

    /** 暂存行操作码不在 CREATE/UPDATE/DELETE 白名单内（后缀拼接 {@code lineOp}）。 */
    String MSG_UNSUPPORTED_LINE_OP_PREFIX = "不支持的行操作: ";

    /** 产品表名不在住院/门诊医嘱开立白名单（后缀拼接 {@code productTableName}）。 */
    String MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX = "不支持的产品定义表名: ";

    /** 停嘱批次请求体为空。 */
    String MSG_STOP_LONG_TERM_LINES_REQUIRED = "停嘱行不能为空";

    /** 出院/转科：存在未完成申请时禁止重复开立。 */
    String MSG_DUPLICATE_TRANSFER_OR_DISCHARGE_REQUEST = "同一就诊存在未完成的转科/出院申请，禁止重复开立";

    /** 诊疗草稿：载荷或活动定义主键缺失。 */
    String MSG_SERVICE_PAYLOAD_OR_ACTIVITY_ID_REQUIRED = "诊疗载荷或活动定义主键不能为空";

    /** 暂存 UPDATE：requestId 缺失。 */
    String MSG_UPDATE_LINE_REQUEST_ID_REQUIRED = "UPDATE 操作 requestId 不能为空";

    /** 暂存 DELETE：requestId 缺失。 */
    String MSG_DELETE_LINE_REQUEST_ID_REQUIRED = "DELETE 操作 requestId 不能为空";

    /** 停嘱/签退：诊疗申请不存在。 */
    String MSG_SERVICE_REQUEST_NOT_FOUND = "诊疗申请不存在";

    /** 停嘱：非长期药品医嘱。 */
    String MSG_STOP_LONG_TERM_MEDICATION_ONLY = "仅允许对长期药品医嘱执行停嘱";

    /** 停嘱：非长期诊疗医嘱。 */
    String MSG_STOP_LONG_TERM_SERVICE_ONLY = "仅允许对长期诊疗医嘱执行停嘱";

    /** 取消停嘱批次请求体为空。 */
    String MSG_CANCEL_STOP_LONG_TERM_LINES_REQUIRED = "取消停嘱行不能为空";

    /** 取消停嘱：非长期药品医嘱。 */
    String MSG_CANCEL_STOP_LONG_TERM_MEDICATION_ONLY = "仅允许对长期药品医嘱取消停嘱";

    /** 取消停嘱：非长期诊疗医嘱。 */
    String MSG_CANCEL_STOP_LONG_TERM_SERVICE_ONLY = "仅允许对长期诊疗医嘱取消停嘱";

    /** 取消停嘱：行状态非已停嘱。 */
    String MSG_CANCEL_STOP_NOT_STOPPED = "仅已停嘱的长期医嘱可取消停嘱";

    /** 取消停嘱：出院/转科门禁拦截。 */
    String MSG_CANCEL_STOP_DISCHARGE_GATE = "存在出院或转科申请，无法取消停嘱";

    /** 改业务时间：请求主键缺失。 */
    String MSG_CHANGE_BUSINESS_TIME_REQUEST_ID_REQUIRED = "改业务时间时 requestId 不能为空";

    /** 改业务时间：产品表名缺失。 */
    String MSG_CHANGE_BUSINESS_TIME_PRODUCT_TABLE_REQUIRED = "改业务时间时 productTableName 不能为空";

    /** 改业务时间：时间种类缺失。 */
    String MSG_CHANGE_BUSINESS_TIME_KIND_REQUIRED = "改业务时间时 timeKind 不能为空";

    /** 改业务时间：新时间为空。 */
    String MSG_CHANGE_BUSINESS_TIME_NEW_TIME_REQUIRED = "改业务时间时 newTime 不能为空";

    /** 改业务时间：时间种类非法（仅 OPENED/STOP）。 */
    String MSG_CHANGE_BUSINESS_TIME_KIND_INVALID = "时间种类仅允许 OPENED 或 STOP，禁止修改签发时间";

    /** 改停嘱时间：须长期且已停嘱。 */
    String MSG_CHANGE_STOP_TIME_NOT_STOPPED = "仅已停嘱的长期医嘱可修改停嘱时间";

    /** 药品草稿载荷缺失。 */
    String MSG_MEDICATION_DRAFT_PAYLOAD_REQUIRED = "药品草稿载荷不能为空";

    /** 药品 UPDATE：requestId 缺失。 */
    String MSG_MEDICATION_UPDATE_REQUEST_ID_REQUIRED = "药品医嘱更新时 requestId 不能为空";

    /** 诊疗草稿载荷缺失。 */
    String MSG_SERVICE_DRAFT_PAYLOAD_REQUIRED = "诊疗草稿载荷不能为空";

    /** 诊疗活动产品读模型缺失。 */
    String MSG_SERVICE_ACTIVITY_READ_MODEL_REQUIRED = "诊疗活动读模型不能为空";

    /** 诊疗 UPDATE：requestId 缺失。 */
    String MSG_SERVICE_UPDATE_REQUEST_ID_REQUIRED = "诊疗医嘱更新时 requestId 不能为空";

    /** 定价行缺失（Mapper/费用草稿前置校验）。 */
    String MSG_CHARGE_PRICE_LINE_REQUIRED = "定价行不能为空";

    /** 诊疗开立数量缺失或非法。 */
    String MSG_SERVICE_QUANTITY_REQUIRED = "诊疗开立数量必须大于 0";

    /** 诊疗开立单位缺失。 */
    String MSG_SERVICE_UNIT_CODE_REQUIRED = "诊疗开立单位不能为空";

    /** 临嘱诊疗计划执行次数缺失或非法。 */
    String MSG_SERVICE_EXECUTION_COUNT_REQUIRED = "临嘱诊疗计划执行次数必须大于 0";

    /** 费用草稿装配缺少产品定义读模型。 */
    String MSG_CHARGE_PRODUCT_DEFINITION_REQUIRED = "产品定义读模型不能为空，无法按目录单位槽计价";

    /** 费用草稿装配缺少计价数量。 */
    String MSG_CHARGE_QUANTITY_REQUIRED = "计价数量必须大于 0";

    /** 费用草稿装配缺少计价单位。 */
    String MSG_CHARGE_UNIT_CODE_REQUIRED = "计价单位不能为空";

    /** 包装价型换算缺少包装单位（主数据 {@code package_unit_code} 未维护）。 */
    String MSG_CHARGE_PACKAGE_UNIT_CODE_REQUIRED = "包装单位未维护，无法按定价行换算金额";

    /** 药品/饮片开立单位缺失。 */
    String MSG_MEDICATION_UNIT_CODE_REQUIRED = "药品开立单位不能为空";

    /** 长临医嘱类型 code 缺失。 */
    String MSG_THERAPY_TYPE_REQUIRED = "长临医嘱类型不能为空";

    /** 长期诊疗草稿/签发时未传频次 code。 */
    String MSG_LONG_TERM_SERVICE_RATE_CODE_REQUIRED = "长期诊疗医嘱须选择频次";

    /** 长临医嘱类型 code 非法（后缀拼接非法 code）。 */
    String MSG_INVALID_THERAPY_TYPE_PREFIX = "无效的长临医嘱类型: ";

    /** 绑定查询：主数据主键缺失。 */
    String MSG_BIND_SOURCE_ITEM_ID_REQUIRED = "主数据主键不能为空";

    /** 绑定查询：主数据项号无法解析。 */
    String MSG_BIND_SOURCE_ITEM_NO_NOT_NUMERIC = "主数据项号无法解析为数值主键";

    /** 绑定查询：用法绑定须传给药途径术语编码（{@code method_code}）。 */
    String MSG_BIND_SOURCE_METHOD_CODE_REQUIRED = "用法绑定须传给药途径术语编码（method_code）";

    /** 开立定价缺失（通用，非批号口径）。 */
    String MSG_CHARGE_DEFINITION_MISSING_FOR_SAVE = "未配置符合开立规则的收费定价，请先维护收费项定价后再保存医嘱。";

    /** FIFO 分段条数与数量列表不一致。 */
    String MSG_DISPENSE_SEGMENT_CHARGE_COUNT_MISMATCH = "发放分段与费用草稿数量不一致";

    /** 药品开立定价缺失（须 LOT_NUMBER_PRICE 批号售价）。 */
    String MSG_CHARGE_DEFINITION_MISSING_LOT_NUMBER_PRICE = "未配置符合开立规则的收费定价（药品须批号售价 LOT_NUMBER_PRICE）。";

    /** 诊疗活动对应产品定义读模型未找到。 */
    String MSG_ACTIVITY_PRODUCT_DEFINITION_NOT_FOUND = "未找到诊疗活动产品定义行";

    /** 签发 FIFO：缺少 orgId。 */
    String MSG_SIGN_FIFO_MISSING_ORG_ID = "签发 FIFO 分配缺少 orgId";

    /** 签发 FIFO：缺少 encounterId 或 medicationDefinitionId。 */
    String MSG_SIGN_FIFO_MISSING_ENCOUNTER_OR_MED_DEF = "签发 FIFO 分配缺少 encounterId/medicationDefinitionId";

    /** 签发 FIFO：分配数量须大于 0。 */
    String MSG_SIGN_FIFO_QUANTITY_MUST_BE_POSITIVE = "签发 FIFO 分配数量必须大于 0";

    /** 签发 FIFO：无候选发放库位。 */
    String MSG_SIGN_FIFO_NO_CANDIDATE_LOCATION = "未解析到候选发放库位，无法按 FIFO 拆分发药";

    /**
     * 签发 FIFO：库存不足以覆盖医嘱数量（{@code %s} 为药品展示名）。
     */
    String MSG_SIGN_FIFO_INSUFFICIENT_STOCK_FOR_ADVICE = "【%s】候选库位可用库存不足以覆盖医嘱数量，拒绝签发";

    /**
     * 签发 FIFO：耗材库存不足以覆盖医嘱数量（{@code %s} 为耗材展示名）。
     */
    String MSG_SIGN_FIFO_INSUFFICIENT_STOCK_FOR_DEVICE = "【%s】候选库位可用库存不足以覆盖耗材医嘱数量，拒绝签发";

    /**
     * 药品/耗材医嘱草稿单位码缺省占位（与门诊 Mapper 口径一致，待主数据补齐后由域层校验）。
     */
    String DEFAULT_MEDICATION_REQUEST_UNIT_CODE_PLACEHOLDER = "U";

    /** Bean Validation：绑定类型必填。 */
    String VALIDATION_BIND_TYPE_REQUIRED = "绑定类型不能为空";

    /** Bean Validation：绑定查询主数据项号必填。 */
    String VALIDATION_BIND_SOURCE_ITEM_NO_REQUIRED = "主数据项号不能为空";

    /** Bean Validation：请求主键必填。 */
    String VALIDATION_REQUEST_ID_REQUIRED = "请求主键不能为空";

    /** Bean Validation：产品定义表名必填。 */
    String VALIDATION_PRODUCT_TABLE_NAME_REQUIRED = "产品定义表名不能为空";

    /** Bean Validation：暂存行操作码必填。 */
    String VALIDATION_LINE_OP_REQUIRED = "行操作不能为空";

    /** Bean Validation：长临医嘱类型必填。 */
    String VALIDATION_THERAPY_TYPE_REQUIRED = "长临医嘱类型不能为空";

    /** Bean Validation：并组组号必填。 */
    String VALIDATION_GROUP_ID_REQUIRED = "组号不能为空";

    /** Bean Validation：并组药品请求列表非空。 */
    String VALIDATION_MEDICATION_REQUEST_IDS_REQUIRED = "药品请求列表不能为空";

    /** Bean Validation：签发批次目标列表非空。 */
    String VALIDATION_SIGN_TARGETS_REQUIRED = "签发目标不能为空";

    /** Bean Validation：签退批次目标列表非空。 */
    String VALIDATION_WITHDRAW_TARGETS_REQUIRED = "签退目标不能为空";

    /** Bean Validation：暂存批次行列表非空。 */
    String VALIDATION_DRAFT_LINES_REQUIRED = "批次行不能为空";

    /** Bean Validation：停嘱批次行列表非空。 */
    String VALIDATION_STOP_LINES_REQUIRED = "停嘱行不能为空";

    /** Bean Validation：取消停嘱批次行列表非空。 */
    String VALIDATION_CANCEL_STOP_LINES_REQUIRED = "取消停嘱行不能为空";

    /** Bean Validation：出院方式必填。 */
    String VALIDATION_DISCHARGE_MODE_REQUIRED = "出院方式不能为空";

    /** EX-IP-ORD-103：签退存在已执行/已发药/待结算或已结算费用（IF-IP-DOR-16）。 */
    String EX_IP_ORD_103 = "EX-IP-ORD-103";

    /** EX-IP-ORD-103 用户提示：已发生执行/发药/费用事实时禁止签退。 */
    String MSG_INPATIENT_WITHDRAW_BLOCKED = "已执行、已发药或费用不可回退，无法签退";

    /** EX-IP-ORD-106：护理级别医嘱长临/并发门控（IF-IP-DOR-14/15 · BR-IP-ORD-026）。 */
    String EX_IP_ORD_106 = "EX-IP-ORD-106";

    /** EX-IP-ORD-107：就诊待结算禁止开立/变更医嘱（IF-IP-DOR-03 可写 Gate）。 */
    String EX_IP_ORD_107 = "EX-IP-ORD-107";

    /** EX-IP-ORD-306：嘱托医嘱正文空/空白（IF-IP-DOR-14 · BR-IP-ORD-028）。 */
    String EX_IP_ORD_306 = "EX-IP-ORD-306";

    /** 护理级别（category 26）仅允许开立长期医嘱。 */
    String MSG_NURSING_LEVEL_LONG_TERM_ONLY = "护理级别医嘱仅允许开立长期医嘱";

    /** 同就诊已存在有效护理级别医嘱，须先停嘱再开新级别。 */
    String MSG_NURSING_LEVEL_STOP_BEFORE_NEW = "已存在有效护理级别医嘱，请先停嘱后再开立新的护理级别";

    /** 嘱托行须填写自由文本正文（entrustContent）。 */
    String MSG_ENTRUST_CONTENT_REQUIRED = "请填写嘱托内容";

    /** EX-ORD-308：门诊禁开嘱托医嘱（IF-DOR-11/14 · BR-ORD-032）。 */
    String EX_ORD_308 = "EX-ORD-308";

    /** 门诊不可开立嘱托（BR-ORD-032 / BR-IP-ORD-028②）。 */
    String MSG_OUTPATIENT_ENTRUST_FORBIDDEN = "门诊不可开立嘱托医嘱";

    /** 出院带药 therapy 仅允许药品行，禁止诊疗/嘱托/申请单（BR-IP-ORD-029）。 */
    String MSG_DISCHARGE_MEDICATION_MEDICATION_ONLY = "出院带药仅允许开立药品医嘱";

    /** Bean Validation：产品定义主键必填（目录预选键）。 */
    String VALIDATION_PRODUCT_DEFINITION_ID_REQUIRED = "产品定义主键不能为空";

    /** IF-IP-DOR-27：追加皮试检查时药品主键列表为空。 */
    String MSG_APPEND_SKIN_TEST_MED_IDS_REQUIRED = "追加皮试检查时药品主键列表不能为空";

    /** IF-IP-DOR-27：仅长期药品可追加皮试检查。 */
    String MSG_APPEND_SKIN_TEST_LONG_TERM_ONLY = "仅长期皮试药可追加皮试检查";

    /** IF-IP-DOR-27：药品未勾选皮试。 */
    String MSG_APPEND_SKIN_TEST_FLAG_REQUIRED = "仅已勾选皮试的药品可追加皮试检查";

    /** IF-IP-DOR-27：已停嘱药品不可追加。 */
    String MSG_APPEND_SKIN_TEST_STOPPED_FORBIDDEN = "已停嘱药品不可追加皮试检查";

    /** IF-IP-DOR-27：药品不属于当前就诊。 */
    String MSG_APPEND_SKIN_TEST_ENCOUNTER_MISMATCH = "皮试药不属于当前就诊，无法追加皮试检查";
}
