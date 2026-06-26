/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.dto;

import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * IF-IP-STK-02 列表装配入参：皮试药、doctor-prescription 皮试检查 SR、过敏记录、批号与人员展示名批量读侧结果。
 */
@Getter
@Setter
public class NurseStationSkinTestListAssemblyInput {

    /** 就诊主键（卡片上下文）。 */
    private Long encounterId;

    /** 患者主键（卡片上下文）。 */
    private Long patientId;

    /** 已校对皮试药医嘱行（BR-IP-STK-002 过滤后，且须存在 doctor 皮试检查 SR）。 */
    private List<MedicationRequestListQueryModel> skinTestMedicationRequests;

    /** doctor-prescription 皮试检查 SR：皮试药医嘱 id → SR 列表模型（IF-IP-STK-02 索引口径）。 */
    private Map<Long, ServiceRequestListModel> doctorPrescriptionSkinTestSrByMedicationRequestId;

    /** 过敏记录：皮试检查 SR id → 记录（request_id 锚定 SR）。 */
    private Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId;

    /** 皮试药医嘱 id → 发放意向库房 locationId。 */
    private Map<Long, Long> locationIdByMedicationRequestId;

    /** 药品定义 id → 展示名称。 */
    private Map<Long, String> medicationNameById;

    /** 批号候选：{@code medicationId|locationId} → 选项列表。 */
    private Map<String, List<NurseStationSkinTestLotOptionDto>> lotOptionsByMedLocationKey;

    /** 医务人员 id → 展示名称（开立医生、双签护士）。 */
    private Map<Long, String> practitionerNameById;

    /** 皮试检查 SR id → 是否已存在费用行（pricedFlag 来源）。 */
    private Map<Long, Boolean> pricedBySkinTestServiceRequestId;
}
