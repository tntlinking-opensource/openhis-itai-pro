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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationAutoRollWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollChargeLineResolved;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollEncounterProcessResult;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationAutoRollMapper;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientAutoRollEncounterQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenActiveRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.openhis.health.domain.custom.chargeitemautogen.service.ChargeItemAutoGenQueryService;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IF-IP-ARG-08：单 encounter 住院滚费事务处理器（Request + Charge 成对落库）。
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
     * @param ctx               滚费就诊上下文
     * @param billDate          业务日
     * @param operatorId        任务操作人
     * @param defaultRequesterId 缺省开立人（床位规则）
     * @return 本 encounter 计数
     */
    public AutoRollEncounterProcessResult processEncounter(
        InpatientAutoRollEncounterQueryModel ctx,
        LocalDate billDate,
        Long operatorId,
        Long defaultRequesterId) {
        AutoRollEncounterProcessResult result = new AutoRollEncounterProcessResult();
        if (ctx == null || ctx.getEncounterId() == null) {
            return result;
        }
        // Pre：规则创建日索引，供 BR-IP-ARG-013 过滤 billDate 之前未生效的配置
        Map<Long, LocalDateTime> createdAtByRuleId = this.loadCreatedAtByRuleId(ctx.getEncounterId());
        List<ChargeItemAutoGenActiveRuleQueryModel> activeRules =
            chargeItemAutoGenQueryService.findActiveRulesByEncounter(ctx.getEncounterId(), ctx.getOrganizationId());
        // Loop：床位服务 ACTIVE 配置逐条落账（与护理分支串行、非互斥）
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
            // Pre：同日同 definition 幂等（BR-IP-ARG-010）
            if (chargeItemQueryService.existsAutoRollChargeItemForBillDate(
                ctx.getEncounterId(), rule.getChargeDefinitionId(), billDate)) {
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
            AutoRollLineContext line = this.buildLineContext(
                ctx,
                billDate,
                rule.getTriggerSourceId(),
                rule.getChargeDefinitionId(),
                rule.getQuantityValue(),
                priceLine,
                ServiceRequestCategoryConstants.SERVICE_CATEGORY_BED_SERVICE,
                defaultRequesterId);
            if (this.postAutoRollLine(line, operatorId, result)) {
                result.setProcessedEncounter(true);
            }
        }
        // Pre：护理级别分支（与配置表无关，BR-IP-ARG-009）
        Optional<InpatientAutoRollNursingLevelQueryModel> nursing =
            serviceRequestQueryService.findLastEffectiveNursingLevelForAutoRoll(ctx.getEncounterId(), billDate);
        if (nursing.isPresent()) {
            InpatientAutoRollNursingLevelQueryModel nursingModel = nursing.get();
            Long nursingChargeDefinitionId =
                nurseStationAutoRollWriteAssembler.resolveNursingChargeDefinitionId(nursingModel.getActivityId());
            // 护理 activity 无法反查有效定价 → 记失败，不阻断床位已落账行
            if (nursingChargeDefinitionId == null) {
                result.setFailedCount(result.getFailedCount() + 1);
            } else if (chargeItemQueryService.existsAutoRollChargeItemForBillDate(
                ctx.getEncounterId(), nursingChargeDefinitionId, billDate)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
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
                    Long requesterId = nursingModel.getRequesterId();
                    // 护理医嘱缺开嘱人时回退任务缺省开嘱人
                    if (requesterId == null) {
                        requesterId = defaultRequesterId;
                    }
                    AutoRollLineContext line = this.buildLineContext(
                        ctx,
                        billDate,
                        nursingModel.getActivityId(),
                        nursingChargeDefinitionId,
                        nursingQty,
                        priceLine,
                        ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING,
                        requesterId);
                    if (this.postAutoRollLine(line, operatorId, result)) {
                        result.setProcessedEncounter(true);
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
     * 组装滚费落账行。
     *
     * @param ctx                 就诊上下文
     * @param billDate            业务日
     * @param activityId          活动 id
     * @param chargeDefinitionId  定价 id
     * @param quantity            数量
     * @param priceLine           定价解析结果
     * @param categoryEnum        诊疗类目
     * @param requesterId         开立人
     * @return 落账行
     */
    private AutoRollLineContext buildLineContext(
        InpatientAutoRollEncounterQueryModel ctx,
        LocalDate billDate,
        Long activityId,
        Long chargeDefinitionId,
        BigDecimal quantity,
        AutoRollChargeLineResolved priceLine,
        String categoryEnum,
        Long requesterId) {
        AutoRollLineContext line = new AutoRollLineContext();
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
        line.setUnitCode(priceLine.getUnitCode());
        line.setCategoryEnum(categoryEnum);
        line.setRequesterId(requesterId);
        line.setBillDate(billDate);
        line.setOccurrenceTime(billDate.atStartOfDay());
        line.setUnitPrice(priceLine.getUnitPrice());
        line.setTotalPrice(priceLine.getTotalPrice());
        return line;
    }

    /**
     * 单条滚费落账（Request + Charge）。
     *
     * @param line       落账行
     * @param operatorId 操作人
     * @param result     累计计数器
     * @return 落账成功时 true
     */
    private boolean postAutoRollLine(
        AutoRollLineContext line,
        Long operatorId,
        AutoRollEncounterProcessResult result) {
        boolean posted = nurseStationAutoRollLinePoster.postLine(line, operatorId);
        if (posted) {
            result.setCreatedChargeItemCount(result.getCreatedChargeItemCount() + 1);
            return true;
        }
        result.setFailedCount(result.getFailedCount() + 1);
        return false;
    }
}
