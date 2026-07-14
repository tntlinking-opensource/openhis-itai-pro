/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.assembler;

import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentQueueRowDto;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.EncounterPendingTraceAssignmentQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.PatientTraceAssignmentAggregateLineQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.PatientTraceAssignmentDispenseLineQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesQuery;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站追溯码分配列表装配（**AS-IP-NUR-015 · IF-IP-TRC-01/02/03**）。
 */
@Component
public class PatientTraceAssignmentListAssembler {

    /**
     * 待办队列行装配：域队列 + 就诊上下文回填展示字段。
     *
     * @param pendingRows   域待办行
     * @param contextByEncounterId 就诊上下文索引
     * @return 队列 DTO 列表
     */
    public List<PatientTraceAssignmentQueueRowDto> buildQueueRows(
        List<EncounterPendingTraceAssignmentQueryModel> pendingRows,
        Map<Long, InpatientEncounterContextQueryModel> contextByEncounterId) {
        List<PatientTraceAssignmentQueueRowDto> rows = new ArrayList<>();
        if (pendingRows == null) {
            return rows;
        }
        for (EncounterPendingTraceAssignmentQueryModel pendingRow : pendingRows) {
            if (pendingRow == null) {
                continue;
            }
            PatientTraceAssignmentQueueRowDto dto = new PatientTraceAssignmentQueueRowDto();
            dto.setEncounterId(pendingRow.getEncounterId());
            dto.setPatientId(pendingRow.getPatientId());
            dto.setPendingGroupCount(pendingRow.getPendingGroupCount());
            InpatientEncounterContextQueryModel context = null;
            if (contextByEncounterId != null && pendingRow.getEncounterId() != null) {
                context = contextByEncounterId.get(pendingRow.getEncounterId());
            }
            if (context != null) {
                if (context.getPatientId() != null) {
                    dto.setPatientId(context.getPatientId());
                }
                dto.setPatientName(context.getPatientName());
                dto.setBedName(context.getBedName());
                dto.setEncounterBusNo(context.getEncounterBusNo());
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 患者药批汇总组列表装配（**BR-IP-TRC-004** 拆零累加 + 码池摘要）。
     *
     * @param aggregateLines  域汇总行
     * @param productByMedKey 药品展示索引
     * @param poolBySummary   summaryNo → 码池
     * @param poolAssembler   码池摘要工具
     * @return 汇总组 DTO 列表
     */
    public List<PatientTraceAssignmentGroupDto> buildGroupDtos(
        List<PatientTraceAssignmentAggregateLineQueryModel> aggregateLines,
        Map<String, ProductDefinitionDisplayDto> productByMedKey,
        Map<String, Set<String>> poolBySummary,
        SummaryTracePoolAssembler poolAssembler) {
        List<PatientTraceAssignmentGroupDto> groups = new ArrayList<>();
        if (aggregateLines == null) {
            return groups;
        }
        for (PatientTraceAssignmentAggregateLineQueryModel line : aggregateLines) {
            if (line == null) {
                continue;
            }
            PatientTraceAssignmentGroupDto dto = new PatientTraceAssignmentGroupDto();
            dto.setGroupKey(line.getGroupKey());
            dto.setSummaryNo(line.getSummaryNo());
            dto.setMedicationId(line.getMedicationId());
            dto.setLotNumber(line.getLotNumber());
            dto.setDispenseIds(line.getDispenseIds());
            dto.setAssignedTraceNo(line.getAssignedTraceNo());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
            ProductDefinitionDisplayDto product = null;
            if (productByMedKey != null) {
                product = productByMedKey.get(productKey);
            }
            if (product != null) {
                dto.setMedicationName(product.getProductName());
                dto.setSpecification(this.resolveSpecificationText(product));
                dto.setSplitUnitCode(product.getSplitUnitCode());
                dto.setSplitUnitCode_dictText(product.getSplitUnitCodeDictText());
            }
            BigDecimal splitQuantity = this.sumDispenseQuantitySplit(line.getDispenseLines(), product);
            dto.setDispenseQuantitySplit(splitQuantity);
            dto.setMaxTraceCodeCount(this.resolveMaxTraceCodeCount(splitQuantity));
            // 组内仍有底层行 trace 为空则标记待分配，否则只读展示已分配
            if (Boolean.TRUE.equals(line.getAssignmentPending())) {
                dto.setAssignmentStatus(InpatientNurseStationConstants.TRACE_ASSIGNMENT_STATUS_PENDING);
                dto.setAssignmentStatus_enumText(InpatientNurseStationConstants.TRACE_ASSIGNMENT_STATUS_PENDING_DISPLAY);
            } else {
                dto.setAssignmentStatus(InpatientNurseStationConstants.TRACE_ASSIGNMENT_STATUS_ASSIGNED);
                dto.setAssignmentStatus_enumText(InpatientNurseStationConstants.TRACE_ASSIGNMENT_STATUS_ASSIGNED_DISPLAY);
            }
            Set<String> pool = null;
            if (poolBySummary != null && line.getSummaryNo() != null) {
                pool = poolBySummary.get(line.getSummaryNo());
            }
            dto.setTracePoolPreview(poolAssembler.buildTracePoolPreview(pool));
            if (pool != null && !pool.isEmpty()) {
                dto.setTracePoolCodes(new ArrayList<>(pool));
            }
            groups.add(dto);
        }
        return groups;
    }

    /**
     * 将汇总组 DTO 转为追溯解析组快照。
     *
     * @param groups 汇总组 DTO
     * @return 组快照列表
     */
    public List<ResolveTraceCodesQuery.GroupSnapshot> toGroupSnapshots(List<PatientTraceAssignmentGroupDto> groups) {
        List<ResolveTraceCodesQuery.GroupSnapshot> snapshots = new ArrayList<>();
        if (groups == null) {
            return snapshots;
        }
        for (PatientTraceAssignmentGroupDto group : groups) {
            if (group == null || StringUtils.isEmpty(group.getGroupKey())) {
                continue;
            }
            ResolveTraceCodesQuery.GroupSnapshot snapshot = new ResolveTraceCodesQuery.GroupSnapshot();
            snapshot.setGroupKey(group.getGroupKey());
            snapshot.setSummaryNo(group.getSummaryNo());
            snapshot.setMedicationId(group.getMedicationId());
            snapshot.setLotNumber(group.getLotNumber());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    /**
     * 按 groupKeys 过滤汇总组 DTO（IF-03 组快照收窄）。
     *
     * @param allGroups 全量组
     * @param groupKeys 请求组键
     * @return 过滤后的组
     */
    public List<PatientTraceAssignmentGroupDto> filterGroupsByKeys(
        List<PatientTraceAssignmentGroupDto> allGroups,
        List<String> groupKeys) {
        List<PatientTraceAssignmentGroupDto> filtered = new ArrayList<>();
        if (allGroups == null || groupKeys == null || groupKeys.isEmpty()) {
            return filtered;
        }
        Map<String, PatientTraceAssignmentGroupDto> groupByKey = new HashMap<>();
        for (PatientTraceAssignmentGroupDto group : allGroups) {
            if (group != null && StringUtils.isNotEmpty(group.getGroupKey())) {
                groupByKey.put(group.getGroupKey(), group);
            }
        }
        for (String groupKey : groupKeys) {
            if (StringUtils.isEmpty(groupKey)) {
                continue;
            }
            PatientTraceAssignmentGroupDto group = groupByKey.get(groupKey.trim());
            if (group != null) {
                filtered.add(group);
            }
        }
        return filtered;
    }

    /**
     * 组内底层发放行数量按拆零单位累加。
     */
    private BigDecimal sumDispenseQuantitySplit(
        List<PatientTraceAssignmentDispenseLineQueryModel> dispenseLines,
        ProductDefinitionDisplayDto product) {
        BigDecimal total = BigDecimal.ZERO;
        if (dispenseLines == null || dispenseLines.isEmpty()) {
            return total;
        }
        for (PatientTraceAssignmentDispenseLineQueryModel line : dispenseLines) {
            if (line == null || line.getQuantity() == null) {
                continue;
            }
            if (product == null) {
                total = total.add(line.getQuantity());
                continue;
            }
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(product),
                line.getUnitCode(),
                line.getQuantity());
            total = total.add(splitQty);
        }
        return total;
    }

    /**
     * 允许最大追溯码数：拆零发药量向上取整，至少 1（**BR-IP-TRC-008**）。
     */
    private Integer resolveMaxTraceCodeCount(BigDecimal splitQuantity) {
        if (splitQuantity == null || splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return 1;
        }
        int maxCount = splitQuantity.setScale(0, RoundingMode.CEILING).intValue();
        if (maxCount < 1) {
            maxCount = 1;
        }
        return maxCount;
    }

    /**
     * 药品规格展示回落。
     */
    private String resolveSpecificationText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(product.getTotalVolume())) {
            return product.getTotalVolume();
        }
        return product.getMerchandiseName();
    }
}
