/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.share;

import com.openhis.campus.application.campusarchive.response.EligibilityCheckResponse;
import com.openhis.campus.application.campusgfchargecalc.response.PublicExpenseSplitResponse;
import com.openhis.campus.application.campusgfconfig.response.GfFreePercentDto;
import com.openhis.campus.application.campusgfconfig.response.MasterSwitchResponse;
import com.openhis.campus.application.campusgfratiocatalog.response.GfCatalogRatioDto;
import com.openhis.campus.application.campusgfratioitem.response.GfItemRatioDto;
import com.openhis.campus.application.campusgfrequestrecord.response.GfRatioRequestDto;
import com.openhis.campus.domain.chargecalc.view.PublicExpenseSplitQuery;
import com.openhis.campus.domain.chargecalc.view.PublicExpenseSplitView;
import com.openhis.campus.domain.gffreepercent.entity.GfFreePercent;
import com.openhis.campus.domain.gfchargeratio.entity.GfChargeRatio;
import com.openhis.campus.domain.gfchargeratioitem.entity.GfChargeRatioItem;
import com.openhis.campus.domain.gfratiorequest.entity.GfRatioRequest;
import com.openhis.campus.domain.publicexpenseprofile.model.EligibilityResult;
import com.openhis.campus.domain.shared.CampusGfConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 公费模块 DTO 映射。
 */
@Component
public class CampusGfMapper {

    public MasterSwitchResponse toMasterSwitchResponse(boolean enabled) {
        MasterSwitchResponse response = new MasterSwitchResponse();
        response.setEnabled(enabled);
        return response;
    }

    public GfFreePercentDto toFreePercentDto(GfFreePercent entity) {
        GfFreePercentDto dto = new GfFreePercentDto();
        dto.setId(entity.getId());
        dto.setClassEnum(entity.getClassEnum());
        dto.setSelfPayRatio(entity.getSelfPayRatio());
        dto.setChargeModeEnum(entity.getChargeModeEnum());
        return dto;
    }

    public GfCatalogRatioDto toCatalogRatioDto(GfChargeRatio entity) {
        GfCatalogRatioDto dto = new GfCatalogRatioDto();
        dto.setId(entity.getId());
        dto.setCatalogCode(entity.getCatalogCode());
        dto.setChargeModeEnum(entity.getChargeModeEnum());
        dto.setChargeModeLabel(CampusGfConstants.chargeModeLabel(entity.getChargeModeEnum()));
        dto.setRatio(entity.getRatio());
        dto.setEffectiveFrom(entity.getEffectiveFrom());
        dto.setEffectiveTo(entity.getEffectiveTo());
        return dto;
    }

    public GfItemRatioDto toItemRatioDto(GfChargeRatioItem entity) {
        GfItemRatioDto dto = new GfItemRatioDto();
        dto.setId(entity.getId());
        dto.setItemCode(entity.getItemCode());
        dto.setChargeModeEnum(entity.getChargeModeEnum());
        dto.setChargeModeLabel(CampusGfConstants.chargeModeLabel(entity.getChargeModeEnum()));
        dto.setRatio(entity.getRatio());
        dto.setEffectiveFrom(entity.getEffectiveFrom());
        dto.setEffectiveTo(entity.getEffectiveTo());
        return dto;
    }

