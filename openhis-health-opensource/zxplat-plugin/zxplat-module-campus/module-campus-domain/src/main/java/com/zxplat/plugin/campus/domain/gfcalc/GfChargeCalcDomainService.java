/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfcalc;

import com.zxplat.plugin.campus.domain.chargecalc.view.PublicExpenseSplitQuery;
import com.zxplat.plugin.campus.domain.chargecalc.view.PublicExpenseSplitView;
import com.zxplat.plugin.campus.domain.gfchargeratio.service.GfChargeRatioQueryService;
import com.zxplat.plugin.campus.domain.gfchargeratioitem.service.GfChargeRatioItemQueryService;
import com.zxplat.plugin.campus.domain.gffreepercent.service.GfFreePercentQueryService;
import com.zxplat.plugin.campus.domain.gfswitch.service.GfSwitchDomainService;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.model.EligibilityResult;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.service.PublicExpenseEligibilityDomainService;
import com.zxplat.plugin.campus.domain.shared.CampusGfConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公费收费拆分试算领域服务。
 */
@Service
@RequiredArgsConstructor
public class GfChargeCalcDomainService {

    private static final int MONEY_SCALE = 2;

    private final PublicExpenseEligibilityDomainService publicExpenseEligibilityDomainService;
    private final GfSwitchDomainService gfSwitchDomainService;
    private final GfChargeRatioItemQueryService gfChargeRatioItemQueryService;
    private final GfChargeRatioQueryService gfChargeRatioQueryService;
    private final GfFreePercentQueryService gfFreePercentQueryService;

    public PublicExpenseSplitView previewSplit(Long tenantId, PublicExpenseSplitQuery query) {
        if (query == null) {
            throw new BusinessException("GF_LINE_INVALID", "试算参数不能为空");
        }
        CampusGfConstants.validateChargeMode(query.getMode());
        Long resolvedTenantId = resolveTenantId(tenantId);
        PublicExpenseSplitView view = new PublicExpenseSplitView();
        EligibilityResult eligibility = publicExpenseEligibilityDomainService.check(resolvedTenantId, query.getPatientId());
        view.setEligibility(toEligibility(eligibility));
        if (!eligibility.isPassed()) {
            return view;
        }
        if (!gfSwitchDomainService.getMasterSwitch(resolvedTenantId)) {
            throw new BusinessException("GF_SWITCH_OFF", "公费总开关已关闭");
        }
        List<PublicExpenseSplitQuery.Line> inputLines = query.getLines();
        if (inputLines == null || inputLines.isEmpty()) {
            throw new BusinessException("GF_LINE_INVALID", "试算明细不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal gfSum = BigDecimal.ZERO;
        BigDecimal selfSum = BigDecimal.ZERO;
        List<PublicExpenseSplitView.Line> outputLines = new ArrayList<>();
        for (PublicExpenseSplitQuery.Line inputLine : inputLines) {
            if (inputLine == null || inputLine.getAmount() == null) {
                throw new BusinessException("GF_LINE_INVALID", "试算明细金额无效");
            }
            if (inputLine.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("GF_LINE_INVALID", "试算明细金额不能为负");
            }
            BigDecimal ratio = resolveRatio(resolvedTenantId, query.getMode(), inputLine, now);
            BigDecimal gfAmount = inputLine.getAmount().multiply(ratio).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal selfAmount = inputLine.getAmount().subtract(gfAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            PublicExpenseSplitView.Line outputLine = new PublicExpenseSplitView.Line();
            outputLine.setItemCode(inputLine.getItemCode());
            outputLine.setChargeModeEnum(query.getMode());
            outputLine.setRatio(ratio);
            outputLine.setGfAmount(gfAmount);
            outputLine.setSelfAmount(selfAmount);
            outputLines.add(outputLine);
            gfSum = gfSum.add(gfAmount);
            selfSum = selfSum.add(selfAmount);
        }
        view.setLines(outputLines);
        view.setGfSum(gfSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        view.setSelfSum(selfSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return view;
    }

    private BigDecimal resolveRatio(
        Long tenantId,
        String chargeModeEnum,
        PublicExpenseSplitQuery.Line line,
        LocalDateTime at) {
        if (line.getItemCode() != null && !line.getItemCode().isBlank()) {
            var itemRatio = gfChargeRatioItemQueryService.getCurrent(
                tenantId, line.getItemCode(), chargeModeEnum, at);
            if (itemRatio.isPresent()) {
                return itemRatio.get().getRatio();
            }
        }
        if (line.getCatalogCode() != null && !line.getCatalogCode().isBlank()) {
            var catalogRatio = gfChargeRatioQueryService.findCurrent(
                tenantId, line.getCatalogCode(), chargeModeEnum, at);
            if (catalogRatio.isPresent()) {
                return catalogRatio.get().getRatio();
            }
        }
        if (line.getClassEnum() != null && !line.getClassEnum().isBlank()) {
            var freePercent = gfFreePercentQueryService.findByClassAndMode(
                tenantId, line.getClassEnum(), chargeModeEnum);
            if (freePercent.isPresent()) {
                return BigDecimal.ONE.subtract(freePercent.get().getSelfPayRatio());
            }
        }
        return BigDecimal.ZERO;
    }

    private PublicExpenseSplitView.Eligibility toEligibility(EligibilityResult result) {
        PublicExpenseSplitView.Eligibility eligibility = new PublicExpenseSplitView.Eligibility();
        eligibility.setPassed(result.isPassed());
        eligibility.setReasonCode(result.getReasonCode());
        eligibility.setReasonMessage(result.getReasonMessage());
        eligibility.setGfStatusEnum(result.getGfStatusEnum());
        eligibility.setCardNo(result.getCardNo());
        eligibility.setMedicalCode(result.getMedicalCode());
        eligibility.setCampusCode(result.getCampusCode());
        return eligibility;
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        Long current = TenantUtils.getCurrentTenantId();
        if (current == null) {
            throw new BusinessException("GF_LINE_INVALID", "租户上下文缺失");
        }
        return current;
    }
}
