/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfratioitem;

import com.openhis.campus.application.campusgfratioitem.request.DisableItemRatioRequest;
import com.openhis.campus.application.campusgfratioitem.request.PageItemRatiosRequest;
import com.openhis.campus.application.campusgfratioitem.request.SaveItemRatioRequest;
import com.openhis.campus.application.campusgfratioitem.response.GfItemRatioDto;
import com.openhis.campus.application.campusgfratioitem.response.PageItemRatiosResponse;
import com.openhis.campus.application.campusgfratioitem.response.SaveItemRatioResponse;
import com.openhis.campus.application.share.CampusGfMapper;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.gfchargeratioitem.entity.GfChargeRatioItem;
import com.openhis.campus.domain.gfchargeratioitem.service.GfChargeRatioItemDomainService;
import com.openhis.campus.domain.gfchargeratioitem.service.GfChargeRatioItemQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目比例应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfRatioItem/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfRatioItemAppService {

    private final GfChargeRatioItemQueryService gfChargeRatioItemQueryService;
    private final GfChargeRatioItemDomainService gfChargeRatioItemDomainService;
    private final CampusGfMapper campusGfMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageItemRatiosResponse pageItemRatios(@Valid PageItemRatiosRequest request) {
        Page<GfChargeRatioItem> page = gfChargeRatioItemQueryService.page(
            request.getTenantId(),
            request.getChargeModeEnum(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GfItemRatioDto> rows = new ArrayList<>();
        for (GfChargeRatioItem entity : page.getContent()) {
            rows.add(campusGfMapper.toItemRatioDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageItemRatiosResponse(), rows, page);
    }

    public SaveItemRatioResponse saveItemRatio(@Valid SaveItemRatioRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfChargeRatioItem saved = gfChargeRatioItemDomainService.upsert(
            campusGfMapper.toItemRatioEntity(request), operatorId);
        SaveItemRatioResponse response = new SaveItemRatioResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveItemRatioResponse disableItemRatio(@Valid DisableItemRatioRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfChargeRatioItem saved = gfChargeRatioItemDomainService.disable(
            request.getTenantId(), request.getId(), operatorId);
        SaveItemRatioResponse response = new SaveItemRatioResponse();
        response.setId(saved.getId());
        return response;
    }
}
