/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

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
/** FieldName 共享常量：ADDRESS，值 {@code address}。 */
    String ADDRESS = "address";
/** FieldName 共享常量：time，值 {@code admissionTime}。 */
    String ADMISSION_TIME = "admissionTime";
/** FieldName 共享常量：quantity，值 {@code afterQuantity}。 */
    String AFTER_QUANTITY = "afterQuantity";
/** FieldName 共享常量：AMOUNT，值 {@code amount}。 */
    String AMOUNT = "amount";
    /**
     * 抗生素分级/管理相关标识。
     */
    String ANTIBIOTIC_FLAG = "antibioticFlag";
/** FieldName 共享常量：id，值 {@code applicantId}。 */
    String APPLICANT_ID = "applicantId";
/** FieldName 共享常量：time，值 {@code applyTime}。 */
    String APPLY_TIME = "applyTime";
/** FieldName 共享常量：time，值 {@code approvalTime}。 */
    String APPROVAL_TIME = "approvalTime";
/** FieldName 共享常量：id，值 {@code approverId}。 */
    String APPROVER_ID = "approverId";
    /**
     * 基本目录/基本药物标识。
     */
    String BASIC_FLAG = "basicFlag";
    /**
     * 集采药品标识（药典勾选=是）。
     */
    String CENTRAL_PURCHASE_FLAG = "centralPurchaseFlag";
    /**
     * 结余留用批次号（药典术语 code；AS-RPT-015 筛选）。
     */
    String SURPLUS_RETENTION_BATCH_CODE = "surplusRetentionBatchCode";
    /**
     * 国家重点监控药品标识（药典勾选=是）。
     */
    String NATIONAL_MONITOR_FLAG = "nationalMonitorFlag";
/** FieldName 共享常量：id，值 {@code bedId}。 */
    String BED_ID = "bedId";
/** FieldName 共享常量：name，值 {@code bedName}。 */
    String BED_NAME = "bedName";
/** FieldName 共享常量：quantity，值 {@code beforeQuantity}。 */
    String BEFORE_QUANTITY = "beforeQuantity";
/** FieldName 共享常量：date，值 {@code billDate}。 */
    String BILL_DATE = "billDate";
    /**
     * 收费员交账头标识（payment 挂载列）。
     */
    String CASHIER_SETTLEMENT_ID = "cashierSettlementId";
/** FieldName 共享常量：date，值 {@code birthDate}。 */
    String BIRTH_DATE = "birthDate";
/** FieldName 共享常量：site code，值 {@code bodySiteCode}。 */
    String BODY_SITE_CODE = "bodySiteCode";
/** FieldName 共享常量：no，值 {@code busNo}。 */
    String BUS_NO = "busNo";
/** FieldName 共享常量：at，值 {@code cancelledAt}。 */
    String CANCELLED_AT = "cancelledAt";
    /**
     * 取消时刻（与 {@link #CANCELLED_AT} 同义异键）。
     */
    String CANCELLED_TIME = "cancelledTime";
/** FieldName 共享常量：code，值 {@code categoryCode}。 */
    String CATEGORY_CODE = "categoryCode";
    /**
     * 分类字典展示文案。
     */
    String CATEGORY_CODE_DICT_TEXT = "categoryCodeDictText";
/** FieldName 共享常量：enum，值 {@code categoryEnum}。 */
    String CATEGORY_ENUM = "categoryEnum";
/** FieldName 共享常量：quantity，值 {@code changeQuantity}。 */
    String CHANGE_QUANTITY = "changeQuantity";
    /**
     * 含子项/组合项标识。
     */
    String CHILDREN_FLAG = "childrenFlag";
    /**
     * 套餐子项引用的诊疗定义主键（{@code wkf_activity_definition_children.children_id}）。
     */
    String CHILDREN_ID = "childrenId";
    /**
     * 子节点或子项 JSON。
     */
    String CHILDREN_JSON = "childrenJson";
    /**
     * 医保收费项目等级（chrgitm）。
     */
    String CHRGITM_LV = "chrgitmLv";
    /**
     * 是否为收费耗材标记。
     */
    String CHARGEABLE_FLAG = "chargeableFlag";
    /**
     * 病案结算类型编码。
     */
    String MR_SETTLEMENT_TYPE_CODE = "mrSettlementTypeCode";
/** FieldName 共享常量：state enum，值 {@code claimStateEnum}。 */
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
     * 通用业务编码字段（如观察定义 {@code code}）。
     */
    String CODE = "code";
    /**
     * 通用展示名字段（如观察定义 {@code display}）。
     */
    String DISPLAY = "display";
