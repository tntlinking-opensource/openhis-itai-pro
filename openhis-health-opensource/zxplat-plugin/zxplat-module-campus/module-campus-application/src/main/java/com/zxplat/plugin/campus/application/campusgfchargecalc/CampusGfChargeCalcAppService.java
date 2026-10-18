/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfchargecalc;

import com.zxplat.plugin.campus.application.campusgfchargecalc.request.PreviewPublicExpenseSplitRequest;
import com.zxplat.plugin.campus.application.campusgfchargecalc.response.PublicExpenseSplitResponse;
import com.zxplat.plugin.campus.application.share.CampusGfMapper;
import com.zxplat.plugin.campus.domain.gfcalc.GfChargeCalcDomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 公费收费试算应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfChargeCalc/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfChargeCalcAppService {

    private final GfChargeCalcDomainService gfChargeCalcDomainService;
    private final CampusGfMapper campusGfMapper;

    public PublicExpenseSplitResponse previewPublicExpenseSplit(@Valid PreviewPublicExpenseSplitRequest request) {
        return campusGfMapper.toSplitResponse(gfChargeCalcDomainService.previewSplit(
            request.getTenantId(),
            campusGfMapper.fromPreviewRequest(request)));
    }
}
