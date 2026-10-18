/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfratiocatalog;

import com.zxplat.plugin.campus.application.campusgfratiocatalog.request.DisableCatalogRatioRequest;
import com.zxplat.plugin.campus.application.campusgfratiocatalog.request.PageCatalogRatiosRequest;
import com.zxplat.plugin.campus.application.campusgfratiocatalog.request.SaveCatalogRatioRequest;
import com.zxplat.plugin.campus.application.campusgfratiocatalog.response.GfCatalogRatioDto;
import com.zxplat.plugin.campus.application.campusgfratiocatalog.response.PageCatalogRatiosResponse;
import com.zxplat.plugin.campus.application.campusgfratiocatalog.response.SaveCatalogRatioResponse;
import com.zxplat.plugin.campus.application.share.CampusGfMapper;
import com.zxplat.plugin.campus.application.share.CampusOperatorResolver;
import com.zxplat.plugin.campus.application.share.CampusPagedResultBuilder;
import com.zxplat.plugin.campus.domain.gfchargeratio.entity.GfChargeRatio;
import com.zxplat.plugin.campus.domain.gfchargeratio.service.GfChargeRatioDomainService;
import com.zxplat.plugin.campus.domain.gfchargeratio.service.GfChargeRatioQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录比例应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfRatioCatalog/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfRatioCatalogAppService {

    private final GfChargeRatioQueryService gfChargeRatioQueryService;
    private final GfChargeRatioDomainService gfChargeRatioDomainService;
    private final CampusGfMapper campusGfMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageCatalogRatiosResponse pageCatalogRatios(@Valid PageCatalogRatiosRequest request) {
        Page<GfChargeRatio> page = gfChargeRatioQueryService.page(
            request.getTenantId(),
            request.getChargeModeEnum(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GfCatalogRatioDto> rows = new ArrayList<>();
        for (GfChargeRatio entity : page.getContent()) {
            rows.add(campusGfMapper.toCatalogRatioDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageCatalogRatiosResponse(), rows, page);
    }

    public SaveCatalogRatioResponse saveCatalogRatio(@Valid SaveCatalogRatioRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfChargeRatio saved = gfChargeRatioDomainService.upsert(
            campusGfMapper.toCatalogRatioEntity(request), operatorId);
        SaveCatalogRatioResponse response = new SaveCatalogRatioResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCatalogRatioResponse disableCatalogRatio(@Valid DisableCatalogRatioRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfChargeRatio saved = gfChargeRatioDomainService.disable(
            request.getTenantId(), request.getId(), operatorId);
        SaveCatalogRatioResponse response = new SaveCatalogRatioResponse();
        response.setId(saved.getId());
        return response;
    }
}
