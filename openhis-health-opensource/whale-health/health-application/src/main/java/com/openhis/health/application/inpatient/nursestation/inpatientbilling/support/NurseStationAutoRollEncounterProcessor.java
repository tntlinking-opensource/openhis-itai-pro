/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationAutoRollWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollChargeLineResolved;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollEncounterProcessResult;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientAutoRollEncounterQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenActiveRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.service.ChargeItemAutoGenQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IF-IP-ARG-08：单 encounter 住院滚费处理器（长期医嘱执行轨 IF-IP-EXE-02，非划价轨）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollEncounterProcessor {

    private final ChargeItemAutoGenQueryService chargeItemAutoGenQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final NurseStationAutoRollWriteAssembler nurseStationAutoRollWriteAssembler;
    private final NurseStationAutoRollLinePoster nurseStationAutoRollLinePoster;

    /**
     * 处理单个在院 encounter 的滚费（床位配置 + 护理分支）。
     *
     * @param ctx         滚费就诊上下文
     * @param billDate    业务日
     * @param operatorId  任务操作人
     * @param performerId 主执行人（责任护士）
     * @return 本 encounter 计数
     */
    public AutoRollEncounterProcessResult processEncounter(
        InpatientAutoRollEncounterQueryModel ctx,
        LocalDate billDate,
        Long operatorId,
        Long performerId) {
        AutoRollEncounterProcessResult result = new AutoRollEncounterProcessResult();
        if (ctx == null || ctx.getEncounterId() == null) {
            return result;
        }
        // Pre：规则创建日索引，供 BR-IP-ARG-013 过滤 billDate 之前未生效的配置
        Map<Long, LocalDateTime> createdAtByRuleId = this.loadCreatedAtByRuleId(ctx.getEncounterId());
        List<ChargeItemAutoGenActiveRuleQueryModel> activeRules =
            chargeItemAutoGenQueryService.findActiveRulesByEncounter(ctx.getEncounterId(), ctx.getOrganizationId());
        if (activeRules.isEmpty()) {
            log.debug(
                "住院滚费无激活床位配置 encounterId={} organizationId={}",
                ctx.getEncounterId(),
                ctx.getOrganizationId());
        }
        // Loop：床位服务 ACTIVE 配置逐条执行长期 SR（与护理分支串行、非互斥）
        for (ChargeItemAutoGenActiveRuleQueryModel rule : activeRules) {
            if (rule == null || rule.getChargeDefinitionId() == null) {
                continue;
            }
            LocalDateTime createdAt = createdAtByRuleId.get(rule.getId());
            // Gate：配置创建日晚于 billDate 则本规则不参与（BR-IP-ARG-013）
            if (createdAt != null && createdAt.toLocalDate().isAfter(billDate)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            // Pre：仅查 IF-IP-ARG-03 新建配置时已 ensure 的长期床位 SR；缺失计失败，任务侧不补建
            Optional<Long> bedServiceRequestId =
                serviceRequestQueryService.findAutoRollBedServiceRequestIdByRuleId(rule.getId());
            if (bedServiceRequestId.isEmpty()) {
                log.warn(
                    "住院滚费跳过：配置缺少长期床位 SR ruleId={} encounterId={}",
                    rule.getId(),
                    ctx.getEncounterId());
                result.setFailedCount(result.getFailedCount() + 1);
                continue;
            }
            Long serviceRequestId = bedServiceRequestId.get();
            // Pre：同日同 SR 幂等（BR-IP-ARG-010）
            if (chargeItemQueryService.existsOrderExecuteChargeForServiceRequestBillDate(
                serviceRequestId, billDate)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            AutoRollChargeLineResolved priceLine = nurseStationAutoRollWriteAssembler.resolveAutoRollChargeLine(
                rule.getChargeDefinitionId(),
                rule.getTriggerSourceId(),
                rule.getQuantityValue(),
                ctx.getOrganizationId());
            if (!priceLine.isResolved()) {
                result.setFailedCount(result.getFailedCount() + 1);
                continue;
            }
            AutoRollLineContext line = this.buildExecutionLineContext(
                ctx,
                billDate,
                serviceRequestId,
                rule.getTriggerSourceId(),
                rule.getChargeDefinitionId(),
                rule.getQuantityValue(),
                priceLine.getUnitCode(),
                rule.getBusinessTime());
            // 配置计费人优先；存量空则回落责任护士/任务操作人（BR-IP-ARG-019）
            Long bedPerformerId = rule.getBillingPractitionerId();
            if (bedPerformerId == null) {
                bedPerformerId = performerId;
            }
            // Charge.performer_id 取自 operatorId，故计费人同时写入两边
            if (this.postAutoRollLine(line, bedPerformerId, bedPerformerId, result)) {
                result.setProcessedEncounter(true);
            }
        }
        // Pre：护理级别分支（与配置表无关，BR-IP-ARG-009）
        Optional<InpatientAutoRollNursingLevelQueryModel> nursing =
            serviceRequestQueryService.findLastEffectiveNursingLevelForAutoRoll(ctx.getEncounterId(), billDate);
        if (nursing.isPresent()) {
            InpatientAutoRollNursingLevelQueryModel nursingModel = nursing.get();
            Long nursingServiceRequestId = nursingModel.getServiceRequestId();
            if (nursingServiceRequestId == null) {
                result.setFailedCount(result.getFailedCount() + 1);
            } else if (chargeItemQueryService.existsOrderExecuteChargeForServiceRequestBillDate(
                nursingServiceRequestId, billDate)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
            } else {
                Long nursingChargeDefinitionId =
                    nurseStationAutoRollWriteAssembler.resolveNursingChargeDefinitionId(nursingModel.getActivityId());
                if (nursingChargeDefinitionId == null) {
                    result.setFailedCount(result.getFailedCount() + 1);
                } else {
                    BigDecimal nursingQty = nursingModel.getQuantity();
                    if (nursingQty == null || nursingQty.compareTo(BigDecimal.ZERO) <= 0) {
                        nursingQty = BigDecimal.ONE;
                    }
                    AutoRollChargeLineResolved priceLine = nurseStationAutoRollWriteAssembler.resolveAutoRollChargeLine(
                        nursingChargeDefinitionId,
                        nursingModel.getActivityId(),
                        nursingQty,
                        ctx.getOrganizationId());
                    if (!priceLine.isResolved()) {
                        result.setFailedCount(result.getFailedCount() + 1);
                    } else {
                        AutoRollLineContext line = this.buildExecutionLineContext(
                            ctx,
                            billDate,
                            nursingServiceRequestId,
                            nursingModel.getActivityId(),
                            nursingChargeDefinitionId,
                            nursingQty,
                            nursingModel.getUnitCode(),
                            null);
                        // 护理分支：时刻写死日末；计费人仍用责任护士/任务操作人
                        if (this.postAutoRollLine(line, operatorId, performerId, result)) {
                            result.setProcessedEncounter(true);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 加载规则创建时刻索引（BR-IP-ARG-013 创建日过滤）。
     *
     * @param encounterId 就诊 id
     * @return ruleId → createdAt
     */
    private Map<Long, LocalDateTime> loadCreatedAtByRuleId(Long encounterId) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        List<ChargeItemAutoGenRuleQueryModel> allRules =
            chargeItemAutoGenQueryService.findRulesByEncounterId(encounterId);
        for (ChargeItemAutoGenRuleQueryModel row : allRules) {
            if (row == null || row.getId() == null) {
                continue;
            }
            map.put(row.getId(), row.getCreatedAt());
        }
        return map;
    }

    /**
     * 组装滚费执行行（锚定已有长期 SR）。
     *
     * @param ctx                就诊上下文
     * @param billDate           业务日
     * @param serviceRequestId   长期 SR
     * @param activityId         活动 id
     * @param chargeDefinitionId 定价 id
     * @param quantity           数量
     * @param unitCode           单位
     * @param businessTime       配置业务时刻；空则日末（护理写死）
     * @return 执行行
     */
    private AutoRollLineContext buildExecutionLineContext(
        InpatientAutoRollEncounterQueryModel ctx,
        LocalDate billDate,
        Long serviceRequestId,
        Long activityId,
        Long chargeDefinitionId,
        BigDecimal quantity,
        String unitCode,
        LocalTime businessTime) {
        AutoRollLineContext line = new AutoRollLineContext();
        line.setServiceRequestId(serviceRequestId);
        line.setEncounterId(ctx.getEncounterId());
        line.setPatientId(ctx.getPatientId());
        line.setAccountId(ctx.getAccountId());
        line.setOrganizationId(ctx.getOrganizationId());
        line.setActivityId(activityId);
        line.setChargeDefinitionId(chargeDefinitionId);
        BigDecimal qty = quantity;
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = BigDecimal.ONE;
        }
        line.setQuantity(qty);
        line.setUnitCode(unitCode);
        line.setBillDate(billDate);
        LocalTime clock = businessTime;
        // 护理或未配时刻：日末，避免 00:00:00 早于入院
        if (clock == null) {
            clock = InpatientNurseStationConstants.DAY_END_CLOCK;
        }
        line.setOccurrenceTime(billDate.atTime(clock));
        return line;
    }

    /**
     * 单条滚费执行落账。
     *
     * @param line        执行行
     * @param operatorId  操作人
     * @param performerId 主执行人
     * @param result      累计计数器
     * @return 新建 Charge 成功时 true
     */
    private boolean postAutoRollLine(
        AutoRollLineContext line,
        Long operatorId,
        Long performerId,
        AutoRollEncounterProcessResult result) {
        InpatientAutoRollOrderExecutionOrchestrator.Outcome outcome =
            nurseStationAutoRollLinePoster.postLine(line, operatorId, performerId);
        if (outcome == InpatientAutoRollOrderExecutionOrchestrator.Outcome.CREATED) {
            result.setCreatedChargeItemCount(result.getCreatedChargeItemCount() + 1);
            return true;
        }
        if (outcome == InpatientAutoRollOrderExecutionOrchestrator.Outcome.SKIPPED) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return false;
        }
        log.warn(
            "住院滚费落账失败 encounterId={} serviceRequestId={} chargeDefinitionId={} billDate={}",
            line.getEncounterId(),
            line.getServiceRequestId(),
            line.getChargeDefinitionId(),
            line.getBillDate());
        result.setFailedCount(result.getFailedCount() + 1);
        return false;
    }
}
