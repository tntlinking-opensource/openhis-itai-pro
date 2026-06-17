/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.assembler;

import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestListAssemblyInput;
import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestLotOptionDto;
import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.share.enums.AllergyVerificationStatus;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-OP-STK-02 皮试列表行装配（SR + MR + Allergy + 批号候选 + 人员展示名）。
 */
@Component
public class OutpatientSkinTestListAssembler {

    /**
     * 将批量读侧结果装配为皮试列表行。
     *
     * @param input 列表装配入参
     * @return 皮试列表行
     */
    public List<OutpatientSkinTestRowDto> assembleRows(OutpatientSkinTestListAssemblyInput input) {
        List<OutpatientSkinTestRowDto> rows = new ArrayList<>();
        if (input == null || input.getSkinTestServiceRequests() == null) {
            return rows;
        }
        for (ServiceRequestListModel serviceRequest : input.getSkinTestServiceRequests()) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            OutpatientSkinTestRowDto row = new OutpatientSkinTestRowDto();
            row.setEncounterId(input.getEncounterId());
            row.setPatientId(input.getPatientId());
            row.setServiceRequestId(serviceRequest.getId());
            row.setFeeSettledFlag(Boolean.TRUE);

            Long medicationRequestId = serviceRequest.getBasedOnId();
            row.setMedicationRequestId(medicationRequestId);

            MedicationRequest medicationRequest = null;
            if (input.getMedicationRequestById() != null && medicationRequestId != null) {
                medicationRequest = input.getMedicationRequestById().get(medicationRequestId);
            }
            if (medicationRequest != null) {
                row.setMedicationId(medicationRequest.getMedicationId());
                if (medicationRequest.getMedicationId() != null
                    && input.getMedicationNameById() != null) {
                    row.setMedicationName(input.getMedicationNameById().get(medicationRequest.getMedicationId()));
                }
                row.setOpenDoctorId(medicationRequest.getRequesterId());
                if (medicationRequest.getRequesterId() != null
                    && input.getPractitionerNameById() != null) {
                    row.setOpenDoctorName(input.getPractitionerNameById().get(medicationRequest.getRequesterId()));
                }
            }

            Long performLocationId = null;
            if (input.getLocationIdByMedicationRequestId() != null && medicationRequestId != null) {
                performLocationId = input.getLocationIdByMedicationRequestId().get(medicationRequestId);
            }
            row.setPerformLocationId(performLocationId);

            AllergyIntoleranceRecordQueryModel allergy = null;
            if (input.getAllergyByServiceRequestId() != null) {
                allergy = input.getAllergyByServiceRequestId().get(serviceRequest.getId());
            }
            if (allergy != null) {
                row.setAllergyRecordId(allergy.getId());
                row.setLotNumber(allergy.getLotNumber());
                row.setRecordedDate(allergy.getRecordedDate());
                row.setClinicalStatusCode(allergy.getClinicalStatusEnum());
                row.setClinicalStatusText(this.resolveEnumDisplay(ClinicalStatus.class, allergy.getClinicalStatusEnum()));
                row.setVerificationStatusCode(allergy.getVerificationStatusEnum());
                row.setVerificationStatusText(
                    this.resolveEnumDisplay(AllergyVerificationStatus.class, allergy.getVerificationStatusEnum()));
                row.setPractitionerId(allergy.getPractitionerId());
                row.setCheckPractitionerId(allergy.getCheckPractitionerId());
                row.setDescription(allergy.getDescription());
                if (input.getPractitionerNameById() != null) {
                    if (allergy.getPractitionerId() != null) {
                        row.setPractitionerName(input.getPractitionerNameById().get(allergy.getPractitionerId()));
                    }
                    if (allergy.getCheckPractitionerId() != null) {
                        row.setCheckPractitionerName(input.getPractitionerNameById().get(allergy.getCheckPractitionerId()));
                    }
                }
            }

            if (row.getMedicationId() != null && performLocationId != null
                && input.getLotOptionsByMedLocationKey() != null) {
                String lotKey = this.buildMedLocationKey(row.getMedicationId(), performLocationId);
                List<OutpatientSkinTestLotOptionDto> lotOptions = input.getLotOptionsByMedLocationKey().get(lotKey);
                if (lotOptions != null) {
                    row.setLotNumberOptions(new ArrayList<>(lotOptions));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private String buildMedLocationKey(Long medicationId, Long locationId) {
        return medicationId + "|" + locationId;
    }

    private <E extends Enum<E> & EnumInterface> String resolveEnumDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDisplay();
    }
}
