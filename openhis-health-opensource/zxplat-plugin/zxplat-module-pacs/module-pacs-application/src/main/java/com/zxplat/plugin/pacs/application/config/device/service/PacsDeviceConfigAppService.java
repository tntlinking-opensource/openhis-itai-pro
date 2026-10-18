/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.service;

import com.zxplat.plugin.pacs.application.config.device.dto.PacsDeviceRowDto;
import com.zxplat.plugin.pacs.application.config.device.mapper.PacsDeviceConfigMapper;
import com.zxplat.plugin.pacs.application.config.device.request.DeviceIdRequest;
import com.zxplat.plugin.pacs.application.config.device.request.SaveDeviceRequest;
import com.zxplat.plugin.pacs.application.config.device.request.SearchDevicesRequest;
import com.zxplat.plugin.pacs.application.config.device.response.SaveDeviceResponse;
import com.zxplat.plugin.pacs.application.config.device.response.SearchDevicesResponse;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.pacs.application.share.PacsCurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.plugin.pacs.domain.device.command.SavePacsDeviceCommand;
import com.zxplat.plugin.pacs.domain.device.entity.PacsDevice;
import com.zxplat.plugin.pacs.domain.device.queries.model.PacsDeviceQueryModel;
import com.zxplat.plugin.pacs.domain.device.service.PacsDeviceDomainService;
import com.zxplat.plugin.pacs.domain.device.service.PacsDeviceQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备台账配置（AS-PACS-CFG-003）。
 *
 * <p>接入：{@code POST /api/app/pacsDeviceConfig/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsDeviceConfigAppService {

    private final PacsDeviceDomainService pacsDeviceDomainService;
    private final PacsDeviceQueryService pacsDeviceQueryService;
    private final PacsCurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PacsDeviceConfigMapper pacsDeviceConfigMapper;

    /**
     * IF-PACS-CFG-03a：设备列表。
     *
     * @param request 分页过滤
     * @return 分页行
     */
    public SearchDevicesResponse searchDevices(SearchDevicesRequest request) {
        // Gate：分页窗口须合法，避免全表扫描
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "分页参数非法");
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        // Pre：按模态/启停/关键字分页查设备（读侧 QS）
        Page<PacsDeviceQueryModel> page = pacsDeviceQueryService.search(
            request.getModalityCode(), request.getEnabledOnly(), request.getKeyword(), pageable);
        // Asm：映射出参
        List<PacsDeviceRowDto> records = pacsDeviceConfigMapper.toRowDtos(page.getContent());
        return PagedResultBuilder.build(new SearchDevicesResponse(), records, page);
    }

    /**
     * IF-PACS-CFG-03b：保存设备。
     *
     * @param request 保存入参
     * @return 结果
     */
    @Transactional
    public SaveDeviceResponse saveDevice(SaveDeviceRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：新增或更新设备
        SavePacsDeviceCommand cmd = new SavePacsDeviceCommand();
        cmd.setId(request.getId());
        cmd.setDeviceCode(request.getDeviceCode());
        cmd.setName(request.getName());
        cmd.setModalityCode(request.getModalityCode());
        cmd.setAeTitle(request.getAeTitle());
        cmd.setStationName(request.getStationName());
        cmd.setEnabledFlag(request.getEnabledFlag());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setRemark(request.getRemark());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setOperatorId(operatorId);
        PacsDevice saved = pacsDeviceDomainService.save(cmd);
        return pacsDeviceConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-03c：停用。
     *
     * @param request 主键
     * @return 结果
     */
    @Transactional
    public SaveDeviceResponse disableDevice(DeviceIdRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：停用设备
        PacsDevice saved = pacsDeviceDomainService.disable(request.getId(), operatorId);
        return pacsDeviceConfigMapper.toSaveResponse(saved);
    }

    /**
     * IF-PACS-CFG-03d：软删。
     *
     * @param request 主键
     * @return 是否成功
     */
    @Transactional
    public Boolean deleteDevice(DeviceIdRequest request) {
        // Gate：须识别当前操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：软删设备
        pacsDeviceDomainService.delete(request.getId(), operatorId);
        return Boolean.TRUE;
    }
}
