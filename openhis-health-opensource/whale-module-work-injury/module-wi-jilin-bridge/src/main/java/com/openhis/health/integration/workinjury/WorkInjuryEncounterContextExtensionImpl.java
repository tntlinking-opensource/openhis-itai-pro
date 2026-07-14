/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.workinjury;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.wi.gateway.scheme.shared.model.WiQualificationStatus;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryEncounterContextExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryEncounterContextQueryExtensionIn;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryEncounterContextExtensionOut;

/**
 * 吉林工伤就诊上下文 SPI 实现（医生站角标 / 2301 按钮门控）。
 *
 * <p><b>SPI</b>：{@link WorkInjuryEncounterContextExtension}</p>
 * <p><b>Health 调用方</b>：医生站、就诊列表</p>
 * <p><b>数据源</b>：{@code yb_wi_encounter_anchor}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryEncounterContextExtensionImpl implements WorkInjuryEncounterContextExtension {

    private final YbWiEncounterAnchorRepository encounterAnchorRepository;

    /** 解析 anchor 审批状态与是否需 2301。 */
    @Override
    public WorkInjuryEncounterContextExtensionOut resolveContext(WorkInjuryEncounterContextQueryExtensionIn query) {
        WorkInjuryEncounterContextExtensionOut context = new WorkInjuryEncounterContextExtensionOut()
                .setWorkInjuryEncounter(false)
                .setQualificationValid(false);
        if (query == null || query.getEncounterId() == null) {
            return context;
        }
        return encounterAnchorRepository.findByEncounterId(query.getEncounterId())
                .map(this::toEncounterContext)
                .orElse(context);
    }

    /** anchor → 医生站角标 / 2301 门控 Extension DTO。 */
    private WorkInjuryEncounterContextExtensionOut toEncounterContext(YbWiEncounterAnchor anchor) {
        boolean workInjury = isNotEmpty(anchor.getPsnNo());
        boolean valid = WiQualificationStatus.VALID.equals(anchor.getQualificationStatus())
                && isNotEmpty(anchor.getQualificationId());
        return new WorkInjuryEncounterContextExtensionOut()
                .setWorkInjuryEncounter(workInjury)
                .setQualificationValid(valid)
                .setApprovalStatusLabel(resolveApprovalLabel(anchor))
                .setContractId(anchor.getContractId())
                .setNeeds2301Upload(workInjury && !valid);
    }

    /** qualification_status → UI 展示文案。 */
    private String resolveApprovalLabel(YbWiEncounterAnchor anchor) {
        if (WiQualificationStatus.VALID.equals(anchor.getQualificationStatus())) {
            return "审批有效";
        }
        if (WiQualificationStatus.PENDING_9102.equals(anchor.getQualificationStatus())) {
            return "待9102确认";
        }
        if (isNotEmpty(anchor.getQualificationId())) {
            return "已选审批";
        }
        return "未审批";
    }
}
