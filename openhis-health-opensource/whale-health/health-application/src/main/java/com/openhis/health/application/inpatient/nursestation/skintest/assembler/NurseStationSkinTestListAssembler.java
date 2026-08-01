/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.assembler;

import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestListAssemblyInput;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestLotOptionDto;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.enums.AllergyCriticality;
import com.openhis.health.domain.share.enums.AllergySeverity;
import com.openhis.health.domain.share.enums.AllergyVerificationStatus;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IF-IP-STK-02 皮试列表行装配（皮试药 × companion SR + 过敏记录 + 发放批号候选）。
 *
 * <p>同一皮试药可挂多条 doctor-prescription 皮试检查 SR，列表一行对应一条 companion（BR-IP-STK-002）。</p>
 */
@Component
public class NurseStationSkinTestListAssembler {

    /**
     * 将批量读侧结果装配为皮试列表行（一行一 companion SR）。
     *
     * @param input 列表装配入参
     * @return 皮试列表行
     */
    public List<NurseStationSkinTestRowDto> assembleRows(NurseStationSkinTestListAssemblyInput input) {
        List<NurseStationSkinTestRowDto> rows = new ArrayList<>();
        if (input == null || input.getSkinTestMedicationRequests() == null) {
            return rows;
        }
        // 遍历已校对皮试药，按其全部 companion SR 展开为多行
        for (MedicationRequestListQueryModel medicationRequest : input.getSkinTestMedicationRequests()) {
            if (medicationRequest == null || medicationRequest.getId() == null) {
                continue;
            }
            List<ServiceRequestListModel> companionServiceRequests =
                this.resolveCompanionServiceRequests(input, medicationRequest.getId());
            if (companionServiceRequests.isEmpty()) {
                continue;
            }
            // 一行一 companion：换批重做可产生多条皮试检查 SR
            for (ServiceRequestListModel skinTestServiceRequest : companionServiceRequests) {
                if (skinTestServiceRequest == null || skinTestServiceRequest.getId() == null) {
                    continue;
                }
                rows.add(this.assembleOneRow(input, medicationRequest, skinTestServiceRequest));
            }
        }
        return rows;
    }

