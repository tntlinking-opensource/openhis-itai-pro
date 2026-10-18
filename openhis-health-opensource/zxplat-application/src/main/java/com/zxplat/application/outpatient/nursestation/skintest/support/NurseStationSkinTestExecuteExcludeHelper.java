/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.support;

import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;

/**
 * 护士站「执行」Tab 与皮试 Tab 互斥口径（<b>仅门诊</b> AS-OP-NUR-001 §1.7）。
 *
 * <p><b>仅门诊</b>：doctor-prescription「皮试检查」companion {@code ServiceRequest} 不进 IF-NUR-02
 * 执行列表（操作费由门诊皮试 Tab 登记时计费）。</p>
 * <p><b>住院已去特殊化</b>（AS-IP-NUR-011 v1.4 / AS-IP-NUR-004）：皮试检查进普通临嘱执行，住院调用方
 * <b>不再</b>使用本 Helper 的 service 排除。</p>
 * <p><b>皮试药</b>{@code MedicationRequest}（{@code skinTestFlag=true}）门诊/住院均须进待执行列表。</p>
 */
public final class NurseStationSkinTestExecuteExcludeHelper {

    private NurseStationSkinTestExecuteExcludeHelper() {
    }

    /**
     * 是否为 doctor-prescription 联动的「皮试检查」诊疗行（字段口径，供实体/读模型复用）。
     *
     * @param skinTestActivityId 皮试检查活动定义主键（{@code ActivityDefinition.name=皮试检查}）
     * @param activityId         诊疗活动定义主键
     * @param generateSourceEnum 生成来源 code
     * @param basedOnTable       based_on 表名
     * @param basedOnId          based_on 主键
     * @return true 表示须从执行列表/待执行计数剔除
     */
    public static boolean isDoctorPrescriptionSkinTestInspectionService(
        Long skinTestActivityId,
        Long activityId,
        String generateSourceEnum,
        String basedOnTable,
        Long basedOnId) {
        if (skinTestActivityId == null || activityId == null) {
            return false;
        }
        // 仅「皮试检查」活动定义命中。
        if (!skinTestActivityId.equals(activityId)) {
            return false;
        }
        // 仅医生开立联动行；划价等来源不走皮试 Tab 互斥。
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(generateSourceEnum)) {
            return false;
        }
        // companion SR 必须挂原皮试药医嘱。
        if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(basedOnTable)) {
            return false;
        }
        return basedOnId != null;
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
        if (serviceRequest == null) {
            return false;
        }
        return isDoctorPrescriptionSkinTestInspectionService(
            skinTestActivityId,
            serviceRequest.getActivityId(),
            serviceRequest.getGenerateSourceEnum(),
            serviceRequest.getBasedOnTable(),
            serviceRequest.getBasedOnId());
    }

    /**
     * 是否为 doctor-prescription 皮试药医嘱行（字段口径；供识别/展示，<b>不</b>用于执行列表互斥）。
     *
     * @param skinTestFlag       皮试药标记
     * @param generateSourceEnum 生成来源 code
     * @return true 表示为医生开立的皮试药
     */
    public static boolean isDoctorPrescriptionSkinTestMedication(
        Boolean skinTestFlag,
        String generateSourceEnum) {
        // 非皮试药。
        if (!Boolean.TRUE.equals(skinTestFlag)) {
            return false;
        }
        return GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(generateSourceEnum);
    }

    /**
     * 是否为 doctor-prescription 皮试药医嘱行。
     *
     * @param medicationRequest 药品申请列表行
     * @return true 表示为医生开立的皮试药
     */
    public static boolean isDoctorPrescriptionSkinTestMedication(
        MedicationRequestListQueryModel medicationRequest) {
        if (medicationRequest == null) {
            return false;
        }
        return isDoctorPrescriptionSkinTestMedication(
            medicationRequest.getSkinTestFlag(),
            medicationRequest.getGenerateSourceEnum());
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
     * 执行列表是否排除该药品行。
     *
     * <p>皮试药须进待执行（发药/执行）；恒返回 {@code false}。保留方法签名以免门诊/住院调用方漂移。</p>
     *
     * @param medicationRequest 药品申请列表行
     * @return 恒为 false
     */
    public static boolean shouldExcludeMedicationFromNurseStationExecuteList(
        MedicationRequestListQueryModel medicationRequest) {
        return false;
    }
}