/** FieldName 共享常量：code，值 {@code conditionCode}。 */
    String CONDITION_CODE = "conditionCode";
    /**
     * 国家临床版诊断编码（国临码，持久化列 {@code clinic_code}）。
     */
    String CLINIC_CODE = "clinicCode";
    /**
     * 手术操作目录编码（持久化列 {@code procedure_code}）。
     */
    String PROCEDURE_CODE = "procedureCode";
    /**
     * 诊断事实主键（医生站快照 JSON、就诊诊断关联）。
     */
    String CONDITION_ID = "conditionId";
    /**
     * 就诊诊断行或快照：诊断定义主键（持久化列 {@code condition_definition_id}，指向 {@code cln_condition_definition.id}）。
     */
    String CONDITION_DEFINITION_ID = "conditionDefinitionId";
    /**
     * 诊断定义标准名称（医生站快照 JSON 中与 {@link #CONDITION_DEFINITION_ID} 成对；就诊诊断关联事件主诊断摘要等同名载荷）。
     */
    String CONDITION_DEFINITION_NAME = "conditionDefinitionName";
/** FieldName 共享常量：enum，值 {@code contextEnum}。 */
    String CONTEXT_ENUM = "contextEnum";
/** FieldName 共享常量：no，值 {@code contractNo}。 */
    String CONTRACT_NO = "contractNo";
    /**
     * 费用性质/合同主键（{@code contract_id}）。
     */
    String CONTRACT_ID = "contractId";
    /**
     * 费用性质合同业务号（{@code contract_code} / {@code bus_no}；耗材行无 contract_id 时落此列）。
     */
    String CONTRACT_CODE = "contractCode";
/** FieldName 共享常量：at，值 {@code createdAt}。 */
    String CREATED_AT = "createdAt";
    /**
     * 创建时间（部分读模型或接口使用该字段名）。
     */
    String CREATE_TIME = "createTime";
/** FieldName 共享常量：id，值 {@code deliveryId}。 */
    String DELIVERY_ID = "deliveryId";
/** FieldName 共享常量：lot number，值 {@code deliveryLotNumber}。 */
    String DELIVERY_LOT_NUMBER = "deliveryLotNumber";
    /**
     * 发放状态枚举（库房单据明细读模型等）。
     */
    String DELIVERY_STATUS_ENUM = "deliveryStatusEnum";
/** FieldName 共享常量：text，值 {@code descriptionText}。 */
    String DESCRIPTION_TEXT = "descriptionText";
    /**
     * 描述/诊断描述等泛用文本（如 {@code adm_encounter_diagnosis.description}）。
     */
    String DESCRIPTION = "description";
/** FieldName 共享常量：location id，值 {@code destinationLocationId}。 */
    String DESTINATION_LOCATION_ID = "destinationLocationId";
/** FieldName 共享常量：location name，值 {@code destinationLocationName}。 */
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
     * 就诊诊断关联主键（快照元素级锚点；单值，非医嘱绑定 有序 ids 串）。
     */
    String ENCOUNTER_DIAGNOSIS_ID = "encounterDiagnosisId";
    /**
     * 医嘱/账单绑定就诊诊断主键有序串（有序；首位为主诊断 {@code adm_encounter_diagnosis.id}）。
     */
    String ENCOUNTER_DIAGNOSIS_IDS = "encounterDiagnosisIds";
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
     * 医生站诊断快照：中医病证组号（同组病名+证候共用，对齐 {@code adm_encounter_diagnosis.tcm_syndrome_group}）。
     */
    String TCM_SYNDROME_GROUP = "tcmSyndromeGroup";
    /**
     * 医生站诊断快照：验证状态 UI 整数码（入库前经域服务映射为 FHIR 码）。
     */
    String VERIFICATION_STATUS_ENUM = "verificationStatusEnum";
    /**
     * 医生站诊断快照：验证状态展示文案（可选冗余列）。
     */
    String VERIFICATION_STATUS_ENUM_TEXT = "verificationStatusEnumText";
/** FieldName 共享常量：time，值 {@code dischargeTime}。 */
    String DISCHARGE_TIME = "dischargeTime";
/** FieldName 共享常量：type enum，值 {@code dischargeTypeEnum}。 */
    String DISCHARGE_TYPE_ENUM = "dischargeTypeEnum";
/** FieldName 共享常量：order，值 {@code displayOrder}。 */
    String DISPLAY_ORDER = "displayOrder";
/** FieldName 共享常量：id，值 {@code doctorId}。 */
    String DOCTOR_ID = "doctorId";
/** FieldName 共享常量：name，值 {@code doctorName}。 */
    String DOCTOR_NAME = "doctorName";
/** FieldName 共享常量：instruction，值 {@code dosageInstruction}。 */
    String DOSAGE_INSTRUCTION = "dosageInstruction";
/** FieldName 共享常量：from，值 {@code doseFrom}。 */
    String DOSE_FROM = "doseFrom";
/** FieldName 共享常量：unit code，值 {@code doseUnitCode}。 */
    String DOSE_UNIT_CODE = "doseUnitCode";
/** FieldName 共享常量：unit ratio，值 {@code doseUnitRatio}。 */
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

    /** 核发发药时刻（{@code med_medication_dispense.dispense_time}）。 */
    String DISPENSE_TIME = "dispenseTime";

    /**
     * 实发数量（{@code wkf_device_dispense.dispense_quantity}；可空，空则回落 {@link #QUANTITY}）。
     */
    String DISPENSE_QUANTITY = "dispenseQuantity";

    /** 计划发药时刻（{@code med_medication_dispense.planned_dispense_time}）。 */
    String PLANNED_DISPENSE_TIME = "plannedDispenseTime";

    /** 耗材拣配时刻（{@code wkf_device_dispense.prepare_time}）。 */
    String PREPARE_TIME = "prepareTime";

    /** 耗材请求主键（{@code wkf_device_dispense.device_req_id}）。 */
    String DEVICE_REQ_ID = "deviceReqId";
    /**
     * 药品成组号（药品申请 {@code groupId}）。
     */
    String GROUP_ID = "groupId";
