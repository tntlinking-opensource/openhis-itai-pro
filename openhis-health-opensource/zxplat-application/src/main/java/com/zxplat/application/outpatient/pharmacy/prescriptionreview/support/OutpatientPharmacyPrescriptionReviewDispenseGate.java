/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.support;

import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionReviewConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊处方点评已发药 Gate（只读药嘱/发放；不写发放）。
 *
 * <p>判定口径：处方号 → 已签发药品申请行 id 集合 → 与 COMPLETED 发放反查 id 求交；
 * 方内至少一行命中即视为已发药。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionReviewDispenseGate {

    /** 已签发门诊药品行读侧。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 发放完成态反查读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * Pre：断言入参处方均已发药；任一方无 COMPLETED 命中则抛 EX-PRV-201。
     *
     * @param prescriptionNos 处方号列表
     * @throws BusinessException EX-PRV-201 未发药；EX-PRV-200 号为空
     */
    public void assertDispensedOutpatientPrescriptions(List<String> prescriptionNos) {
        // 空列表无法判定发药态
        if (prescriptionNos == null || prescriptionNos.isEmpty()) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        // Pre：一次性拉取 COMPLETED 发放对应的药品申请 id，避免按方重复全量扫描
        List<Long> completedMedReqIds =
            this.medicationDispenseQueryService.listDistinctMedicationRequestIdsByStatusEnum(
                DispenseStatus.COMPLETED.getCode());
        Set<Long> completedIdSet = new HashSet<>();
        // 发放侧可能返回 null：按空集合处理，后续各方均判未发药
        if (completedMedReqIds != null) {
            completedIdSet.addAll(completedMedReqIds);
        }
        for (String prescriptionNo : prescriptionNos) {
            // 空白号无法判定发药态
            if (StringUtils.isBlank(prescriptionNo)) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                    "请检查点评入参");
            }
            List<Long> medReqIds = this.listMedicationRequestIdsByPrescriptionNo(prescriptionNo.trim());
            boolean dispensed = false;
            for (Long medReqId : medReqIds) {
                // 方内任一行已 COMPLETED 即视为该方已发药
                if (medReqId != null && completedIdSet.contains(medReqId)) {
                    dispensed = true;
                    break;
                }
            }
            // 无已签发药品行或无 COMPLETED 交集 → 拒绝点评
            if (!dispensed) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_201,
                    "仅已发药处方可点评");
            }
        }
    }

    /**
     * 按处方号拉取已签发药品申请主键列表。
     *
     * @param prescriptionNo 已 trim 的处方号
     * @return 药品申请 id 列表；无行时为空列表
     */
    private List<Long> listMedicationRequestIdsByPrescriptionNo(String prescriptionNo) {
        Page<MedicationRequestListQueryModel> page = this.pageSignedLinesByPrescriptionNo(prescriptionNo);
        List<Long> ids = new ArrayList<>();
        for (MedicationRequestListQueryModel line : page.getContent()) {
            // 空行或无主键：跳过，不参与发药求交
            if (line == null || line.getId() == null) {
                continue;
            }
            ids.add(line.getId());
        }
        return ids;
    }

    /**
     * 按处方号精确匹配分页拉取门诊已签发药品行。
     *
     * <p>{@code OutpatientSignedPrescriptionDrugLinePageQuery} 无 outpatientAmbOnly 字段；
     * 本 Gate 仅设 prescriptionNo + 页窗，依赖领域方法已签发门诊口径。</p>
     *
     * @param prescriptionNo 已 trim 的处方号
     * @return 药品行分页
     */
    private Page<MedicationRequestListQueryModel> pageSignedLinesByPrescriptionNo(String prescriptionNo) {
        OutpatientSignedPrescriptionDrugLinePageQuery query = new OutpatientSignedPrescriptionDrugLinePageQuery();
        query.setPrescriptionNo(prescriptionNo);
        query.setPageNo(1);
        query.setPageSize(OutpatientPharmacyPrescriptionReviewConstants.GATE_PRESCRIPTION_LINE_PAGE_SIZE);
        return this.medicationRequestQueryService.pageOutpatientSignedPrescriptionDrugLines(query);
    }
}
