/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.domain.share.constants.PaymentChannelConstants;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.terminology.entity.Concept;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.domain.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收银支付渠道下拉：{@link PaymentChannel} 用户可选 ∩ 477 active ∩（SCAN_PAY 时）第三方收单开关。
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelOptionProvider {

    /** 术语读侧：加载 PAY_ENUM 激活概念。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 第三方收单扩展：控制 SCAN_PAY 是否可选。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /**
     * 加载用户可选支付渠道（含 UI 专用 {@link PaymentChannel#SCAN_PAY}）。
     *
     * @return 按 sort 升序的支付渠道下拉项
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
            long sort;
            if (concept.sortOrder() != null) {
                sort = concept.sortOrder();
            } else {
                sort = channel.getSortOrder();
            }
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
     *
     * @param payChannelCode 支付渠道持久化码
     * @return 在用户可选且术语/第三方开关均满足时为 {@code true}
     */
    public boolean isAllowedUserSelectableCode(String payChannelCode) {
        if (StringUtils.isEmpty(payChannelCode)) {
            return false;
        }
        String code = payChannelCode.trim();
        return loadUserSelectableOptions().stream().anyMatch(o -> code.equals(o.getValue()));
    }

    /**
     * 加载 PAY_ENUM 术语系统中激活概念的 code → 展示/sortOrder 视图。
     *
     * @return code → ConceptView
     */
    private Map<String, ConceptView> loadActivePayEnumConcepts() {
        List<Concept> concepts =
            codeSystemQueryService.findConceptsByCodeSystem(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID);
        Map<String, ConceptView> activeByCode = new HashMap<>();
        for (Concept concept : concepts) {
            if (concept == null || Boolean.FALSE.equals(concept.getIsActive())) {
                continue;
            }
            if (StringUtils.isEmpty(concept.getCode())) {
                continue;
            }
            activeByCode.put(
                concept.getCode().trim(),
                new ConceptView(concept.getDisplay(), concept.getSortOrder()));
        }
        return activeByCode;
    }

    /**
     * 支付渠道术语概念视图。
     *
     * @param display   展示名
     * @param sortOrder 排序值
     */
    private record ConceptView(String display, Long sortOrder) {
    }
}
