/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.mapper;

import com.zxplat.plugin.pacs.application.registration.dto.PacsRegistrationRowDto;
import com.zxplat.plugin.pacs.application.registration.response.CreateLocalRegistrationResponse;
import com.zxplat.plugin.pacs.application.registration.response.GetRegistrationDetailResponse;
import com.zxplat.plugin.pacs.application.registration.response.SaveRegistrationResponse;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemOrderQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检查登记工作台 DTO 装配。
 */
@Component
public class PacsRegistrationMapper {

    /**
     * 列表读模型 → 行 DTO。
     *
     * @param model 读模型
     * @param patientName 患者姓名
     * @return 行 DTO
     */
    public PacsRegistrationRowDto toRowDto(PacsWorkItemQueryModel model, String patientName) {
        PacsRegistrationRowDto dto = new PacsRegistrationRowDto();
        if (model == null) {
            return dto;
        }
        dto.setWorkItemId(model.getId());
        dto.setAccessionNumber(model.getAccessionNumber());
        dto.setPatientId(model.getPatientId());
        dto.setPatientName(patientName);
        dto.setEncounterId(model.getEncounterId());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setModalityCode(model.getModalityCode());
        dto.setBodySiteCode(model.getBodySiteCode());
        dto.setScheduledTime(model.getScheduledTime());
        dto.setOrderCount(model.getOrderCount());
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }

    /**
     * 批量装配列表行。
     *
     * @param models 读模型列表
     * @param nameByPatientId 患者姓名映射
     * @return 行列表
     */
    public List<PacsRegistrationRowDto> toRowDtos(
        List<PacsWorkItemQueryModel> models,
        Map<Long, String> nameByPatientId) {
        List<PacsRegistrationRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsWorkItemQueryModel model : models) {
            String name = null;
            if (model != null && model.getPatientId() != null && nameByPatientId != null) {
                name = nameByPatientId.get(model.getPatientId());
            }
            rows.add(this.toRowDto(model, name));
        }
        return rows;
    }

    /**
     * 详情读模型 → 响应。
     *
     * @param detail 详情
     * @param patientName 姓名
     * @return 响应
     */
    public GetRegistrationDetailResponse toDetailResponse(PacsWorkItemDetailQueryModel detail, String patientName) {
        GetRegistrationDetailResponse resp = new GetRegistrationDetailResponse();
        if (detail == null) {
            return resp;
        }
        resp.setWorkItemId(detail.getId());
        resp.setAccessionNumber(detail.getAccessionNumber());
        resp.setPatientId(detail.getPatientId());
        resp.setPatientName(patientName);
        resp.setEncounterId(detail.getEncounterId());
        resp.setOrganizationId(detail.getOrganizationId());
        resp.setStatusEnum(detail.getStatusEnum());
        resp.setModalityCode(detail.getModalityCode());
        resp.setBodySiteCode(detail.getBodySiteCode());
        resp.setDeviceId(detail.getDeviceId());
        resp.setPerformerPractitionerId(detail.getPerformerPractitionerId());
        resp.setScheduledTime(detail.getScheduledTime());
        resp.setHasMedia(detail.getHasMedia());
        resp.setOrderCount(detail.getOrderCount());
        List<Long> srIds = new ArrayList<>();
        if (detail.getOrderLinks() != null) {
            for (PacsWorkItemOrderQueryModel link : detail.getOrderLinks()) {
                if (link != null && link.getServiceRequestId() != null) {
                    srIds.add(link.getServiceRequestId());
                }
            }
        }
        resp.setServiceRequestIds(srIds);
        return resp;
    }

    /**
     * 聚合根 → 保存响应。
     *
     * @param workItem 工单
     * @return 响应
     */
    public SaveRegistrationResponse toSaveResponse(PacsWorkItem workItem) {
        SaveRegistrationResponse resp = new SaveRegistrationResponse();
        if (workItem == null) {
            return resp;
        }
        resp.setWorkItemId(workItem.getId());
        resp.setAccessionNumber(workItem.getAccessionNumber());
        resp.setStatusEnum(workItem.getStatusEnum());
        return resp;
    }

    /**
     * 本地补登聚合根 → 响应。
     *
     * @param workItem 工单
     * @param encounterId 就诊主键
     * @return 响应
     */
    public CreateLocalRegistrationResponse toCreateLocalResponse(PacsWorkItem workItem, Long encounterId) {
        CreateLocalRegistrationResponse resp = new CreateLocalRegistrationResponse();
        if (workItem == null) {
            return resp;
        }
        resp.setWorkItemId(workItem.getId());
        resp.setEncounterId(encounterId);
        resp.setAccessionNumber(workItem.getAccessionNumber());
        resp.setStatusEnum(workItem.getStatusEnum());
        return resp;
    }
}
