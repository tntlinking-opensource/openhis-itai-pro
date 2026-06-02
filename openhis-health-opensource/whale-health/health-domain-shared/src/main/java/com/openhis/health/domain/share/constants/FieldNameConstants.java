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
 * 契约/持久化属性路径名常量（JPA/Sort/动态查询等）。值与实体或契约 JSON 字段名一致。
 *
 * <p>JavaDoc 描述字段业务含义；常量名采用 UPPER_SNAKE，值为 camelCase 属性名。</p>
 */
public interface FieldNameConstants {
    /**
     * 在院或有效活跃标识。
     */
    String ACTIVE_FLAG = "activeFlag";
    /**
     * 联系地址。
     */
    String ADDRESS = "address";
    /**
     * 入院时刻。
     */
    String ADMISSION_TIME = "admissionTime";
    /**
     * 变动后数量。
     */
    String AFTER_QUANTITY = "afterQuantity";
    /**
     * 金额。
     */
    String AMOUNT = "amount";
    /**
     * 抗生素分级/管理相关标识。
     */
    String ANTIBIOTIC_FLAG = "antibioticFlag";
    /**
     * 申请人标识。
     */
    String APPLICANT_ID = "applicantId";
    /**
     * 申请时间。
     */
    String APPLY_TIME = "applyTime";
    /**
     * 审批通过时间。
     */
    String APPROVAL_TIME = "approvalTime";
    /**
     * 审批人标识。
     */
    String APPROVER_ID = "approverId";
    /**
     * 基本目录/基本药物标识。
     */
    String BASIC_FLAG = "basicFlag";
    /**
     * 床位标识。
     */
    String BED_ID = "bedId";
    /**
     * 床位名称。
     */
    String BED_NAME = "bedName";
    /**
     * 变动前数量。
     */
    String BEFORE_QUANTITY = "beforeQuantity";
    /**
     * 账单或计费日期。
     */
    String BILL_DATE = "billDate";
    /**
     * 出生日期。
     */
    String BIRTH_DATE = "birthDate";
    /**
     * 身体部位编码。
     */
    String BODY_SITE_CODE = "bodySiteCode";
    /**
     * 业务流水号。
     */
    String BUS_NO = "busNo";
    /**
     * 取消时刻。
     */
    String CANCELLED_AT = "cancelledAt";
    /**
     * 取消时刻（与 {@link #CANCELLED_AT} 同义异键）。
     */
    String CANCELLED_TIME = "cancelledTime";
    /**
     * 分类编码。
     */
    String CATEGORY_CODE = "categoryCode";
    /**
     * 分类字典展示文案。
     */
    String CATEGORY_CODE_DICT_TEXT = "categoryCodeDictText";
    /**
     * 分类枚举码。
     */
    String CATEGORY_ENUM = "categoryEnum";
    /**
     * 变动数量。
     */
    String CHANGE_QUANTITY = "changeQuantity";
    /**
     * 含子项/组合项标识。
     */
    String CHILDREN_FLAG = "childrenFlag";
    /**
     * 子节点或子项 JSON。
     */
    String CHILDREN_JSON = "childrenJson";
    /**
     * 医保收费项目等级（chrgitm）。
     */
    String CHRGITM_LV = "chrgitmLv";
    /**
     * 报账/理赔状态。
     */
    String CLAIM_STATE_ENUM = "claimStateEnum";
    /**
     * 就诊类别或班次等枚举。
     */
    String CLASS_ENUM = "classEnum";
    /**
     * 终末消毒关联床位。
     */
    String CLEANED_BED_ID = "cleanedBedId";
    /**
     * 终末消毒关联病区。
     */
    String CLEANED_WARD_ID = "cleanedWardId";
    /**
     * 诊断或病情编码。
     */
    String CONDITION_CODE = "conditionCode";
    /**
     * 诊断事实主键（医生站快照 JSON、就诊诊断关联）。
     */
    String CONDITION_ID = "conditionId";
    /**
     * 医嘱请求（药品/诊疗/耗材）：诊断定义主键（持久化列 {@code condition_definition_id}，指向 {@code cln_condition_definition.id}）。
     */
    String CONDITION_DEFINITION_ID = "conditionDefinitionId";
    /**
     * 诊断定义标准名称（医生站快照 JSON 中与 {@link #CONDITION_DEFINITION_ID} 成对；就诊诊断关联事件主诊断摘要等同名载荷）。
     */
    String CONDITION_DEFINITION_NAME = "conditionDefinitionName";
    /**
     * 业务上下文枚举。
     */
    String CONTEXT_ENUM = "contextEnum";
    /**
     * 合同编号。
     */
    String CONTRACT_NO = "contractNo";
    /**
     * 创建时间。
     */
    String CREATED_AT = "createdAt";
    /**
     * 创建时间（部分读模型或接口使用该字段名）。
     */
    String CREATE_TIME = "createTime";
    /**
     * 配送单标识。
     */
    String DELIVERY_ID = "deliveryId";
    /**
     * 配送批号。
     */
    String DELIVERY_LOT_NUMBER = "deliveryLotNumber";
    /**
     * 发放状态枚举（库房单据明细读模型等）。
     */
    String DELIVERY_STATUS_ENUM = "deliveryStatusEnum";
    /**
     * 说明或描述正文。
     */
    String DESCRIPTION_TEXT = "descriptionText";
    /**
     * 描述/诊断描述等泛用文本（如 {@code adm_encounter_diagnosis.description}）。
     */
    String DESCRIPTION = "description";
    /**
     * 目标位置标识。
     */
    String DESTINATION_LOCATION_ID = "destinationLocationId";
    /**
     * 目标位置名称。
     */
    String DESTINATION_LOCATION_NAME = "destinationLocationName";
    /**
     * 诊断明细 JSON。
     */
    String DIAGNOSIS_JSON = "diagnosisJson";
    /**
     * 医生站诊断快照：中西医维度标签（{@code WESTERN}/{@code TCM}）。
     */
    String DIAGNOSIS_KIND = "diagnosisKind";
    /**
     * 医生站诊断快照：中西医维度中文展示（冗余列，可由 {@link #DIAGNOSIS_KIND} 推导）。
     */
    String DIAGNOSIS_KIND_TEXT = "diagnosisKindText";
    /**
     * 医生站诊断快照：诊断补充说明（可回落到病情事实描述）。
     */
    String DIAGNOSIS_DESC = "diagnosisDesc";
    /**
     * 医生站诊断快照：列表排序号（与就诊诊断关联 {@code rank} 对齐）。
     */
    String DIAGNOSIS_SORT_NO = "diagnosisSortNo";
    /**
     * 就诊诊断关联主键（快照元素级锚点）。
     */
    String ENCOUNTER_DIAGNOSIS_ID = "encounterDiagnosisId";
    /**
     * 医生站诊断快照：在院患者疾病诊断类型编码（与 {@code IptPatientDiseaseDiagType} 对齐）。
     */
    String IPT_DISEASE_TYPE_CODE = "iptDiseaseTypeCode";
    /**
     * 医生站诊断快照：是否主诊（1/0 与历史 JSON 契约一致）。
     */
    String MAIN_DIAGNOSIS_FLAG = "mainDiagnosisFlag";
    /**
     * 医生站诊断快照：医疗类别/医保类别编码（就诊诊断关联 {@code medType}）。
     */
    String MEDICAL_TYPE_CODE = "medicalTypeCode";
    /**
     * 医生站诊断快照：验证状态 UI 整数码（入库前经域服务映射为 FHIR 码）。
     */
    String VERIFICATION_STATUS_ENUM = "verificationStatusEnum";
    /**
     * 医生站诊断快照：验证状态展示文案（可选冗余列）。
     */
    String VERIFICATION_STATUS_ENUM_TEXT = "verificationStatusEnumText";
    /**
     * 出院或离院时刻。
     */
    String DISCHARGE_TIME = "dischargeTime";
    /**
     * 离院方式枚举。
     */
    String DISCHARGE_TYPE_ENUM = "dischargeTypeEnum";
    /**
     * 列表展示排序号。
     */
    String DISPLAY_ORDER = "displayOrder";
    /**
     * 医师标识。
     */
    String DOCTOR_ID = "doctorId";
    /**
     * 医师姓名。
     */
    String DOCTOR_NAME = "doctorName";
    /**
     * 用药说明条目。
     */
    String DOSAGE_INSTRUCTION = "dosageInstruction";
    /**
     * 剂量来源口径。
     */
    String DOSE_FROM = "doseFrom";
    /**
     * 剂量单位编码。
     */
    String DOSE_UNIT_CODE = "doseUnitCode";
    /**
     * 剂量单位换算比。
     */
    String DOSE_UNIT_RATIO = "doseUnitRatio";
    /**
     * 剂型编码（药品定义 {@code dose_form_code} 等）。
     */
    String DOSE_FORM_CODE = "doseFormCode";
    /**
     * 单次剂量（药品申请 {@code dose}）。
     */
    String DOSE = "dose";
    /**
     * 用药天数（药品申请 {@code dispensePerDuration}）。
     */
    String DISPENSE_PER_DURATION = "dispensePerDuration";
    /**
     * 药品发放类型枚举（{@code MedicationDispense.dispenseEnum}）。
     */
    String DISPENSE_ENUM = "dispenseEnum";
    /**
     * 药品成组号（药品申请 {@code groupId}）。
     */
    String GROUP_ID = "groupId";
    /**
     * 就诊业务流水号。
     */
    String ENCOUNTER_BUS_NO = "encounterBusNo";
    /**
     * 就诊标识。
     */
    String ENCOUNTER_ID = "encounterId";
    /**
     * 结束时刻。
     */
    String END_TIME = "endTime";
    /**
     * 录入人标识。
     */
    String ENTERER_ID = "entererId";
    /**
     * 嘱托或委托内容。
     */
    String ENTRUST_CONTENT = "entrustContent";
    /**
     * 效期或到期日。
     */
    String EXPIRATION_DATE = "expirationDate";
    /**
     * 首次剂量。
     */
    String FIRST_DOSE = "firstDose";
    /**
     * 首次剂量单位编码。
     */
    String FIRST_DOSE_UNIT_CODE = "firstDoseUnitCode";
    /**
     * 性别编码。
     */
    String GENDER_ENUM = "genderEnum";
    /**
     * 性别展示文案。
     */
    String GENDER_ENUM_TEXT = "genderEnumText";
    /**
     * 基本药物目录（HVCM）标识。
     */
    String HVCM_FLAG = "hvcmFlag";
    /**
     * 主键标识。
     */
    String ID = "id";
    /**
     * 身份证件号。
     */
    String ID_CARD = "idCard";
    /**
     * 是否纳入库存统计。
     */
    String INCLUDE_INVENTORY = "includeInventory";
    /**
     * 期初余额。
     */
    String INITIAL_BALANCE = "initialBalance";
    /**
     * 注射给药标识。
     */
    String INJECT_FLAG = "injectFlag";
    /**
     * 业务实例标识。
     */
    String INSTANCE_ID = "instanceId";
    /**
     * 定价行上游业务表名（收费项定义 {@code adm_charge_item_definition.instance_table}）。
     */
    String INSTANCE_TABLE = "instanceTable";
    /**
     * 库存状态枚举。
     */
    String INVENTORY_STATUS_ENUM = "inventoryStatusEnum";
    /**
     * 发票业务流水号。
     */
    String INVOICE_BUS_NO = "invoiceBusNo";
    /**
     * 发票标识。
     */
    String INVOICE_ID = "invoiceId";
    /**
     * 入区/入径方式编码。
     */
    String IN_WAY_CODE = "inWayCode";
    /**
     * 逻辑删除标记。
     */
    String IS_DELETED = "isDeleted";
    /**
     * 明细行标识。
     */
    String ITEM_ID = "itemId";
    /**
     * 明细数量。
     */
    String ITEM_QUANTITY = "itemQuantity";
    /**
     * 明细来源表名。
     */
    String ITEM_TABLE = "itemTable";
    /**
     * 种类或性质枚举。
     */
    String KIND_ENUM = "kindEnum";
    /**
     * 限价。
     */
    String LIMIT_PRICE = "limitPrice";
    /**
     * 位置标识。
     */
    String LOCATION_ID = "locationId";
    /**
     * 生产或库存批号。
     */
    String LOT_NUMBER = "lotNumber";
    /**
     * 收费项定价行批次号（{@code adm_charge_item_definition.lot_no}，JPA 属性 {@code lotNo}）。
     */
    String LOT_NO = "lotNo";
    /**
     * 生产厂家编码。
     */
    String MANUFACTURER = "manufacturer";
    /**
     * 生产厂家展示名。
     */
    String MANUFACTURER_TEXT = "manufacturerText";
    /**
     * 药品标识。
     */
    String MEDICATION_ID = "medicationId";
    /**
     * 药品医嘱申请主键（{@code med_medication_dispense.med_req_id}）。
     */
    String MED_REQ_ID = "medReqId";
    /**
     * 医嘱/申请在医生站列表中的展示排序号（整型，小靠前）。
     */
    String SORT_NUMBER = "sortNumber";
    /**
     * 耗材定义标识（发放聚合等设备侧字段名）。
     */
    String DEVICE_DEF_ID = "deviceDefId";
    /**
     * 商品名称。
     */
    String MERCHANDISE_NAME = "merchandiseName";
    /**
     * 商品名拼音检索码。
     */
    String MERCHANDISE_PY_STR = "merchandisePyStr";
    /**
     * 商品名五笔检索码。
     */
    String MERCHANDISE_WB_STR = "merchandiseWbStr";
    /**
     * 诊疗项目业务版本（非乐观锁）。
     */
    String ACTIVITY_VERSION = "activityVersion";
    /**
     * 默认门诊/就诊单位编码。
     */
    String DEF_ENCOUNTER_UNIT_CODE = "defEncounterUnitCode";
    /**
     * 默认住院单位编码。
     */
    String DEF_INHOSPITAL_UNIT_CODE = "defInhospitalUnitCode";
    /**
     * 给药途径或执行方法编码。
     */
    String METHOD_CODE = "methodCode";
    /**
     * 最小包装/计量单位编码。
     */
    String MIN_UNIT_CODE = "minUnitCode";
    /**
     * 名称（泛用；如位置名、通用名称等）。
     */
    String NAME = "name";
    /**
     * 变更后床位标识。
     */
    String NEW_BED_ID = "newBedId";
    /**
     * 变更后床位名称。
     */
    String NEW_BED_NAME = "newBedName";
    /**
     * 变更后展示序。
     */
    String NEW_DISPLAY_ORDER = "newDisplayOrder";
    /**
     * 变更后医护人员标识。
     */
    String NEW_PRACTITIONER_ID = "newPractitionerId";
    /**
     * 变更后医护人员姓名。
     */
    String NEW_PRACTITIONER_NAME = "newPractitionerName";
    /**
     * 变更后病区标识。
     */
    String NEW_WARD_ID = "newWardId";
    /**
     * 变更后病区名称。
     */
    String NEW_WARD_NAME = "newWardName";
    /**
     * 爽约或未到原因。
     */
    String NO_SHOW_REASON = "noShowReason";
    /**
     * 业务发生时刻。
     */
    String OCCURRED_AT = "occurredAt";
    /**
     * 业务发生时刻（与 {@link #OCCURRED_AT} 可能并存于不同载荷）。
     */
    String OCCURRED_TIME = "occurredTime";
    /**
     * 计划或实际结束时刻。
     */
    String OCCURRENCE_END_TIME = "occurrenceEndTime";
    /**
     * 计划或实际开始时刻。
     */
    String OCCURRENCE_START_TIME = "occurrenceStartTime";
    /**
     * 发生时刻（通用）。
     */
    String OCCURRENCE_TIME = "occurrenceTime";
    /**
     * 床位等运营状态。
     */
    String OPERATIONAL_STATUS = "operationalStatus";
    /**
     * 操作人标识。
     */
    String OPERATOR_ID = "operatorId";
    /**
     * 机构标识。
     */
    String ORGANIZATION_ID = "organizationId";
    /**
     * 机构名称。
     */
    String ORGANIZATION_NAME = "organizationName";
    /**
     * 组织单元标识。
     */
    String ORG_ID = "orgId";
    /**
     * 原业务单号。
     */
    String ORIGINAL_BUS_NO = "originalBusNo";
    /**
     * 出区/出径方式编码。
     */
    String OUT_WAY_CODE = "outWayCode";
    /**
     * 上级或父节点标识。
     */
    String PARENT_ID = "parentId";
    /**
     * 上级或父节点名称。
     */
    String PARENT_NAME = "parentName";
    /**
     * 部件/部位属性枚举。
     */
    String PART_ATTRIBUTE_ENUM = "partAttributeEnum";
    /**
     * 患者年龄展示文案。
     */
    String PATIENT_AGE_TEXT = "patientAgeText";
    /**
     * 患者业务流水号。
     */
    String PATIENT_BUS_NO = "patientBusNo";
    /**
     * 患者标识。
     */
    String PATIENT_ID = "patientId";
    /**
     * 患者姓名。
     */
    String PATIENT_NAME = "patientName";
    /**
     * 执行人标识。
     */
    String PERFORMER_ID = "performerId";
    /**
     * 执行机构标识。
     */
    String PERFORMING_ORG_ID = "performingOrgId";
    /**
     * 执行地点描述。
     */
    String PERFORM_LOCATION = "performLocation";
    /**
     * 药理分类编码。
     */
    String PHARMACOLOGY_CATEGORY_CODE = "pharmacologyCategoryCode";
    /**
     * 联系电话。
     */
    String PHONE = "phone";
    /**
     * 计划区间结束。
     */
    String PLANNING_HORIZON_END = "planningHorizonEnd";
    /**
     * 计划区间开始。
     */
    String PLANNING_HORIZON_START = "planningHorizonStart";
    /**
     * 医护人员标识。
     */
    String PRACTITIONER_ID = "practitionerId";
    /**
     * 医护人员姓名。
     */
    String PRACTITIONER_NAME = "practitionerName";
    /**
     * 预收治机构标识。
     */
    String PRE_ADMISSION_ORG_ID = "preAdmissionOrgId";
    /**
     * 预收治或预入院时刻。
     */
    String PRE_ADMISSION_TIME = "preAdmissionTime";
    /**
     * 收费项定价行价格类型编码（{@code adm_charge_item_definition.price_type_code}）。
     */
    String PRICE_TYPE_CODE = "priceTypeCode";
    /**
     * 收费项定价行价格数值（{@code adm_charge_item_definition.price_value}）。
     */
    String PRICE_VALUE = "priceValue";
    /**
     * 单价或价格。
     */
    String PRICE = "price";
    /**
     * 是否参与计价标识。
     */
    String PRICING_FLAG = "pricingFlag";
    /**
     * 优先级枚举。
     */
    String PRIORITY_ENUM = "priorityEnum";
    /**
     * 操作/手术标识。
     */
    String PROCEDURE_ID = "procedureId";
    /**
     * 产品业务流水号。
     */
    String PRODUCT_BUS_NO = "productBusNo";
    /**
     * 产品定义主键（读模型 {@code hisrm_product_definition.product_definition_id}，JPA 属性 {@code productDefinitionId}）。
     */
    String PRODUCT_DEFINITION_ID = "productDefinitionId";
    /**
     * 产品名称。
     */
    String PRODUCT_NAME = "productName";
    /**
     * 产品来源物理表名。
     */
    String PRODUCT_TABLE_NAME = "productTableName";
    /**
     * 采购单位编码。
     */
    String PURCHASE_UNIT_CODE = "purchaseUnitCode";
    /**
     * 采购单位换算比。
     */
    String PURCHASE_UNIT_RATIO = "purchaseUnitRatio";
    /**
     * 用途地点标识。
     */
    String PURPOSE_LOCATION_ID = "purposeLocationId";
    /**
     * 用途类型枚举。
     */
    String PURPOSE_TYPE_ENUM = "purposeTypeEnum";
    /**
     * 拼音检索串。
     */
    String PY_STR = "pyStr";
    /**
     * 数量。
     */
    String QUANTITY = "quantity";
    /**
     * 频次或费率编码。
     */
    String RATE_CODE = "rateCode";
    /**
     * 原因简述。
     */
    String REASON = "reason";
    /**
     * 原因正文。
     */
    String REASON_TEXT = "reasonText";
    /**
     * 单据完结标识。
     */
    String RECEIPT_FINALIZED_FLAG = "receiptFinalizedFlag";
    /**
     * 接收人标识。
     */
    String RECEIVER_ID = "receiverId";
    /**
     * 接收时刻。
     */
    String RECEIVE_TIME = "receiveTime";
    /**
     * 接诊开始时刻。
     */
    String RECEPTION_TIME = "receptionTime";
    /**
     * 退费单标识。
     */
    String REFUND_ID = "refundId";
    /**
     * 原耗材申请主键（退耗材医嘱 {@code wkf_device_request.refund_device_id}）。
     */
    String REFUND_DEVICE_ID = "refundDeviceId";
    /**
     * 原药品申请主键（退药医嘱 {@code med_medication_request.refund_medicine_id}）。
     */
    String REFUND_MEDICINE_ID = "refundMedicineId";
    /**
     * 原服务申请主键（退服务医嘱 {@code wkf_service_request.refund_service_id}）。
     */
    String REFUND_SERVICE_ID = "refundServiceId";
    /**
     * 就诊参与者角色编码（{@code adm_encounter_participant.role_code}）。
     */
    String ROLE_CODE = "roleCode";
    /**
     * 挂号员标识。
     */
    String REGISTRAR_ID = "registrarId";
    /**
     * 挂号员姓名。
     */
    String REGISTRAR_NAME = "registrarName";
    /**
     * 挂号所选医师标识。
     */
    String REGISTRATION_DOCTOR_ID = "registrationDoctorId";
    /**
     * 挂号所选医师姓名。
     */
    String REGISTRATION_DOCTOR_NAME = "registrationDoctorName";
    /**
     * 关联单据明细标识。
     */
    String RELATED_DOC_DETAIL_ID = "relatedDocDetailId";
    /**
     * 关联单据标识。
     */
    String RELATED_DOC_ID = "relatedDocId";
    /**
     * 关联单据类型。
     */
    String RELATED_DOC_TYPE = "relatedDocType";
    /**
     * 补打或重制标记。
     */
    String REMAKE = "remake";
    /**
     * 备注。
     */
    String REMARK = "remark";
    /**
     * 限制用药标志。
     */
    String RESTRICTED_FLAG = "restrictedFlag";
    /**
     * 限制用药范围说明正文。
     */
    String RESTRICTED_SCOPE = "restrictedScope";
    /**
     * 限制用药等级或权限枚举编码。
     */
    String RESTRICTED_ENUM = "restrictedEnum";
    /**
     * 申请开立人标识。
     */
    String REQUESTER_ID = "requesterId";
    /**
     * 申请单标识。
     */
    String REQUEST_ID = "requestId";
    /**
     * 规则标识。
     */
    String RULE_ID = "ruleId";
    /**
     * 规则名称。
     */
    String RULE_NAME = "ruleName";
    /**
     * 处方药标识。
     */
    String RX_FLAG = "rxFlag";
    /**
     * 包装单位编码（领域/持久化/对外 JSON 主键名；与 {@link #PACKAGE_UNIT_CODE_LEGACY_EVENT_KEY} 互链）。
     */
    String PACKAGE_UNIT_CODE = "packageUnitCode";
    /**
     * 历史事件载荷中的包装单位键名（回放兼容旧版 {@code salesUnitCode} 序列化）。
     *
     * @see #PACKAGE_UNIT_CODE
     */
    String PACKAGE_UNIT_CODE_LEGACY_EVENT_KEY = "salesUnitCode";
    /**
     * 包装单位→拆零单位换算比（事件载荷与 JPA 属性名）。
     */
    String PACKAGE_UNIT_RATIO = "packageUnitRatio";
    /**
     * 历史事件载荷中的包装换算比键名（回放兼容旧版 {@code salesUnitRatio}）。
     *
     * @see #PACKAGE_UNIT_RATIO
     */
    String PACKAGE_UNIT_RATIO_LEGACY_EVENT_KEY = "salesUnitRatio";
    /**
     * 服务类型标识。
     */
    String SERVICE_TYPE_ID = "serviceTypeId";
    /**
     * 上游业务表锚点（收费明细 {@code adm_charge_item.service_table}）。
     */
    String SERVICE_TABLE = "serviceTable";
    /**
     * 服务类型名称。
     */
    String SERVICE_TYPE_NAME = "serviceTypeName";
    /**
     * 结算日期。
     */
    String SETTLE_DATE = "settleDate";
    /**
     * 规格量或尺寸。
     */
    String SIZE = "size";
    /**
     * 皮试要求标识。
     */
    String SKIN_TEST_FLAG = "skinTestFlag";
    /**
     * 单据号。
     */
    String SLIP_NO = "slipNo";
    /**
     * 排序字段名（动态排序）。
     */
    String SORT_FIELD = "sortField";
    /**
     * 排序方向（动态排序）。
     */
    String SORT_ORDER = "sortOrder";
    /**
     * 来源位置标识。
     */
    String SOURCE_LOCATION_ID = "sourceLocationId";
    /**
     * 来源类型枚举。
     */
    String SOURCE_TYPE_ENUM = "sourceTypeEnum";
    /**
     * 专科编码。
     */
    String SPECIALTY_CODE = "specialtyCode";
    /**
     * 标本类型编码。
     */
    String SPECIMEN_CODE = "specimenCode";
    /**
     * 拆零单位编码。
     */
    String SPLIT_UNIT_CODE = "splitUnitCode";
    /**
     * 标准价或目录价。
     */
    String STANDARD_PRICE = "standardPrice";
    /**
     * 开始时刻。
     */
    String START_TIME = "startTime";
    /**
     * 状态枚举码。
     */
    String STATUS_ENUM = "statusEnum";
    /**
     * 位置形态枚举（就诊位置 {@code adm_encounter_location.form_enum}）。
     */
    String FORM_ENUM = "formEnum";
    /**
     * 业务状态最近变更时刻（发药单读模型 {@code status_changed_time}）。
     */
    String STATUS_CHANGED_TIME = "statusChangedTime";
    /**
     * 状态展示文案。
     */
    String STATUS_ENUM_TEXT = "statusEnumText";
    /**
     * 库存单位编码。
     */
    String STOCK_UNIT_CODE = "stockUnitCode";
    /**
     * 库存单位换算比。
     */
    String STOCK_UNIT_RATIO = "stockUnitRatio";
    /**
     * 汇总主表标识。
     */
    String SUMMARY_ID = "summaryId";
    /**
     * 汇总单号。
     */
    String SUMMARY_NO = "summaryNo";
    /**
     * 供应商标识。
     */
    String SUPPLIER_ID = "supplierId";
    /**
     * 供应或申领单标识。
     */
    String SUPPLY_ID = "supplyId";
    /**
     * 目标定义标识。
     */
    String TARGET_DEFINITION_ID = "targetDefinitionId";
    /**
     * 租户标识（多租户行级隔离，与审计基类 {@code tenant_id} 对齐）。
     */
    String TENANT_ID = "tenantId";
    /**
     * 临时建档或临时状态标识。
     */
    String TEMP_FLAG = "tempFlag";
    /**
     * 治疗类型枚举。
     */
    String THERAPY_ENUM = "therapyEnum";
    /**
     * 是否中药标记（OrderInfo / MedicationRequest 投影列）。
     */
    String TCM_FLAG = "tcmFlag";
    /**
     * 医嘱生成来源枚举（GenerateSource#getCode()）。
     */
    String GENERATE_SOURCE_ENUM = "generateSourceEnum";
    /**
     * 诊疗侧上级医嘱来源表（OrderInfo / ServiceRequest {@code based_on_table}）。
     */
    String BASED_ON_TABLE = "basedOnTable";
    /**
     * 诊疗侧上级医嘱来源 ID（OrderInfo / ServiceRequest {@code based_on_id}）。
     */
    String BASED_ON_ID = "basedOnId";
    /**
     * 耗材侧上级来源表（OrderInfo {@code dev_based_on_table}）。
     */
    String DEV_BASED_ON_TABLE = "devBasedOnTable";
    /**
     * 耗材侧上级来源 ID（OrderInfo {@code dev_based_on_id}）。
     */
    String DEV_BASED_ON_ID = "devBasedOnId";
    /**
     * 医嘱开立时间（OrderInfo {@code authored_time}）。
     */
    String AUTHORED_TIME = "authoredTime";
    /**
     * 医嘱来源类型（SERVICE_REQUEST / MED_REQUEST / DEVICE_REQUEST）。
     */
    String ORDER_TYPE = "orderType";
    /**
     * 第三方部件属性枚举。
     */
    String THO_PART_ATTRIBUTE_ENUM = "thoPartAttributeEnum";
    /**
     * 总价或合计金额。
     */
    String TOTAL_PRICE = "totalPrice";
    /**
     * 规格量或制剂总量描述（药品 {@code total_volume} 等）。
     */
    String TOTAL_VOLUME = "totalVolume";
    /**
     * 追溯码。
     */
    String TRACE_NO = "traceNo";
    /**
     * 分诊结束时刻。
     */
    String TRIAGE_END_TIME = "triageEndTime";
    /**
     * 分诊记录标识。
     */
    String TRIAGE_ID = "triageId";
    /**
     * 分诊级别枚举。
     */
    String TRIAGE_LEVEL_ENUM = "triageLevelEnum";
    /**
     * 急诊同步的分诊级别。
     */
    String TRIAGE_LEVEL_FROM_EMG = "triageLevelFromEmg";
    /**
     * 分诊护士标识。
     */
    String TRIAGE_NURSE_ID = "triageNurseId";
    /**
     * 分诊开始时刻。
     */
    String TRIAGE_START_TIME = "triageStartTime";
    /**
     * 分诊时刻。
     */
    String TRIAGE_TIME = "triageTime";
    /**
     * 触发来源主键。
     */
    String TRIGGER_SOURCE_ID = "triggerSourceId";
    /**
     * 触发来源表名。
     */
    String TRIGGER_SOURCE_TABLE = "triggerSourceTable";
    /**
     * 类型编码。
     */
    String TYPE_CODE = "typeCode";
    /**
     * 类型枚举码。
     */
    String TYPE_ENUM = "typeEnum";
    /**
     * 诊疗套餐子项覆盖 JSON 中的执行次数键（前端草稿 {@code childOverridesJson} 约定）。
     */
    String EXECUTION_COUNT = "executionCount";
    /**
     * 计量单位编码。
     */
    String UNIT_CODE = "unitCode";
    /**
     * 使用结束时刻。
     */
    String USE_END_TIME = "useEndTime";
    /**
     * 使用开始时刻。
     */
    String USE_START_TIME = "useStartTime";
    /**
     * 使用时刻。
     */
    String USE_TIME = "useTime";
    /**
     * 用药时机说明。
     */
    String USE_TIMING = "useTiming";
    /**
     * 病区标识。
     */
    String WARD_ID = "wardId";
    /**
     * 病区名称。
     */
    String WARD_NAME = "wardName";
    /**
     * 五笔检索串。
     */
    String WB_STR = "wbStr";
    /**
     * 医保相关标识。
     */
    String YB_FLAG = "ybFlag";
    /**
     * 医保对码匹配标识。
     */
    String YB_MATCH_FLAG = "ybMatchFlag";
    /**
     * 医保目录编码。
     */
    String YB_NO = "ybNo";
    /**
     * 医保机构编号。
     */
    String YB_ORG_NO = "ybOrgNo";
    /**
     * 目录或事实的来源分类枚举码（如诊断定义 {@code sourceEnum}）。
     */
    String SOURCE_ENUM = "sourceEnum";
    /**
     * 最近更新时间（审计字段，如 {@code AuditedEntityBase#updatedAt}）。
     */
    String UPDATED_AT = "updatedAt";
}
