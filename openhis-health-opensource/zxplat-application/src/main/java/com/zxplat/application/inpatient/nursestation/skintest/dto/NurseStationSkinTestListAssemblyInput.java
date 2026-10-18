/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.dto;

import com.zxplat.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
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

    /**
     * doctor-prescription 皮试检查 SR：皮试药医嘱 id → companion SR 列表（一行一 SR，BR-IP-STK-002）。
     */
    private Map<Long, List<ServiceRequestListModel>> doctorPrescriptionSkinTestSrByMedicationRequestId;

    /** 过敏记录：皮试检查 SR id → 记录（request_id 锚定 SR）。 */
    private Map<Long, AllergyIntoleranceRecordQueryModel> allergyBySkinTestServiceRequestId;

    /** 皮试药医嘱 id → 发放意向库房 locationId（行上 performLocationId 展示）。 */
    private Map<Long, Long> locationIdByMedicationRequestId;

    /** 药品定义 id → 展示名称。 */
    private Map<Long, String> medicationNameById;

    /**
     * 批号候选：皮试药医嘱 id → 选项列表（MedicationDispense 去重 lot_number，BR-IP-STK-013）。
     */
    private Map<Long, List<NurseStationSkinTestLotOptionDto>> lotOptionsByMedicationRequestId;

    /** 医务人员 id → 展示名称（开立医生、双签护士）。 */
    private Map<Long, String> practitionerNameById;

    /** 皮试检查 SR id → 是否已执行（executedFlag 来源）。 */
    private Map<Long, Boolean> executedBySkinTestServiceRequestId;
}