    public GfRatioRequestDto toRequestDto(GfRatioRequest entity) {
        GfRatioRequestDto dto = new GfRatioRequestDto();
        dto.setId(entity.getId());
        dto.setRequestNo(entity.getRequestNo());
        dto.setChargeModeEnum(entity.getChargeModeEnum());
        dto.setChargeModeLabel(CampusGfConstants.chargeModeLabel(entity.getChargeModeEnum()));
        dto.setItemCode(entity.getItemCode());
        dto.setOldRatio(entity.getOldRatio());
        dto.setNewRatio(entity.getNewRatio());
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setApplicantId(entity.getApplicantId());
        dto.setApplicantName(entity.getApplicantName());
        dto.setApplyTime(entity.getCreatedAt());
        dto.setApproveTime(entity.getApproveTime());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    public PublicExpenseSplitResponse toSplitResponse(PublicExpenseSplitView view) {
        PublicExpenseSplitResponse response = new PublicExpenseSplitResponse();
        if (view.getEligibility() != null) {
            EligibilityCheckResponse eligibility = new EligibilityCheckResponse();
            eligibility.setPassed(view.getEligibility().getPassed());
            eligibility.setReasonCode(view.getEligibility().getReasonCode());
            eligibility.setReasonMessage(view.getEligibility().getReasonMessage());
            eligibility.setGfStatusEnum(view.getEligibility().getGfStatusEnum());
            eligibility.setCardNo(view.getEligibility().getCardNo());
            eligibility.setMedicalCode(view.getEligibility().getMedicalCode());
            eligibility.setCampusCode(view.getEligibility().getCampusCode());
            response.setEligibility(eligibility);
        }
        response.setGfSum(view.getGfSum());
        response.setSelfSum(view.getSelfSum());
        if (view.getLines() != null) {
            for (PublicExpenseSplitView.Line line : view.getLines()) {
                PublicExpenseSplitResponse.Line dto = new PublicExpenseSplitResponse.Line();
                dto.setItemCode(line.getItemCode());
                dto.setChargeModeEnum(line.getChargeModeEnum());
                dto.setRatio(line.getRatio());
                dto.setGfAmount(line.getGfAmount());
                dto.setSelfAmount(line.getSelfAmount());
                response.getLines().add(dto);
            }
        }
        return response;
    }

    public PublicExpenseSplitQuery fromPreviewRequest(
        com.openhis.campus.application.campusgfchargecalc.request.PreviewPublicExpenseSplitRequest request) {
        PublicExpenseSplitQuery query = new PublicExpenseSplitQuery();
        query.setPatientId(request.getPatientId());
        query.setMode(request.getMode());
        if (request.getLines() != null) {
            for (com.openhis.campus.application.campusgfchargecalc.request.PreviewPublicExpenseSplitRequest.Line line
                : request.getLines()) {
                PublicExpenseSplitQuery.Line domainLine = new PublicExpenseSplitQuery.Line();
                domainLine.setItemCode(line.getItemCode());
                domainLine.setCatalogCode(line.getCatalogCode());
                domainLine.setAmount(line.getAmount());
                domainLine.setClassEnum(line.getClassEnum());
                query.getLines().add(domainLine);
            }
        }
        return query;
    }

    public GfFreePercent toFreePercentEntity(GfFreePercentDto dto) {
        GfFreePercent entity = new GfFreePercent();
        entity.setId(dto.getId());
        entity.setClassEnum(dto.getClassEnum());
        entity.setSelfPayRatio(dto.getSelfPayRatio());
        entity.setChargeModeEnum(dto.getChargeModeEnum());
        return entity;
    }

    public GfChargeRatio toCatalogRatioEntity(
        com.openhis.campus.application.campusgfratiocatalog.request.SaveCatalogRatioRequest request) {
        GfChargeRatio entity = new GfChargeRatio();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setCatalogCode(request.getCatalogCode());
        entity.setChargeModeEnum(request.getChargeModeEnum());
        entity.setRatio(request.getRatio());
        entity.setEffectiveFrom(request.getEffectiveFrom());
        entity.setEffectiveTo(request.getEffectiveTo());
        return entity;
    }

    public GfChargeRatioItem toItemRatioEntity(
        com.openhis.campus.application.campusgfratioitem.request.SaveItemRatioRequest request) {
        GfChargeRatioItem entity = new GfChargeRatioItem();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setItemCode(request.getItemCode());
        entity.setChargeModeEnum(request.getChargeModeEnum());
        entity.setRatio(request.getRatio());
        entity.setEffectiveFrom(request.getEffectiveFrom());
        entity.setEffectiveTo(request.getEffectiveTo());
        return entity;
    }

    public GfRatioRequest toRequestEntity(
        com.openhis.campus.application.campusgfrequestrecord.request.CreateAdjustRequestRequest request) {
        GfRatioRequest entity = new GfRatioRequest();
        entity.setTenantId(request.getTenantId());
        entity.setChargeModeEnum(request.getChargeModeEnum());
        entity.setItemCode(request.getItemCode());
        entity.setOldRatio(request.getOldRatio());
        entity.setNewRatio(request.getNewRatio());
        entity.setRemark(request.getReason());
        return entity;
    }

    public EligibilityCheckResponse toEligibilityResponse(EligibilityResult result) {
        EligibilityCheckResponse response = new EligibilityCheckResponse();
        response.setPassed(result.isPassed());
        response.setReasonCode(result.getReasonCode());
        response.setReasonMessage(result.getReasonMessage());
        response.setGfStatusEnum(result.getGfStatusEnum());
        response.setCardNo(result.getCardNo());
        response.setMedicalCode(result.getMedicalCode());
        response.setCampusCode(result.getCampusCode());
        return response;
    }
}