/** FieldName 共享常量：bus no，值 {@code encounterBusNo}。 */
    String ENCOUNTER_BUS_NO = "encounterBusNo";
/** FieldName 共享常量：id，值 {@code encounterId}。 */
    String ENCOUNTER_ID = "encounterId";
/** FieldName 共享常量：time，值 {@code endTime}。 */
    String END_TIME = "endTime";
/** FieldName 共享常量：id，值 {@code entererId}。 */
    String ENTERER_ID = "entererId";
/** FieldName 共享常量：date，值 {@code enteredDate}（收费明细=收费时间）。 */
    String ENTERED_DATE = "enteredDate";
/** FieldName 共享常量：content，值 {@code entrustContent}。 */
    String ENTRUST_CONTENT = "entrustContent";
/** FieldName 共享常量：date，值 {@code expirationDate}。 */
    String EXPIRATION_DATE = "expirationDate";
/** FieldName 共享常量：dose，值 {@code firstDose}。 */
    String FIRST_DOSE = "firstDose";
    /**
     * 首次剂量单位编码。
     */
    String FIRST_DOSE_UNIT_CODE = "firstDoseUnitCode";
    /**
     * 分次剂量序列（药品申请 {@code dose_schedule}；BR-MRQ-035）。
     */
    String DOSE_SCHEDULE = "doseSchedule";
    /**
     * 分次用量序列（药品申请 {@code quantity_schedule}；BR-MRQ-035）。
     */
    String QUANTITY_SCHEDULE = "quantitySchedule";
/** FieldName 共享常量：enum，值 {@code genderEnum}。 */
    String GENDER_ENUM = "genderEnum";
/** FieldName 共享常量：enum text，值 {@code genderEnumText}。 */
    String GENDER_ENUM_TEXT = "genderEnumText";
    /**
     * 基本药物目录（HVCM）标识。
     */
    String HVCM_FLAG = "hvcmFlag";
/** FieldName 共享常量：ID，值 {@code id}。 */
    String ID = "id";
/** FieldName 共享常量：card，值 {@code idCard}。 */
    String ID_CARD = "idCard";
    /**
     * 是否纳入库存统计。
     */
    String INCLUDE_INVENTORY = "includeInventory";
/** FieldName 共享常量：balance，值 {@code initialBalance}。 */
    String INITIAL_BALANCE = "initialBalance";
/** FieldName 共享常量：flag，值 {@code injectFlag}。 */
    String INJECT_FLAG = "injectFlag";
/** FieldName 共享常量：id，值 {@code instanceId}。 */
    String INSTANCE_ID = "instanceId";
    /**
     * 定价行上游业务表名（收费项定义 {@code adm_charge_item_definition.instance_table}）。
     */
    String INSTANCE_TABLE = "instanceTable";
/** FieldName 共享常量：status enum，值 {@code inventoryStatusEnum}。 */
    String INVENTORY_STATUS_ENUM = "inventoryStatusEnum";
    /**
     * 源库存项主键（快照静表 {@code inventory_item_id}）。
     */
    String INVENTORY_ITEM_ID = "inventoryItemId";
/** FieldName 共享常量：bus no，值 {@code invoiceBusNo}。 */
    String INVOICE_BUS_NO = "invoiceBusNo";
/** FieldName 共享常量：id，值 {@code invoiceId}。 */
    String INVOICE_ID = "invoiceId";
    /**
     * 入区/入径方式编码。
     */
    String IN_WAY_CODE = "inWayCode";
/** FieldName 共享常量：deleted，值 {@code isDeleted}。 */
    String IS_DELETED = "isDeleted";
/** FieldName 共享常量：id，值 {@code itemId}。 */
    String ITEM_ID = "itemId";
/** FieldName 共享常量：quantity，值 {@code itemQuantity}。 */
    String ITEM_QUANTITY = "itemQuantity";
    /**
     * 实盘量或合计拆零量（盘点/损益等场景）。
     */
    String TOTAL_QUANTITY = "totalQuantity";
/** FieldName 共享常量：table，值 {@code itemTable}。 */
    String ITEM_TABLE = "itemTable";
/** FieldName 共享常量：enum，值 {@code kindEnum}。 */
    String KIND_ENUM = "kindEnum";
/** FieldName 共享常量：price，值 {@code limitPrice}。 */
    String LIMIT_PRICE = "limitPrice";
    /**
     * 进价行总额（快照静表 {@code line_cost_total}）。
     */
    String LINE_COST_TOTAL = "lineCostTotal";
    /**
     * 售价行总额（快照静表 {@code line_price_total}）。
     */
    String LINE_PRICE_TOTAL = "linePriceTotal";