    /**
     * 装配单行：皮试药展示字段 + 指定 companion SR 的执行态/过敏记录/批号候选。
     *
     * @param input                   装配入参
     * @param medicationRequest       皮试药医嘱
     * @param skinTestServiceRequest  companion 皮试检查 SR
     * @return 列表行 DTO
     */
    private NurseStationSkinTestRowDto assembleOneRow(
        NurseStationSkinTestListAssemblyInput input,
        MedicationRequestListQueryModel medicationRequest,
        ServiceRequestListModel skinTestServiceRequest) {
        NurseStationSkinTestRowDto row = new NurseStationSkinTestRowDto();
        row.setEncounterId(input.getEncounterId());
        row.setPatientId(input.getPatientId());
        row.setMedicationRequestId(medicationRequest.getId());
        row.setMedicationId(medicationRequest.getMedicationId());
        if (medicationRequest.getMedicationId() != null && input.getMedicationNameById() != null) {
            row.setMedicationName(input.getMedicationNameById().get(medicationRequest.getMedicationId()));
        }
        row.setOpenDoctorId(medicationRequest.getRequesterId());
        if (medicationRequest.getRequesterId() != null && input.getPractitionerNameById() != null) {
            row.setOpenDoctorName(input.getPractitionerNameById().get(medicationRequest.getRequesterId()));
        }

        Long performLocationId = null;
        if (input.getLocationIdByMedicationRequestId() != null) {
            performLocationId = input.getLocationIdByMedicationRequestId().get(medicationRequest.getId());
        }
        row.setPerformLocationId(performLocationId);

        row.setSkinTestServiceRequestId(skinTestServiceRequest.getId());
        if (input.getExecutedBySkinTestServiceRequestId() != null) {
            Boolean executed =
                input.getExecutedBySkinTestServiceRequestId().get(skinTestServiceRequest.getId());
            row.setExecutedFlag(Boolean.TRUE.equals(executed));
        } else {
            row.setExecutedFlag(Boolean.FALSE);
        }

        AllergyIntoleranceRecordQueryModel allergy = null;
        if (input.getAllergyBySkinTestServiceRequestId() != null) {
            allergy = input.getAllergyBySkinTestServiceRequestId().get(skinTestServiceRequest.getId());
        }
        if (allergy != null) {
            // 已有过敏记录：回填结果并标记只读（BR-IP-STK-003）
            row.setAllergyRecordId(allergy.getId());
            row.setReadOnlyFlag(Boolean.TRUE);
            row.setLotNumber(allergy.getLotNumber());
            row.setRecordedDate(allergy.getRecordedDate());
            row.setClinicalStatusCode(allergy.getClinicalStatusEnum());
            row.setClinicalStatusText(this.resolveEnumDisplay(ClinicalStatus.class, allergy.getClinicalStatusEnum()));
            row.setVerificationStatusCode(allergy.getVerificationStatusEnum());
            row.setVerificationStatusText(
                this.resolveEnumDisplay(AllergyVerificationStatus.class, allergy.getVerificationStatusEnum()));
            row.setCriticalityCode(allergy.getCriticalityEnum());
            row.setCriticalityText(this.resolveEnumDisplay(AllergyCriticality.class, allergy.getCriticalityEnum()));
            row.setSeverityCode(allergy.getSeverityEnum());
            row.setSeverityText(this.resolveEnumDisplay(AllergySeverity.class, allergy.getSeverityEnum()));
            row.setPractitionerId(allergy.getPractitionerId());
            row.setCheckPractitionerId(allergy.getCheckPractitionerId());
            row.setDescription(allergy.getDescription());
            if (input.getPractitionerNameById() != null) {
                if (allergy.getPractitionerId() != null) {
                    row.setPractitionerName(input.getPractitionerNameById().get(allergy.getPractitionerId()));
                }
                if (allergy.getCheckPractitionerId() != null) {
                    row.setCheckPractitionerName(
                        input.getPractitionerNameById().get(allergy.getCheckPractitionerId()));
                }
            }
        } else {
            row.setReadOnlyFlag(Boolean.FALSE);
        }

        // 批号候选按皮试药医嘱挂载（同一药多行共享发放批号）
        if (input.getLotOptionsByMedicationRequestId() != null) {
            List<NurseStationSkinTestLotOptionDto> lotOptions =
                input.getLotOptionsByMedicationRequestId().get(medicationRequest.getId());
            if (lotOptions != null) {
                row.setLotNumberOptions(new ArrayList<>(lotOptions));
            }
        }
        return row;
    }

    /**
     * 解析皮试药下全部 doctor-prescription companion SR；无索引时返回空列表。
     *
     * @param input               装配入参
     * @param medicationRequestId 皮试药医嘱 id
     * @return companion SR 列表（不可变空列表或原列表）
     */
    private List<ServiceRequestListModel> resolveCompanionServiceRequests(
        NurseStationSkinTestListAssemblyInput input,
        Long medicationRequestId) {
        if (input.getDoctorPrescriptionSkinTestSrByMedicationRequestId() == null) {
            return Collections.emptyList();
        }
        List<ServiceRequestListModel> companions =
            input.getDoctorPrescriptionSkinTestSrByMedicationRequestId().get(medicationRequestId);
        if (companions == null) {
            return Collections.emptyList();
        }
        return companions;
    }

    /**
     * 枚举 code 转展示文案。
     *
     * @param enumClass 枚举类型
     * @param code      持久化 code
     * @param <E>       枚举泛型
     * @return 展示文案或 null
     */
    private <E extends Enum<E> & EnumInterface> String resolveEnumDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDisplay();
    }
}
