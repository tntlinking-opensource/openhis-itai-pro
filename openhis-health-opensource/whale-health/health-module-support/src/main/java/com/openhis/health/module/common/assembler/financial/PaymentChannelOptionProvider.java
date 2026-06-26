/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.assembler.financial;

import com.openhis.health.domain.share.constants.PaymentChannelConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收银支付渠道下拉：{@link PaymentChannel} 用户可选 ∩ 477 active ∩（SCAN_PAY 时）第三方收单开关。
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelOptionProvider {

    private final CodeSystemQueryService codeSystemQueryService;
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /**
     * 加载用户可选支付渠道（含 UI 专用 {@link PaymentChannel#SCAN_PAY}）。
     */
    public List<OptionDto> loadUserSelectableOptions() {
        Map<String, ConceptView> activeByCode = loadActivePayEnumConcepts();
        List<OptionDto> options = new ArrayList<>();
        for (PaymentChannel channel : PaymentChannel.values()) {
            if (!channel.isUserSelectable()) {
                continue;
            }
            String code = channel.getCode();
            if (!activeByCode.containsKey(code)) {
                continue;
            }
            if (PaymentChannel.SCAN_PAY.equals(channel) && !thirdPartyPaymentExtension.isEnabled()) {
                continue;
            }
            ConceptView concept = activeByCode.get(code);
            long sort = concept.sortOrder() != null ? concept.sortOrder() : channel.getSortOrder();
            String label = StringUtils.defaultIfBlank(concept.display(), channel.getDisplay());
            options.add(new OptionDto(sort, code, label));
        }
        options.sort(
            Comparator.comparing(OptionDto::getSort, Comparator.nullsLast(Long::compareTo))
                .thenComparing(o -> StringUtils.defaultString(o.getValue())));
        return options;
    }

    /**
     * 是否允许作为 {@code payChannelCode} 入参（含 {@code SCAN_PAY}）。
     */
    public boolean isAllowedUserSelectableCode(String payChannelCode) {
        if (StringUtils.isBlank(payChannelCode)) {
            return false;
        }
        String code = payChannelCode.trim();
        return loadUserSelectableOptions().stream().anyMatch(o -> code.equals(o.getValue()));
    }

    private Map<String, ConceptView> loadActivePayEnumConcepts() {
        List<Concept> concepts =
            codeSystemQueryService.findConceptsByCodeSystem(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID);
        Map<String, ConceptView> activeByCode = new HashMap<>();
        for (Concept concept : concepts) {
            if (concept == null || Boolean.FALSE.equals(concept.getIsActive())) {
                continue;
            }
            if (StringUtils.isBlank(concept.getCode())) {
                continue;
            }
            activeByCode.put(
                concept.getCode().trim(),
                new ConceptView(concept.getDisplay(), concept.getSortOrder()));
        }
        return activeByCode;
    }

    private record ConceptView(String display, Long sortOrder) {
    }
}