/** FieldName 共享常量：id，值 {@code locationId}。 */
    String LOCATION_ID = "locationId";
/** FieldName 共享常量：number，值 {@code lotNumber}。 */
    String LIMIT_TIME = "limitTime";

    String LOT_NUMBER = "lotNumber";
    /**
     * 批号进价（快照静表 {@code lot_number_cost_amount}）。
     */
    String LOT_NUMBER_COST_AMOUNT = "lotNumberCostAmount";
    /**
     * 批号售价（快照静表 {@code lot_number_price_amount}）。
     */
    String LOT_NUMBER_PRICE_AMOUNT = "lotNumberPriceAmount";
    /**
     * 收费项定价行批次号（{@code adm_charge_item_definition.lot_no}，JPA 属性 {@code lotNo}）。
     */
    String LOT_NO = "lotNo";
/** FieldName 共享常量：MANUFACTURER，值 {@code manufacturer}。 */
    String MANUFACTURER = "manufacturer";
    /**
     * 生产厂家主键 JPA 属性名（{@code manufacturerId} / 列 {@code manufacturer_id}）。
     */
    String MANUFACTURER_ID = "manufacturerId";
/** FieldName 共享常量：text，值 {@code manufacturerText}（导出列键/展示字段名，非实体持久化列）。 */
    String MANUFACTURER_TEXT = "manufacturerText";
/** FieldName 共享常量：id，值 {@code medicationId}。 */
    String MEDICATION_ID = "medicationId";
    /**
     * 药品分类字典 code（快照静表 {@code medication_category_code} 等）。
     */
    String MEDICATION_CATEGORY_CODE = "medicationCategoryCode";
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
     * 活动定义主键（服务申请 {@code activity_id}）。
     */
    String ACTIVITY_ID = "activityId";
    /**
     * 耗材分类字典 code（快照静表 {@code device_category_code} 等）。
     */
    String DEVICE_CATEGORY_CODE = "deviceCategoryCode";
/** FieldName 共享常量：name，值 {@code merchandiseName}。 */
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
/** FieldName 共享常量：bed id，值 {@code newBedId}。 */
    String NEW_BED_ID = "newBedId";
/** FieldName 共享常量：bed name，值 {@code newBedName}。 */
    String NEW_BED_NAME = "newBedName";
/** FieldName 共享常量：display order，值 {@code newDisplayOrder}。 */
    String NEW_DISPLAY_ORDER = "newDisplayOrder";
    /**
     * 变更后医护人员标识。
     */
    String NEW_PRACTITIONER_ID = "newPractitionerId";
    /**
     * 变更后医护人员姓名。
     */
    String NEW_PRACTITIONER_NAME = "newPractitionerName";
/** FieldName 共享常量：ward id，值 {@code newWardId}。 */
    String NEW_WARD_ID = "newWardId";
/** FieldName 共享常量：ward name，值 {@code newWardName}。 */
    String NEW_WARD_NAME = "newWardName";
/** FieldName 共享常量：show reason，值 {@code noShowReason}。 */
    String NO_SHOW_REASON = "noShowReason";
/** FieldName 共享常量：at，值 {@code occurredAt}。 */
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
/** FieldName 共享常量：status，值 {@code operationalStatus}。 */
    String OPERATIONAL_STATUS = "operationalStatus";
/** FieldName 共享常量：id，值 {@code operatorId}。 */
    String OPERATOR_ID = "operatorId";
/** FieldName 共享常量：id，值 {@code organizationId}。 */
    String ORGANIZATION_ID = "organizationId";
/** FieldName 共享常量：name，值 {@code organizationName}。 */
    String ORGANIZATION_NAME = "organizationName";
/** FieldName 共享常量：id，值 {@code orgId}。 */
    String ORG_ID = "orgId";
/** FieldName 共享常量：bus no，值 {@code originalBusNo}。 */
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
/** FieldName 共享常量：bus no，值 {@code patientBusNo}。 */
    String PATIENT_BUS_NO = "patientBusNo";
/** FieldName 共享常量：id，值 {@code patientId}。 */
    String PATIENT_ID = "patientId";
/** FieldName 共享常量：name，值 {@code patientName}。 */
    String PATIENT_NAME = "patientName";
/** FieldName 共享常量：id，值 {@code performerId}。 */
    String PERFORMER_ID = "performerId";
/** FieldName 共享常量：org id，值 {@code performingOrgId}。 */
    String PERFORMING_ORG_ID = "performingOrgId";
/** FieldName 共享常量：location，值 {@code performLocation}。 */
    String PERFORM_LOCATION = "performLocation";
/** FieldName 共享常量：category code，值 {@code pharmacologyCategoryCode}。 */
    String PHARMACOLOGY_CATEGORY_CODE = "pharmacologyCategoryCode";
/** FieldName 共享常量：PHONE，值 {@code phone}。 */
    String PHONE = "phone";
/** FieldName 共享常量：horizon end，值 {@code planningHorizonEnd}。 */
    String PLANNING_HORIZON_END = "planningHorizonEnd";
