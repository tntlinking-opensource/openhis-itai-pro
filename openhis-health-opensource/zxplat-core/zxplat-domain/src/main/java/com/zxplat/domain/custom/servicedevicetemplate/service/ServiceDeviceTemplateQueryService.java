/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicedevicetemplate.service;

import com.zxplat.domain.custom.servicedevicetemplate.entity.ServiceDeviceTemplate;
import com.zxplat.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.zxplat.domain.custom.servicedevicetemplate.repository.ServiceDeviceTemplateRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗活动与耗材/设备模板绑定关系的读侧查询服务。
 *
 * <p>职责：按业务范围、项目类型、关联主表与主键等维度查询绑定记录，供开立端与配置端只读展示；不写库。
 * 启用态与软删过滤口径随各仓储方法下推到 SQL。</p>
 *
 * <p>聚合边界：仅本聚合 {@link ServiceDeviceTemplateRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceDeviceTemplateQueryService {

    private final ServiceDeviceTemplateRepository repository;

    /**
     * 按范围、类型与来源主键列出绑定（仅启用态，按主键升序）。
     *
     * @param rangeCode 业务范围编码
     * @param typeCode 项目类型编码
     * @param itemTable 来源主表名
     * @param itemId 来源主键
     * @return 绑定读模型列表，无命中时为空列表
     */
    public List<ServiceDeviceTemplateQueryModel> listBindingsByRangeTypeAndSource(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId
    ) {
        // 查询：仅返回启用（active）且未逻辑删除的绑定（BR-SDT-003/006/013）
        List<ServiceDeviceTemplate> rows = repository.findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdAndStatusEnumOrderByIdAsc(
            rangeCode,
            typeCode,
            itemTable,
            itemId,
            PublicationStatus.ACTIVE.getCode()
        );
        List<ServiceDeviceTemplateQueryModel> result = new ArrayList<>();
        // 逐条映射为读模型，便于应用层直接渲染绑定明细
        for (ServiceDeviceTemplate row : rows) {
            result.add(toModel(row));
        }
        return result;
    }

    /**
     * 按来源主键分页查询绑定（默认排序 id 降序，软删由底座/仓储口径排除）。
     *
     * @param rangeCode 业务范围编码；为空时不限范围
     * @param typeCode 项目类型编码
     * @param itemTable 来源主表名
     * @param itemId 来源主键
     * @param pageable 分页与排序
     * @return 分页读模型
     */
    public Page<ServiceDeviceTemplateQueryModel> pageBindingsBySource(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId,
        Pageable pageable
    ) {
        // 查询：分页直接下推到数据库，默认排除已逻辑删除行（BR-SDT-013）
        Page<ServiceDeviceTemplate> page;
        if (StringUtils.isEmpty(rangeCode)) {
            page = repository.findAllByTypeCodeAndItemTableAndItemIdOrderByIdDesc(
                typeCode,
                itemTable,
                itemId,
                pageable
            );
        } else {
            page = repository.findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdOrderByIdDesc(
                rangeCode,
                typeCode,
                itemTable,
                itemId,
                pageable
            );
        }
        return page.map(this::toModel);
    }

    /**
     * 按来源主键与发布状态分页查询绑定。
     *
     * @param rangeCode 业务范围编码；为空时不限范围
     * @param typeCode 项目类型编码
     * @param itemTable 来源主表名
     * @param itemId 来源主键
     * @param statusEnum 状态编码（与主数据发布状态一致）
     * @param pageable 分页与排序
     * @return 分页读模型
     */
    public Page<ServiceDeviceTemplateQueryModel> pageBindingsBySourceAndStatus(
        String rangeCode,
        String typeCode,
        String itemTable,
        String itemId,
        String statusEnum,
        Pageable pageable
    ) {
        Page<ServiceDeviceTemplate> page;
        if (StringUtils.isEmpty(rangeCode)) {
            page = repository.findAllByTypeCodeAndItemTableAndItemIdAndStatusEnumOrderByIdDesc(
                typeCode,
                itemTable,
                itemId,
                statusEnum,
                pageable
            );
        } else {
            page = repository.findAllByRangeCodeAndTypeCodeAndItemTableAndItemIdAndStatusEnumOrderByIdDesc(
                rangeCode,
                typeCode,
                itemTable,
                itemId,
                statusEnum,
                pageable
            );
        }
        return page.map(this::toModel);
    }

    /**
     * 按范围与类型分页查询绑定（不限制具体来源主键，用于目录型浏览）。
     *
     * @param rangeCode 业务范围编码
     * @param typeCode 项目类型编码
     * @param pageable 分页与排序
     * @return 分页读模型
     */
    public Page<ServiceDeviceTemplateQueryModel> pageBindingsByRangeAndType(
        String rangeCode,
        String typeCode,
        Pageable pageable
    ) {
        Page<ServiceDeviceTemplate> page = repository.findAllByRangeCodeAndTypeCodeOrderByIdDesc(
            rangeCode,
            typeCode,
            pageable
        );
        return page.map(this::toModel);
    }

    /**
     * 按范围、类型与状态分页查询绑定。
     *
     * @param rangeCode 业务范围编码
     * @param typeCode 项目类型编码
     * @param statusEnum 状态编码
     * @param pageable 分页与排序
     * @return 分页读模型
     */
    public Page<ServiceDeviceTemplateQueryModel> pageBindingsByRangeAndTypeAndStatus(
        String rangeCode,
        String typeCode,
        String statusEnum,
        Pageable pageable
    ) {
        Page<ServiceDeviceTemplate> page = repository.findAllByRangeCodeAndTypeCodeAndStatusEnumOrderByIdDesc(
            rangeCode,
            typeCode,
            statusEnum,
            pageable
        );
        return page.map(this::toModel);
    }

    /**
     * 按主键加载单条绑定；不存在时视为数据缺失，阻断后续业务。
     *
     * @param bindingId 绑定主键
     * @return 绑定读模型
     * @throws BusinessException 绑定不存在
     */
    public ServiceDeviceTemplateQueryModel findById(Long bindingId) {
        ServiceDeviceTemplate row = repository.findById(bindingId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_NOT_FOUND, "绑定不存在"));
        return toModel(row);
    }

    /**
     * 将持久化实体拷贝为读侧模型（无计算字段，仅扁平暴露绑定事实）。
     *
     * @param row 仓储加载的绑定实体
     * @return 查询模型
     */
    private ServiceDeviceTemplateQueryModel toModel(ServiceDeviceTemplate row) {
        ServiceDeviceTemplateQueryModel model = new ServiceDeviceTemplateQueryModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setRangeCode(row.getRangeCode());
        model.setTypeCode(row.getTypeCode());
        model.setItemTable(row.getItemTable());
        model.setItemId(row.getItemId());
        model.setDevActTable(row.getDevActTable());
        model.setDevActId(row.getDevActId());
        model.setQuantity(row.getQuantity());
        model.setUnitCode(row.getUnitCode());
        return model;
    }
}
