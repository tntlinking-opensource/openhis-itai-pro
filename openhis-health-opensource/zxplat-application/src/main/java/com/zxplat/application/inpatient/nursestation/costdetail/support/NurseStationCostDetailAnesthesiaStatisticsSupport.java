/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.costdetail.support;

import com.zxplat.application.shared.nursestation.InpatientNurseStationAnesthesiaApplyScopeSupport;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 费用明细「麻醉统计」筛选：麻醉申请已执行费用 + 手麻计费挂麻醉申请父项目的划价费用。
 *
 * <p>识别口径：麻醉申请父锚点 = 就诊下方号 AA 前缀的诊疗/药品申请；
 * 手麻计费子行 = based_on 指向上述父锚点的护士划价诊疗/耗材/药品请求。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationCostDetailAnesthesiaStatisticsSupport {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final InpatientNurseStationAnesthesiaApplyScopeSupport anesthesiaApplyScopeSupport;

    /**
     * 按麻醉统计开关收窄费用行。
     *
     * @param rows                      已进窗收费行
     * @param encounterId               当前就诊
     * @param anesthesiaStatisticsOnly  true=仅麻醉统计口径；false/空=不过滤
     * @return 收窄后的行（新列表）；未开启时原样返回
     */
    public List<InpatientNurseStationCostDetailChargeRow> filterChargeRowsByAnesthesiaStatistics(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Long encounterId,
        Boolean anesthesiaStatisticsOnly) {
        // 未勾选：保持全量费用口径
        if (!Boolean.TRUE.equals(anesthesiaStatisticsOnly)) {
            if (rows == null) {
                return new ArrayList<>();
            }
            return rows;
        }
        if (rows == null || rows.isEmpty() || encounterId == null) {
            return new ArrayList<>();
        }
        Set<Long> eligibleServiceIds = this.resolveEligibleServiceIds(encounterId);
        // 无麻醉申请父项：统计结果为空
        if (eligibleServiceIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<InpatientNurseStationCostDetailChargeRow> kept = new ArrayList<>();
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            if (charge == null) {
                continue;
            }
            // 新落账：麻醉执行 / 麻醉划价来源码直接命中
            String generateSource = charge.getGenerateSourceEnum();
            if (GenerateSource.ANESTHESIA_EXECUTE.getCode().equals(generateSource)
                || GenerateSource.ANESTHESIA_PRICING.getCode().equals(generateSource)) {
                kept.add(row);
                continue;
            }
            // 方号已落 AA：直接命中麻醉申请侧费用（存量兼容）
            if (anesthesiaApplyScopeSupport.matchesAnesthesiaApply(charge.getPrescriptionNo())) {
                kept.add(row);
                continue;
            }
            Long serviceId = charge.getServiceId();
            if (serviceId != null && eligibleServiceIds.contains(serviceId)) {
                kept.add(row);
            }
        }
        return kept;
    }

    /**
     * 解析麻醉统计可展示的 service_id 集合（父申请 + based_on 子划价请求）。
     *
     * @param encounterId 就诊主键
     * @return 可命中的上游业务主键集合
     */
    private Set<Long> resolveEligibleServiceIds(Long encounterId) {
        LinkedHashSet<Long> anesthServiceRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> anesthMedicationRequestIds = new LinkedHashSet<>();
        // Pre：就诊下全部 SR，按 AA 方号收麻醉申请父项
        List<ServiceRequest> serviceRequests =
            serviceRequestQueryService.listByEncounterIds(Collections.singletonList(encounterId));
        for (ServiceRequest serviceRequest : serviceRequests) {
            if (serviceRequest == null || serviceRequest.getId() == null) {
                continue;
            }
            if (anesthesiaApplyScopeSupport.matchesAnesthesiaApply(serviceRequest.getPrescriptionNo())) {
                anesthServiceRequestIds.add(serviceRequest.getId());
            }
        }
        // Pre：就诊下药嘱，按 AA 方号收麻醉申请药品父项
        List<MedicationRequestListQueryModel> medicationRequests =
            medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
        for (MedicationRequestListQueryModel medicationRequest : medicationRequests) {
            if (medicationRequest == null || medicationRequest.getId() == null) {
                continue;
            }
            if (anesthesiaApplyScopeSupport.matchesAnesthesiaApply(medicationRequest.getPrescriptionNo())) {
                anesthMedicationRequestIds.add(medicationRequest.getId());
            }
        }
        LinkedHashSet<Long> eligible = new LinkedHashSet<>();
        eligible.addAll(anesthServiceRequestIds);
        eligible.addAll(anesthMedicationRequestIds);
        // Pre：手麻计费挂父——based_on 指向麻醉申请父项的子请求
        this.appendBasedOnChildServiceIds(
            eligible, TableNameConstants.WKF_SERVICE_REQUEST, anesthServiceRequestIds);
        this.appendBasedOnChildServiceIds(
            eligible, TableNameConstants.MED_MEDICATION_REQUEST, anesthMedicationRequestIds);
        return eligible;
    }

    /**
     * 追加 based_on 挂靠指定父表/父主键集合的子诊疗、耗材、药嘱主键。
     *
     * @param eligible       结果集合（就地追加）
     * @param basedOnTable   父申请物理表
     * @param basedOnIds     父申请主键集合
     */
    private void appendBasedOnChildServiceIds(
        Set<Long> eligible,
        String basedOnTable,
        Set<Long> basedOnIds) {
        if (eligible == null || StringUtils.isEmpty(basedOnTable) || basedOnIds == null || basedOnIds.isEmpty()) {
            return;
        }
        HashSet<Long> distinctParentIds = new HashSet<>();
        for (Long basedOnId : basedOnIds) {
            if (basedOnId != null) {
                distinctParentIds.add(basedOnId);
            }
        }
        if (distinctParentIds.isEmpty()) {
            return;
        }
        // 诊疗划价子请求
        List<ServiceRequest> childServices =
            serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(basedOnTable, distinctParentIds);
        for (ServiceRequest child : childServices) {
            if (child != null && child.getId() != null) {
                eligible.add(child.getId());
            }
        }
        // 耗材划价子请求
        List<DeviceRequestListModel> childDevices =
            deviceRequestQueryService.listDeviceRequestsByBasedOnAnchors(basedOnTable, distinctParentIds);
        for (DeviceRequestListModel child : childDevices) {
            if (child != null && child.getId() != null) {
                eligible.add(child.getId());
            }
        }
        // 药品划价子请求（若存在挂麻醉申请父项的药嘱补费）
        List<MedicationRequest> childMedications =
            medicationRequestQueryService.listByBasedOnAnchors(basedOnTable, distinctParentIds);
        for (MedicationRequest child : childMedications) {
            if (child != null && child.getId() != null) {
                eligible.add(child.getId());
            }
        }
    }
}