/** FieldName 共享常量：horizon start，值 {@code planningHorizonStart}。 */
    String PLANNING_HORIZON_START = "planningHorizonStart";
/** FieldName 共享常量：id，值 {@code practitionerId}。 */
    String PRACTITIONER_ID = "practitionerId";
/** FieldName 共享常量：name，值 {@code practitionerName}。 */
    String PRACTITIONER_NAME = "practitionerName";
/** FieldName 共享常量：admission org id，值 {@code preAdmissionOrgId}。 */
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
/** FieldName 共享常量：PRICE，值 {@code price}。 */
    String PRICE = "price";
    /**
     * 是否参与计价标识。
     */
    String PRICING_FLAG = "pricingFlag";
/** FieldName 共享常量：enum，值 {@code priorityEnum}。 */
    String PRIORITY_ENUM = "priorityEnum";
/** FieldName 共享常量：id，值 {@code procedureId}。 */
    String PROCEDURE_ID = "procedureId";
/** FieldName 共享常量：bus no，值 {@code productBusNo}。 */
    String PRODUCT_BUS_NO = "productBusNo";
    /**
     * 产品定义主键（读模型 {@code hisrm_product_definition.product_definition_id}，JPA 属性 {@code productDefinitionId}）。
     */
    String PRODUCT_DEFINITION_ID = "productDefinitionId";
/** FieldName 共享常量：name，值 {@code productName}。 */
    String PRODUCT_NAME = "productName";
    /**
     * 产品来源物理表名。
     */
    String PRODUCT_TABLE_NAME = "productTableName";

    /** 收费明细产品来源表（{@code adm_charge_item.product_table}，JPA {@code productTable}）。 */
    String PRODUCT_TABLE = "productTable";
    /** 折扣率字段名（收费折扣配置 {@code discount_rate}）。 */
    String DISCOUNT_RATE = "discountRate";

    /** 收费明细产品主键（{@code adm_charge_item.product_id}，JPA {@code productId}）。 */
    String PRODUCT_ID = "productId";

    /** 收费明细挂账账户主键（{@code adm_charge_item.account_id}，JPA {@code accountId}）。 */
    String ACCOUNT_ID = "accountId";

    /** 开立/申请机构主键（{@code adm_charge_item.requesting_org_id}，JPA {@code requestingOrgId}）。 */
    String REQUESTING_ORG_ID = "requestingOrgId";
/** FieldName 共享常量：unit code，值 {@code purchaseUnitCode}。 */
    String PURCHASE_UNIT_CODE = "purchaseUnitCode";
/** FieldName 共享常量：unit ratio，值 {@code purchaseUnitRatio}。 */
    String PURCHASE_UNIT_RATIO = "purchaseUnitRatio";
/** FieldName 共享常量：location id，值 {@code purposeLocationId}。 */
    String PURPOSE_LOCATION_ID = "purposeLocationId";

    /** 领用科室（行政 Organization 主键，JPA 属性名）。 */
    String CONSUMING_ORGANIZATION_ID = "consumingOrganizationId";
/** FieldName 共享常量：type enum，值 {@code purposeTypeEnum}。 */
    String PURPOSE_TYPE_ENUM = "purposeTypeEnum";
/** FieldName 共享常量：str，值 {@code pyStr}。 */
    String PY_STR = "pyStr";
/** FieldName 共享常量：QUANTITY，值 {@code quantity}。 */
    String QUANTITY = "quantity";
/** FieldName 共享常量：code，值 {@code rateCode}。 */
    String RATE_CODE = "rateCode";
    /**
     * 输液滴速编码（{@code med_medication_request.speed_code}）。
     */
    String SPEED_CODE = "speedCode";
/** FieldName 共享常量：REASON，值 {@code reason}。 */
    String REASON = "reason";
/** FieldName 共享常量：text，值 {@code reasonText}。 */
    String REASON_TEXT = "reasonText";
    /**
     * 是否合理标记（处方点评 {@code reasonable_flag}）。
     */
    String REASONABLE_FLAG = "reasonableFlag";
/** FieldName 共享常量：finalized flag，值 {@code receiptFinalizedFlag}。 */
    String RECEIPT_FINALIZED_FLAG = "receiptFinalizedFlag";
/** FieldName 共享常量：id，值 {@code receiverId}。 */
    String RECEIVER_ID = "receiverId";
/** FieldName 共享常量：time，值 {@code receiveTime}。 */
    String RECEIVE_TIME = "receiveTime";
/** FieldName 共享常量：time，值 {@code receptionTime}。 */
    String RECEPTION_TIME = "receptionTime";
/** FieldName 共享常量：id，值 {@code refundId}。 */
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
     * 关联原支付单标识（逆向退费单 {@code fin_payment_reconciliation.relation_id}）。
     */
    String RELATION_ID = "relationId";
    /**
     * 就诊参与者角色编码（{@code adm_encounter_participant.role_code}）。
     */
    String ROLE_CODE = "roleCode";
/** FieldName 共享常量：id，值 {@code registrarId}。 */
    String REGISTRAR_ID = "registrarId";
