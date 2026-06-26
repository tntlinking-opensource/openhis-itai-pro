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

package com.openhis.health.module.querysupport.nursestation;

import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;

/**
 * 护士站「执行」Tab 与皮试 Tab 互斥口径（AS-OP-NUR-001 §1.7.4 / AS-IP-NUR-011）。
 *
 * <p>doctor-prescription 皮试检查 SR 与皮试药医嘱仅由皮试 Tab 承载，不进 IF-NUR-02 / IF-IP-EXE-01 执行列表。</p>
 */
public final class NurseStationSkinTestExecuteExcludeSupport {

    private NurseStationSkinTestExecuteExcludeSupport() {
    }

    /**
     * 是否为 doctor-prescription 联动的「皮试检查」诊疗行。
     *
     * @param skinTestActivityId 皮试检查活动定义主键（{@code ActivityDefinition.name=皮试检查}）
     * @param serviceRequest     诊疗申请列表行
     * @return true 表示须从执行列表剔除
     */
    public static boolean isDoctorPrescriptionSkinTestInspectionService(
        Long skinTestActivityId,
        ServiceRequestListModel serviceRequest) {
        if (serviceRequest == null || skinTestActivityId == null) {
            return false;
        }
        if (!skinTestActivityId.equals(serviceRequest.getActivityId())) {
            return false;
        }
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(serviceRequest.getGenerateSourceEnum())) {
            return false;
        }
        if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceRequest.getBasedOnTable())) {
            return false;
        }
        return serviceRequest.getBasedOnId() != null;
    }

    /**
     * 是否为 doctor-prescription 皮试药医嘱行。
     *
     * @param medicationRequest 药品申请列表行
     * @return true 表示须从执行列表剔除
     */
    public static boolean isDoctorPrescriptionSkinTestMedication(
        MedicationRequestListQueryModel medicationRequest) {
        if (medicationRequest == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(medicationRequest.getSkinTestFlag())) {
            return false;
        }
        return GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(medicationRequest.getGenerateSourceEnum());
    }

    /**
     * 执行列表是否排除该诊疗行（皮试检查 companion SR）。
     *
     * @param skinTestActivityId 皮试检查活动定义主键
     * @param serviceRequest     诊疗申请列表行
     * @return true 表示不进入护士站执行 Tab
     */
    public static boolean shouldExcludeServiceFromNurseStationExecuteList(
        Long skinTestActivityId,
        ServiceRequestListModel serviceRequest) {
        return isDoctorPrescriptionSkinTestInspectionService(skinTestActivityId, serviceRequest);
    }

    /**
     * 执行列表是否排除该药品行（皮试药）。
     *
     * @param medicationRequest 药品申请列表行
     * @return true 表示不进入护士站执行 Tab
     */
    public static boolean shouldExcludeMedicationFromNurseStationExecuteList(
        MedicationRequestListQueryModel medicationRequest) {
        return isDoctorPrescriptionSkinTestMedication(medicationRequest);
    }
}
