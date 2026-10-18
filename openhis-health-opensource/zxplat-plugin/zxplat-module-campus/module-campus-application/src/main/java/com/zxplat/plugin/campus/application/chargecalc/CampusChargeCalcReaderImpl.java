/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.chargecalc;

import com.zxplat.plugin.campus.domain.chargecalc.CampusChargeCalcReader;
import com.zxplat.plugin.campus.domain.chargecalc.view.PublicExpenseSplitQuery;
import com.zxplat.plugin.campus.domain.chargecalc.view.PublicExpenseSplitView;
import com.zxplat.plugin.campus.domain.gfcalc.GfChargeCalcDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 公费收费试算 Reader 实现。
 */
@Service
@RequiredArgsConstructor
public class CampusChargeCalcReaderImpl implements CampusChargeCalcReader {

    private final GfChargeCalcDomainService gfChargeCalcDomainService;

    @Override
    public PublicExpenseSplitView previewSplit(Long tenantId, PublicExpenseSplitQuery query) {
        return gfChargeCalcDomainService.previewSplit(tenantId, query);
    }
}