/** FieldName 共享常量：name，值 {@code registrarName}。 */
    String REGISTRAR_NAME = "registrarName";
    /**
     * 剩余过期天数（快照写入时刻冻结，{@code remaining_days}）。
     */
    String REMAINING_DAYS = "remainingDays";
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
/** FieldName 共享常量：doc id，值 {@code relatedDocId}。 */
    String RELATED_DOC_ID = "relatedDocId";
/** FieldName 共享常量：doc type，值 {@code relatedDocType}。 */
    String RELATED_DOC_TYPE = "relatedDocType";
/** FieldName 共享常量：REMAKE，值 {@code remake}。 */
    String REMAKE = "remake";
/** FieldName 共享常量：REMARK，值 {@code remark}。 */
    String REMARK = "remark";
/** FieldName 共享常量：flag，值 {@code restrictedFlag}。 */
    String RESTRICTED_FLAG = "restrictedFlag";
    /**
     * 限制用药范围说明正文。
     */
    String RESTRICTED_SCOPE = "restrictedScope";
    /**
     * 限制用药等级或权限枚举编码。
     */
    String RESTRICTED_ENUM = "restrictedEnum";
/** FieldName 共享常量：id，值 {@code requesterId}。 */
    String REQUESTER_ID = "requesterId";
/** FieldName 共享常量：id，值 {@code requestId}。 */
    String REQUEST_ID = "requestId";
    /**
     * 点评时间（处方点评 {@code reviewed_at}）。
     */
    String REVIEWED_AT = "reviewedAt";
    /**
     * 点评人标识（处方点评 {@code reviewer_id}）。
     */
    String REVIEWER_ID = "reviewerId";
/** FieldName 共享常量：id，值 {@code ruleId}。 */
    String RULE_ID = "ruleId";
/** FieldName 共享常量：name，值 {@code ruleName}。 */
    String RULE_NAME = "ruleName";
/** FieldName 共享常量：flag，值 {@code rxFlag}。 */
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
/** FieldName 共享常量：type id，值 {@code serviceTypeId}。 */
    String SERVICE_TYPE_ID = "serviceTypeId";
    /**
     * 上游业务表锚点（收费明细 {@code adm_charge_item.service_table}）。
     */
    String SERVICE_TABLE = "serviceTable";
    /**
     * 上游业务主键锚点（收费明细 {@code adm_charge_item.service_id}）。
     */
    String SERVICE_ID = "serviceId";
    /**
     * 发放表锚点（收费明细 {@code adm_charge_item.dispense_table}）。
     */
    String DISPENSE_TABLE = "dispenseTable";
    /**
     * 发放主键锚点（收费明细 {@code adm_charge_item.dispense_id}）。
     */
    String DISPENSE_ID = "dispenseId";
/** FieldName 共享常量：type name，值 {@code serviceTypeName}。 */
    String SERVICE_TYPE_NAME = "serviceTypeName";
/** FieldName 共享常量：date，值 {@code settleDate}。 */
    String SETTLE_DATE = "settleDate";
/** FieldName 共享常量：SIZE，值 {@code size}。 */
    String SIZE = "size";
/** FieldName 共享常量：test flag，值 {@code skinTestFlag}。 */
    String SKIN_TEST_FLAG = "skinTestFlag";
/** FieldName 共享常量：no，值 {@code slipNo}。 */
    String SLIP_NO = "slipNo";
    /**
     * 排序字段名（动态排序）。
     */
    String SORT_FIELD = "sortField";
    /**
     * 排序方向（动态排序）。
     */
    String SORT_ORDER = "sortOrder";

    /** 诊疗项目适用性别（{@code ActivityDefinition.permittedGenderEnum}）。 */
    String PERMITTED_GENDER_ENUM = "permittedGenderEnum";
/** FieldName 共享常量：location id，值 {@code sourceLocationId}。 */
    String SOURCE_LOCATION_ID = "sourceLocationId";
/** FieldName 共享常量：type enum，值 {@code sourceTypeEnum}。 */
    String SOURCE_TYPE_ENUM = "sourceTypeEnum";
/** FieldName 共享常量：code，值 {@code specialtyCode}。 */
    String SPECIALTY_CODE = "specialtyCode";
/** FieldName 共享常量：code，值 {@code specimenCode}。 */
    String SPECIMEN_CODE = "specimenCode";
/** FieldName 共享常量：unit code，值 {@code splitUnitCode}。 */
    String SPLIT_UNIT_CODE = "splitUnitCode";
/** FieldName 共享常量：price，值 {@code standardPrice}。 */
    String STANDARD_PRICE = "standardPrice";
/** FieldName 共享常量：time，值 {@code startTime}。 */
    String START_TIME = "startTime";
    /**
     * 提交审批时刻（调价单 {@code submit_time}）。
     */
    String SUBMIT_TIME = "submitTime";
    /**
     * 交账头提交时刻（{@code fin_cashier_settlement.submitted_at} / 实体 {@code submittedAt}）。
     */
    String SUBMITTED_AT = "submittedAt";
    /**
     * 制单/申请时刻（调价单 {@code adjustment_time}）。
     */
    String ADJUSTMENT_TIME = "adjustmentTime";
    /**
     * 调价原因（调价单 {@code adjustment_reason}）。
     */
    String ADJUSTMENT_REASON = "adjustmentReason";
    /**
     * 审批通过/生效时刻（调价单 {@code approve_time}）。
     */
    String APPROVE_TIME = "approveTime";
    /**
     * 状态码（prt_template.status / prt_print_event_log.status 等通用列名）。
     */
    String STATUS = "status";
