/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfconfig;

import com.zxplat.plugin.campus.application.campusgfconfig.request.GetCalcModeRequest;
import com.zxplat.plugin.campus.application.campusgfconfig.request.GetMasterSwitchRequest;
import com.zxplat.plugin.campus.application.campusgfconfig.request.ListFreePercentsRequest;
import com.zxplat.plugin.campus.application.campusgfconfig.request.SaveFreePercentsRequest;
import com.zxplat.plugin.campus.application.campusgfconfig.request.SaveMasterSwitchRequest;
import com.zxplat.plugin.campus.application.campusgfconfig.response.CalcModeResponse;
import com.zxplat.plugin.campus.application.campusgfconfig.response.GfFreePercentDto;
import com.zxplat.plugin.campus.application.campusgfconfig.response.ListFreePercentsResponse;
import com.zxplat.plugin.campus.application.campusgfconfig.response.MasterSwitchResponse;
import com.zxplat.plugin.campus.application.share.CampusGfMapper;
import com.zxplat.plugin.campus.application.share.CampusOperatorResolver;
import com.zxplat.plugin.campus.domain.gffreepercent.entity.GfFreePercent;
import com.zxplat.plugin.campus.domain.gffreepercent.service.GfFreePercentDomainService;
import com.zxplat.plugin.campus.domain.gffreepercent.service.GfFreePercentQueryService;
import com.zxplat.plugin.campus.domain.gfswitch.service.GfSwitchDomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 公费配置应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfConfig/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfConfigAppService {

    private final GfSwitchDomainService gfSwitchDomainService;
    private final GfFreePercentDomainService gfFreePercentDomainService;
    private final GfFreePercentQueryService gfFreePercentQueryService;
    private final CampusGfMapper campusGfMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    public MasterSwitchResponse getMasterSwitch(@Valid GetMasterSwitchRequest request) {
        return campusGfMapper.toMasterSwitchResponse(
            gfSwitchDomainService.getMasterSwitch(request.getTenantId()));
    }

    public MasterSwitchResponse saveMasterSwitch(@Valid SaveMasterSwitchRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        gfSwitchDomainService.saveMasterSwitch(
            request.getTenantId(), Boolean.TRUE.equals(request.getEnabled()), operatorId);
        return campusGfMapper.toMasterSwitchResponse(
            gfSwitchDomainService.getMasterSwitch(request.getTenantId()));
    }

    public CalcModeResponse getCalcMode(@Valid GetCalcModeRequest request) {
        CalcModeResponse response = new CalcModeResponse();
        gfSwitchDomainService.getCalcMode(request.getTenantId()).ifPresent(response::setCalcMode);
        return response;
    }

    public ListFreePercentsResponse listFreePercents(@Valid ListFreePercentsRequest request) {
        ListFreePercentsResponse response = new ListFreePercentsResponse();
        for (GfFreePercent entity : gfFreePercentQueryService.listByMode(
            request.getTenantId(), request.getChargeModeEnum())) {
            response.getRecords().add(campusGfMapper.toFreePercentDto(entity));
        }
        return response;
    }

    public ListFreePercentsResponse saveFreePercents(@Valid SaveFreePercentsRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        List<GfFreePercent> incoming = new ArrayList<>();
        for (GfFreePercentDto dto : request.getItems()) {
            GfFreePercent entity = campusGfMapper.toFreePercentEntity(dto);
            entity.setChargeModeEnum(request.getChargeModeEnum());
            incoming.add(entity);
        }
        ListFreePercentsResponse response = new ListFreePercentsResponse();
        for (GfFreePercent saved : gfFreePercentDomainService.saveByMode(
            request.getTenantId(), request.getChargeModeEnum(), incoming, operatorId)) {
            response.getRecords().add(campusGfMapper.toFreePercentDto(saved));
        }
        return response;
    }
}
