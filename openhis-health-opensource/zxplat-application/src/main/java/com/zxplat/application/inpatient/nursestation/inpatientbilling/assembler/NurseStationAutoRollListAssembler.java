/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollConfigRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollPostedChargeRowDto;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.custom.chargeitemautogen.queries.model.ChargeItemAutoGenRuleQueryModel;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.servicerequest.queries.model.InpatientAutoRollNursingLevelQueryModel;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IF-IP-ARG-02/09：自动计费配置列表、护理只读区与已滚费用行装配（AS-IP-NUR-013）。
 *
 * <p>列表单价仅用批量预取的 {@code ChargeItemDefinition.priceValue}，禁止按行再查价。</p>
 */
@Component
public class NurseStationAutoRollListAssembler {

    /**
     * 装配配置列表行（含 INACTIVE；展示名来自批量预取的 activity/定价 Map）。
     *
     * @param rules                         规则读模型
     * @param activityById                  床位服务定义
     * @param definitionById                定价定义
     * @param organizationId                机构主键（兼容入参；列表展示不再二次择优）
     * @param billingPractitionerNameById   计费人姓名（可空 Map）
     * @return 列表 DTO
     */
    public List<NurseStationAutoRollConfigRowDto> assembleConfigRows(
        List<ChargeItemAutoGenRuleQueryModel> rules,
        Map<Long, ActivityDefinition> activityById,
        Map<Long, ChargeItemDefinition> definitionById,
        Long organizationId,
        Map<Long, String> billingPractitionerNameById) {
        List<NurseStationAutoRollConfigRowDto> rows = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            return rows;
        }
        Map<Long, String> nameById = billingPractitionerNameById;
        if (nameById == null) {
            nameById = Map.of();
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
            dto.setBusinessTime(rule.getBusinessTime());
            dto.setBillingPractitionerId(rule.getBillingPractitionerId());
            if (rule.getBillingPractitionerId() != null) {
                dto.setBillingPractitionerName(nameById.get(rule.getBillingPractitionerId()));
            }
            dto.setChargeDefinitionId(rule.getChargeDefinitionId());
            // Pre：展示名来自批量 Map，避免列表 N+1
            ActivityDefinition activity = activityById.get(rule.getTriggerSourceId());
            if (activity != null) {
                dto.setActivityName(activity.getName());
            }
            ChargeItemDefinition definition = definitionById.get(rule.getChargeDefinitionId());
            if (definition != null) {
                dto.setChargeDefinitionName(definition.getChargeName());
                // Asm：列表展示单价取批量预取的定价行，禁止再走 resolveAutoRollChargeLine（避免按行查库）
                if (definition.getPriceValue() != null) {
                    dto.setUnitPrice(definition.getPriceValue());
                }
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
     * @param organizationId   机构主键（兼容入参；列表展示不再二次择优）
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
            // 单位展示名暂不解析字典；禁止把 unitCode 字典码塞进 unitName（前端勿拼接）
            if (StringUtils.isNotEmpty(model.getUnitCode())) {
                dto.setUnitCode(model.getUnitCode());
            } else if (StringUtils.isNotEmpty(activity.getPackageUnitCode())) {
                dto.setUnitCode(activity.getPackageUnitCode());
            }
        }
        // Asm：护理定价展示取自 App 预解析的单项 map（列表禁止再 resolveAutoRollChargeLine）
        if (definitionById != null && !definitionById.isEmpty()) {
            for (Map.Entry<Long, ChargeItemDefinition> entry : definitionById.entrySet()) {
                ChargeItemDefinition definition = entry.getValue();
                // 取 map 中首个可用定价行作为本护理行展示价
                if (definition == null) {
                    continue;
                }
                dto.setChargeDefinitionId(definition.getId());
                dto.setChargeDefinitionName(definition.getChargeName());
                if (definition.getPriceValue() != null) {
                    dto.setUnitPrice(definition.getPriceValue());
                }
                break;
            }
        }
        return dto;
    }

    /**
     * 装配床位配置已滚费用行（IF-IP-ARG-09；取消执行锚点取 procedureId）。
     *
     * @param charges     order-execute 收费行（已按 occurrence 倒序）
     * @param encounterId 就诊主键；非空时过滤跨就诊脏数据
     * @return 展开列表 DTO
     */
    public List<NurseStationAutoRollPostedChargeRowDto> assemblePostedChargeRows(
        List<ChargeItem> charges,
        Long encounterId) {
        List<NurseStationAutoRollPostedChargeRowDto> rows = new ArrayList<>();
        if (charges == null || charges.isEmpty()) {
            return rows;
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            // 子项账单不进滚费已入账列表（只展示父项）
            if (charge.getParentId() != null) {
                continue;
            }
            // 仅本就诊费用；防止 SR 复用误挂其它就诊
            if (encounterId != null
                && charge.getEncounterId() != null
                && !encounterId.equals(charge.getEncounterId())) {
                continue;
            }
            NurseStationAutoRollPostedChargeRowDto dto = new NurseStationAutoRollPostedChargeRowDto();
            dto.setChargeItemId(charge.getId());
            dto.setProcedureId(charge.getProcedureId());
            dto.setOccurrenceTime(charge.getOccurrenceTime());
            dto.setQuantityValue(charge.getQuantityValue());
            dto.setQuantityUnit(charge.getQuantityUnit());
            dto.setUnitPrice(charge.getUnitPrice());
            dto.setTotalPrice(charge.getTotalPrice());
            dto.setStatusEnum(charge.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, charge.getStatusEnum());
            if (status != null) {
                dto.setStatusDisplay(status.getDisplay());
            } else {
                dto.setStatusDisplay(charge.getStatusEnum());
            }
            // 取消执行写路径仅回退待结算 order-execute；无 procedure 不可发起
            boolean cancelable = charge.getProcedureId() != null
                && pendingCode.equals(charge.getStatusEnum());
            dto.setCancelable(Boolean.valueOf(cancelable));
            rows.add(dto);
        }
        return rows;
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
