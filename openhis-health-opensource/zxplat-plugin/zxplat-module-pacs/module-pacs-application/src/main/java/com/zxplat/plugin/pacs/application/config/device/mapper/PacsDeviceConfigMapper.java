/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.mapper;

import com.zxplat.plugin.pacs.application.config.device.dto.PacsDeviceRowDto;
import com.zxplat.plugin.pacs.application.config.device.response.SaveDeviceResponse;
import com.zxplat.plugin.pacs.domain.device.entity.PacsDevice;
import com.zxplat.plugin.pacs.domain.device.queries.model.PacsDeviceQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备台账 DTO 装配。
 */
@Component
public class PacsDeviceConfigMapper {

    /**
     * 读模型转列表行。
     *
     * @param model 读模型
     * @return 列表行
     */
    public PacsDeviceRowDto toRowDto(PacsDeviceQueryModel model) {
        PacsDeviceRowDto dto = new PacsDeviceRowDto();
        if (model == null) {
            return dto;
        }
        dto.setId(model.getId());
        dto.setDeviceCode(model.getDeviceCode());
        dto.setName(model.getName());
        dto.setModalityCode(model.getModalityCode());
        dto.setAeTitle(model.getAeTitle());
        dto.setStationName(model.getStationName());
        dto.setEnabledFlag(model.getEnabledFlag());
        dto.setSortOrder(model.getSortOrder());
        return dto;
    }

    /**
     * 读模型批量转列表行。
     *
     * @param models 读模型列表
     * @return 列表行
     */
    public List<PacsDeviceRowDto> toRowDtos(List<PacsDeviceQueryModel> models) {
        List<PacsDeviceRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsDeviceQueryModel model : models) {
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
    public SaveDeviceResponse toSaveResponse(PacsDevice entity) {
        SaveDeviceResponse resp = new SaveDeviceResponse();
        if (entity == null) {
            return resp;
        }
        resp.setId(entity.getId());
        resp.setDeviceCode(entity.getDeviceCode());
        resp.setName(entity.getName());
        resp.setEnabledFlag(entity.getEnabledFlag());
        return resp;
    }
}
