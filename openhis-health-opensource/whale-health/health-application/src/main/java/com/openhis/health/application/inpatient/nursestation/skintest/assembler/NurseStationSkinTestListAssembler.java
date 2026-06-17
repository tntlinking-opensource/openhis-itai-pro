/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.assembler;

import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestListAssemblyInput;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestLotOptionDto;
import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
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
 * IF-IP-STK-02 皮试列表行装配（皮试药 + nurse-pricing SR + 过敏记录 + 批号候选）。
 */
@Component
public class NurseStationSkinTestListAssembler {

    /**
     * 将批量读侧结果装配为皮试列表行。
     *
     * @param input 列表装配入参
     * @return 皮试列表行
     */
    public List<NurseStationSkinTestRowDto> assembleRows(NurseStationSkinTestListAssemblyInput input) {
        List<NurseStationSkinTestRowDto> rows = new ArrayList<>();
        if (input == null || input.getSkinTestMedicationRequests() == null) {
            return rows;
        }
        for (MedicationRequestListQueryModel medicationRequest : input.getSkinTestMedicationRequests()) {
            if (medicationRequest == null || medicationRequest.getId() == null) {
                continue;
            }
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

            ServiceRequestListModel skinTestServiceRequest = null;
            if (input.getNursePricingSkinTestSrByMedicationRequestId() != null) {
                skinTestServiceRequest =
                    input.getNursePricingSkinTestSrByMedicationRequestId().get(medicationRequest.getId());
            }
            if (skinTestServiceRequest != null) {
                row.setSkinTestServiceRequestId(skinTestServiceRequest.getId());
                if (input.getPricedBySkinTestServiceRequestId() != null
                    && skinTestServiceRequest.getId() != null) {
                    Boolean priced = input.getPricedBySkinTestServiceRequestId().get(skinTestServiceRequest.getId());
                    row.setPricedFlag(priced);
                }
            } else {
                row.setPricedFlag(Boolean.FALSE);
            }

            AllergyIntoleranceRecordQueryModel allergy = null;
            if (skinTestServiceRequest != null && skinTestServiceRequest.getId() != null
                && input.getAllergyBySkinTestServiceRequestId() != null) {
                allergy = input.getAllergyBySkinTestServiceRequestId().get(skinTestServiceRequest.getId());
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
                        row.setCheckPractitionerName(
                            input.getPractitionerNameById().get(allergy.getCheckPractitionerId()));
                    }
                }
            }

            if (row.getMedicationId() != null && performLocationId != null
                && input.getLotOptionsByMedLocationKey() != null) {
                String lotKey = this.buildMedLocationKey(row.getMedicationId(), performLocationId);
                List<NurseStationSkinTestLotOptionDto> lotOptions = input.getLotOptionsByMedLocationKey().get(lotKey);
                if (lotOptions != null) {
                    row.setLotNumberOptions(new ArrayList<>(lotOptions));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 药品与库房复合键（与库存批号候选索引一致）。
     *
     * @param medicationId 药品 id
     * @param locationId   库房 id
     * @return 复合键
     */
    private String buildMedLocationKey(Long medicationId, Long locationId) {
        return medicationId + "|" + locationId;
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