/** FieldName 共享常量：enum，值 {@code statusEnum}。 */
    String STATUS_ENUM = "statusEnum";
    /**
     * 位置形态枚举（就诊位置 {@code adm_encounter_location.form_enum}）。
     */
    String FORM_ENUM = "formEnum";
    /**
     * 业务状态最近变更时刻（发药单读模型 {@code status_changed_time}）。
     */
    String STATUS_CHANGED_TIME = "statusChangedTime";
/** FieldName 共享常量：enum text，值 {@code statusEnumText}。 */
    String STATUS_ENUM_TEXT = "statusEnumText";
/** FieldName 共享常量：unit code，值 {@code stockUnitCode}。 */
    String STOCK_UNIT_CODE = "stockUnitCode";
/** FieldName 共享常量：unit ratio，值 {@code stockUnitRatio}。 */
    String STOCK_UNIT_RATIO = "stockUnitRatio";
/** FieldName 共享常量：id，值 {@code summaryId}。 */
    String SUMMARY_ID = "summaryId";
    /**
     * 快照批次完成时刻（静表 {@code snapshot_time}）。
     */
    String SNAPSHOT_TIME = "snapshotTime";
/** FieldName 共享常量：no，值 {@code summaryNo}。 */
    String SUMMARY_NO = "summaryNo";
/** FieldName 共享常量：id，值 {@code supplierId}。 */
    String SUPPLIER_ID = "supplierId";
    /**
     * 供应或申领单标识。
     */
    String SUPPLY_ID = "supplyId";
    /**
     * 目标定义标识（ChargeItemAutoGen 等配置表 {@code charge_definition_id}）。
     */
    String CHARGE_DEFINITION_ID = "chargeDefinitionId";
    /**
     * 收费项目定义主键（{@code adm_charge_item.definition_id}；与 {@link #CHARGE_DEFINITION_ID} 配置锚点语义不同）。
     */
    String DEFINITION_ID = "definitionId";
    /**
     * 租户标识（多租户行级隔离，与审计基类 {@code tenant_id} 对齐）。
     */
    String TENANT_ID = "tenantId";
    /**
     * 临时建档或临时状态标识。
     */
    String TEMP_FLAG = "tempFlag";
/** FieldName 共享常量：enum，值 {@code therapyEnum}。 */
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
     * 签发时间（三 Request 统一 Java 属性 {@code reqAuthoredTime} / 列 {@code req_authored_time}；仅系统追溯，不作业务排序/展示主轴）。
     */
    String AUTHORED_TIME = "reqAuthoredTime";
    /**
     * 开立时间（三 Request 统一 {@code reqOpenedTime} / 列 {@code req_opened_time}；业务排序/展示/频次主轴）。
     */
    String REQ_OPENED_TIME = "reqOpenedTime";
    /**
     * 停嘱时间（三 Request 统一 {@code stopTime} / 列 {@code stop_time}；住院业务右界）。
     */
    String STOP_TIME = "stopTime";
    /**
     * 医嘱来源类型（SERVICE_REQUEST / MED_REQUEST / DEVICE_REQUEST）。
     */
    String ORDER_TYPE = "orderType";
    /**
     * 第三方部件属性枚举。
     */
    String THO_PART_ATTRIBUTE_ENUM = "thoPartAttributeEnum";
/** FieldName 共享常量：price，值 {@code totalPrice}。 */
    String TOTAL_PRICE = "totalPrice";
    /**
     * 规格量或制剂总量描述（药品 {@code total_volume} 等）。
     */
    String TOTAL_VOLUME = "totalVolume";
/** FieldName 共享常量：no，值 {@code traceNo}。 */
    String TRACE_NO = "traceNo";
    /**
     * 追溯码主档外键（子表 {@code trace_number_id}）。
     */
    String TRACE_NUMBER_ID = "traceNumberId";
    /**
     * 追溯记录类别枚举列（{@code record_category_enum}）。
     */
    String RECORD_CATEGORY_ENUM = "recordCategoryEnum";
    /**
     * 追溯轨迹方向枚举列（{@code record_direction_enum}）。
     */
    String RECORD_DIRECTION_ENUM = "recordDirectionEnum";
    /**
     * 追溯码主档在院状态枚举列。
     */
    String IN_HOSPITAL_STATUS_ENUM = "inHospitalStatusEnum";
    /**
     * 追溯记录操作类型列。
     */
    String OPERATION_TYPE = "operationType";
    /**
     * 追溯记录拆零数量列（写侧换算后落库）。
     */
    String SPLIT_QUANTITY = "splitQuantity";
/** FieldName 共享常量：end time，值 {@code triageEndTime}。 */
    String TRIAGE_END_TIME = "triageEndTime";
