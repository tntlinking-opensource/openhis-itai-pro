/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollChargeLineResolved;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollConfigRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IF-IP-ARG-02：自动计费配置列表与护理只读区装配（AS-IP-NUR-013 §3.3）。
 */
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollListAssembler {

    private final NurseStationAutoRollWriteAssembler nurseStationAutoRollWriteAssembler;

    /**
     * 装配配置列表行（含 INACTIVE；展示名来自批量预取的 activity/定价 Map）。
     *
     * @param rules           规则读模型
     * @param activityById    床位服务定义
     * @param definitionById  定价定义
     * @param organizationId  机构主键（定价择优上下文；可为 null）
     * @return 列表 DTO
     */
    public List<NurseStationAutoRollConfigRowDto> assembleConfigRows(
        List<ChargeItemAutoGenRuleQueryModel> rules,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, ChargeItemDefinition> definitionById,
        Long organizationId) {
        List<NurseStationAutoRollConfigRowDto> rows = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            return rows;
        }
        for (ChargeItemAutoGenRuleQueryModel rule : rules) {
            if (rule == null) {
                continue;
            }
            NurseStationAutoRollConfigRowDto dto = new NurseStationAutoRollConfigRowDto();
            dto.setRuleId(rule.getId());
            dto.setRuleName(rule.getRuleName());
            dto.setStatusEnum(rule.getStatusEnum());
            dto.setStatusDictText(this.resolvePublicationDisplay(rule.getStatusEnum()));
            dto.setEncounterId(rule.getEncounterId());
            dto.setTriggerSourceTable(rule.getTriggerSourceTable());
            dto.setTriggerSourceId(rule.getTriggerSourceId());
            dto.setQuantityValue(rule.getQuantityValue());
            dto.setPerformingOrgId(rule.getPerformingOrgId());
            dto.setPriority(rule.getPriority());
            dto.setRemark(rule.getRemark());
            dto.setChargeDefinitionId(rule.getChargeDefinitionId());
            // Pre：展示名来自批量 Map，避免列表 N+1
            ActivityDefinition activity = activityById.get(rule.getTriggerSourceId());
            if (activity != null) {
                dto.setActivityName(activity.getName());
            }
            ChargeItemDefinition definition = definitionById.get(rule.getChargeDefinitionId());
            if (definition != null) {
                dto.setChargeDefinitionName(definition.getChargeName());
            }
            // Asm：读侧解析单价（与滚费写路径同口径；仅展示参考）
            AutoRollChargeLineResolved priceResolved = nurseStationAutoRollWriteAssembler.resolveAutoRollChargeLine(
                rule.getChargeDefinitionId(),
                rule.getTriggerSourceId(),
                rule.getQuantityValue(),
                organizationId);
            if (priceResolved.isResolved()) {
                dto.setUnitPrice(priceResolved.getUnitPrice());
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 装配护理级别只读区（BR-IP-ARG-005：只读，不入配置表）。
     *
     * @param nursing          护理级别读模型
     * @param billDate         业务日
     * @param activityById     活动定义
     * @param definitionById   护理分支解析出的定价（App 传入单项 Map，key=chargeDefinitionId）
     * @param organizationId   机构主键（护理定价择优上下文；可为 null）
     * @return 只读 DTO；无有效护理时为 null
     */
    public NurseStationAutoRollNursingReadonlyDto assembleNursingReadonly(
        Optional<InpatientAutoRollNursingLevelQueryModel> nursing,
        LocalDate billDate,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, ChargeItemDefinition> definitionById,
        Long organizationId) {
        if (nursing == null || nursing.isEmpty()) {
            return null;
        }
        InpatientAutoRollNursingLevelQueryModel model = nursing.get();
        NurseStationAutoRollNursingReadonlyDto dto = new NurseStationAutoRollNursingReadonlyDto();
        dto.setServiceRequestId(model.getServiceRequestId());
        dto.setActivityId(model.getActivityId());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setCheckTime(model.getCheckTime());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusDictText(this.resolveRequestStatusDisplay(model.getStatusEnum()));
        dto.setBillDate(billDate);
        ActivityDefinition activity = activityById.get(model.getActivityId());
        if (activity != null) {
            dto.setActivityName(activity.getName());
            if (StringUtils.isNotEmpty(model.getUnitCode())) {
                dto.setUnitName(model.getUnitCode());
            } else if (StringUtils.isNotEmpty(activity.getPackageUnitCode())) {
                dto.setUnitName(activity.getPackageUnitCode());
            }
        }
        // Asm：护理定价展示取自 App 预解析的单项 map（非配置表字段）
        Long nursingChargeDefinitionId = null;
        if (definitionById != null && !definitionById.isEmpty()) {
            for (Map.Entry<Long, ChargeItemDefinition> entry : definitionById.entrySet()) {
                ChargeItemDefinition definition = entry.getValue();
                if (definition != null) {
                    nursingChargeDefinitionId = definition.getId();
                    dto.setChargeDefinitionId(definition.getId());
                    dto.setChargeDefinitionName(definition.getChargeName());
                }
                break;
            }
        }
        // Asm：读侧解析护理单价（与滚费写路径同口径；仅展示参考）
        if (nursingChargeDefinitionId != null) {
            BigDecimal nursingQty = model.getQuantity();
            if (nursingQty == null || nursingQty.compareTo(BigDecimal.ZERO) <= 0) {
                nursingQty = BigDecimal.ONE;
            }
            AutoRollChargeLineResolved priceResolved = nurseStationAutoRollWriteAssembler.resolveAutoRollChargeLine(
                nursingChargeDefinitionId,
                model.getActivityId(),
                nursingQty,
                organizationId);
            if (priceResolved.isResolved()) {
                dto.setUnitPrice(priceResolved.getUnitPrice());
            } else if (definitionById != null) {
                // 读侧展示：解析链未命中时回落定价行 price_value
                ChargeItemDefinition definition = definitionById.get(nursingChargeDefinitionId);
                if (definition != null && definition.getPriceValue() != null) {
                    dto.setUnitPrice(definition.getPriceValue());
                }
            }
        }
        return dto;
    }

    /**
     * 发布状态 code → 展示名。
     *
     * @param code 持久化码
     * @return 展示名
     */
    private String resolvePublicationDisplay(String code) {
        if (StringUtils.isEmpty(code)) {
            return code;
        }
        for (PublicationStatus status : PublicationStatus.values()) {
            if (code.equals(status.getCode())) {
                return status.getDisplay();
            }
        }
        return code;
    }

    /**
     * 医嘱状态 code → 展示名。
     *
     * @param code 持久化码
     * @return 展示名
     */
    private String resolveRequestStatusDisplay(String code) {
        if (StringUtils.isEmpty(code)) {
            return code;
        }
        for (RequestStatus status : RequestStatus.values()) {
            if (code.equals(status.getCode())) {
                return status.getDisplay();
            }
        }
        return code;
    }
}
