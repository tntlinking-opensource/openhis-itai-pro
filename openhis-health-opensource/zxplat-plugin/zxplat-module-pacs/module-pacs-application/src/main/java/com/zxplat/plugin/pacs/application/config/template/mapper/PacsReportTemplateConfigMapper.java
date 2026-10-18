/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.template.mapper;

import com.zxplat.plugin.pacs.application.config.template.dto.PacsReportTemplateRowDto;
import com.zxplat.plugin.pacs.application.config.template.response.SaveReportTemplateResponse;
import com.zxplat.plugin.pacs.domain.reporttemplate.entity.PacsReportTemplate;
import com.zxplat.plugin.pacs.domain.reporttemplate.queries.model.PacsReportTemplateQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告模板 DTO 装配。
 */
@Component
public class PacsReportTemplateConfigMapper {

    /**
     * 读模型转列表行。
     *
     * @param model 读模型
     * @return 列表行
     */
    public PacsReportTemplateRowDto toRowDto(PacsReportTemplateQueryModel model) {
        PacsReportTemplateRowDto dto = new PacsReportTemplateRowDto();
        if (model == null) {
            return dto;
        }
        dto.setId(model.getId());
        dto.setName(model.getName());
        dto.setModalityCode(model.getModalityCode());
        dto.setEnabledFlag(model.getEnabledFlag());
        dto.setSortOrder(model.getSortOrder());
        dto.setOwnerPractitionerId(model.getOwnerPractitionerId());
        return dto;
    }

    /**
     * 读模型批量转列表行。
     *
     * @param models 读模型列表
     * @return 列表行
     */
    public List<PacsReportTemplateRowDto> toRowDtos(List<PacsReportTemplateQueryModel> models) {
        List<PacsReportTemplateRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsReportTemplateQueryModel model : models) {
            rows.add(this.toRowDto(model));
        }
        return rows;
    }

    /**
     * 实体转写响应。
     *
     * @param entity 实体
     * @return 写响应
     */
    public SaveReportTemplateResponse toSaveResponse(PacsReportTemplate entity) {
        SaveReportTemplateResponse resp = new SaveReportTemplateResponse();
        if (entity == null) {
            return resp;
        }
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setEnabledFlag(entity.getEnabledFlag());
        return resp;
    }
}