/** FieldName 共享常量：id，值 {@code triageId}。 */
    String TRIAGE_ID = "triageId";
/** FieldName 共享常量：level enum，值 {@code triageLevelEnum}。 */
    String TRIAGE_LEVEL_ENUM = "triageLevelEnum";
/** FieldName 共享常量：nurse id，值 {@code triageNurseId}。 */
    String TRIAGE_NURSE_ID = "triageNurseId";
/** FieldName 共享常量：start time，值 {@code triageStartTime}。 */
    String TRIAGE_START_TIME = "triageStartTime";
/** FieldName 共享常量：time，值 {@code triageTime}。 */
    String TRIAGE_TIME = "triageTime";
/** FieldName 共享常量：source id，值 {@code triggerSourceId}。 */
    String TRIGGER_SOURCE_ID = "triggerSourceId";
/** FieldName 共享常量：source table，值 {@code triggerSourceTable}。 */
    String TRIGGER_SOURCE_TABLE = "triggerSourceTable";
/** FieldName 共享常量：code，值 {@code typeCode}。 */
    String TYPE_CODE = "typeCode";
/** FieldName 共享常量：enum，值 {@code typeEnum}。 */
    String TYPE_ENUM = "typeEnum";
    /**
     * 诊疗套餐子项覆盖 JSON 中的执行次数键（前端草稿 {@code childOverridesJson} 约定）。
     */
    String EXECUTION_COUNT = "executionCount";
/** FieldName 共享常量：code，值 {@code unitCode}。 */
    String UNIT_CODE = "unitCode";
/** FieldName 共享常量：end time，值 {@code useEndTime}。 */
    String USE_END_TIME = "useEndTime";
/** FieldName 共享常量：start time，值 {@code useStartTime}。 */
    String USE_START_TIME = "useStartTime";
/** FieldName 共享常量：time，值 {@code useTime}。 */
    String USE_TIME = "useTime";
/** FieldName 共享常量：timing，值 {@code useTiming}。 */
    String USE_TIMING = "useTiming";
/** FieldName 共享常量：id，值 {@code wardId}。 */
    String WARD_ID = "wardId";
    /**
     * 库房主键（快照静表 {@code warehouse_id}，不含货位）。
     */
    String WAREHOUSE_ID = "warehouseId";
/** FieldName 共享常量：name，值 {@code wardName}。 */
    String WARD_NAME = "wardName";
/** FieldName 共享常量：str，值 {@code wbStr}。 */
    String WB_STR = "wbStr";
/** FieldName 共享常量：flag，值 {@code ybFlag}。 */
    String YB_FLAG = "ybFlag";
    /**
     * 医保对码匹配标识。
     */
    String YB_MATCH_FLAG = "ybMatchFlag";
/** FieldName 共享常量：no，值 {@code ybNo}。 */
    String YB_NO = "ybNo";

/** FieldName 共享常量：org no，值 {@code ybOrgNo}。 */
    String YB_ORG_NO = "ybOrgNo";
    /**
     * 目录或事实的来源分类枚举码（如诊断定义 {@code sourceEnum}）。
     */
    String SOURCE_ENUM = "sourceEnum";
    /**
     * 最近更新时间（审计字段，如 {@code AuditedEntityBase#updatedAt}）。
     */
    String UPDATED_AT = "updatedAt";

    /**
     * 组套模板绑定类型（个人/科室/全院）。
     */
    String BINDING_ENUM = "bindingEnum";

    /**
     * 组套模板适用场景（门诊/住院/通用）。
     */
    String SCENE_ENUM = "sceneEnum";

    /**
     * 组套模板种类（GENERAL/TCM/PRICING）。
     */
    String TEMPLATE_KIND_ENUM = "templateKindEnum";

    /**
     * 组套模板来源（MAINTAINED/FROM_ADVICE）。
     */
    String TEMPLATE_SOURCE_ENUM = "templateSourceEnum";

    /**
     * 组套模板头列表排序权重。
     */
    String SORT_WEIGHT = "sortWeight";

    /**
     * 生命体征记录时点。
     */
    String RECORDED_AT = "recordedAt";

    /**
     * 术语体系 code。
     */
    String TERM_CODE = "termCode";

    /**
     * 字典项 code。
     */
    String ITEM_CODE = "itemCode";

/** FieldName 共享常量：value，值 {@code itemValue}。 */
    String ITEM_VALUE = "itemValue";

    /**
     * 生命体征主表 id（明细外键）。
     */
    String VITAL_SIGN_ID = "vitalSignId";

/** FieldName 共享常量：no，值 {@code babyNo}。 */
    String BABY_NO = "babyNo";

    /**
     * 处方号（{@code med_medication_request.prescription_no}）。
     */
    String PRESCRIPTION_NO = "prescriptionNo";

    /**
     * 药品/耗材/诊疗签发时刻（{@code req_authored_time}）。
     */
    String REQ_AUTHORED_TIME = "reqAuthoredTime";

    /**
     * 处方类别编码（术语 355，{@code med_medication_request.rx_type_code}）。
     */
    String RX_TYPE_CODE = "rxTypeCode";
}
