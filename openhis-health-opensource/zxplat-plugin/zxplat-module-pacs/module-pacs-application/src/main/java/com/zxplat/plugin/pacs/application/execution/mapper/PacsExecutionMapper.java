/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.execution.mapper;

import com.zxplat.plugin.pacs.application.execution.dto.PacsExecutionRowDto;
import com.zxplat.plugin.pacs.application.execution.response.CompleteExamResponse;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检查执行工作台 DTO 装配。
 */
@Component
public class PacsExecutionMapper {

    /**
     * 读模型 → 行。
     *
     * @param model 读模型
     * @param patientName 姓名
     * @return 行
     */
    public PacsExecutionRowDto toRowDto(PacsWorkItemQueryModel model, String patientName) {
        PacsExecutionRowDto dto = new PacsExecutionRowDto();
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
        dto.setPerformedTime(model.getPerformedTime());
        dto.setOrderCount(model.getOrderCount());
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }

    /**
     * 批量装配。
     *
     * @param models 读模型
     * @param nameByPatientId 姓名映射
     * @return 行列表
     */
    public List<PacsExecutionRowDto> toRowDtos(
        List<PacsWorkItemQueryModel> models,
        Map<Long, String> nameByPatientId) {
        List<PacsExecutionRowDto> rows = new ArrayList<>();
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
     * 实体 → 完成响应。
     *
     * @param workItem 工单
     * @return 响应
     */
    public CompleteExamResponse toCompleteResponse(PacsWorkItem workItem) {
        CompleteExamResponse resp = new CompleteExamResponse();
        if (workItem == null) {
            return resp;
        }
        resp.setWorkItemId(workItem.getId());
        resp.setAccessionNumber(workItem.getAccessionNumber());
        resp.setStatusEnum(workItem.getStatusEnum());
        resp.setPerformedTime(workItem.getPerformedTime());
        return resp;
    }
}
