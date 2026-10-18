/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.labdevice.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.basic.labdevice.assembler.LabDeviceDisplayAssembler;
import com.zxplat.application.basic.labdevice.dto.LabDeviceDto;
import com.zxplat.application.basic.labdevice.dto.LabDeviceInitDto;
import com.zxplat.application.basic.labdevice.mapper.LabDeviceMapper;
import com.zxplat.application.basic.labdevice.request.CreateLabDeviceRequest;
import com.zxplat.application.basic.labdevice.request.PageLabDeviceRequest;
import com.zxplat.application.basic.labdevice.request.SearchLabDeviceRequest;
import com.zxplat.application.basic.labdevice.request.UpdateLabDeviceRequest;
import com.zxplat.application.shared.converter.TextValueConverter;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.administration.labdevice.command.ActivateLabDevicesCommand;
import com.zxplat.domain.administration.labdevice.command.DeactivateLabDevicesCommand;
import com.zxplat.domain.administration.labdevice.command.DeleteLabDeviceCommand;
import com.zxplat.domain.administration.labdevice.queries.PageLabDeviceQuery;
import com.zxplat.domain.administration.labdevice.queries.SearchLabDeviceQuery;
import com.zxplat.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.zxplat.domain.administration.labdevice.queries.model.LabDeviceSearchItemQueryModel;
import com.zxplat.domain.administration.labdevice.service.LabDeviceDomainService;
import com.zxplat.domain.administration.labdevice.service.LabDeviceQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.SupplierType;
import com.zxplat.domain.share.utils.OptionDtoLegacyRowUtil;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基础数据「检验设备」应用服务（AS-BAS-MST-020）。
 *
 * <p>编排 {@link LabDeviceQueryService} 与 {@link LabDeviceDomainService}；
 * 接入方式：{@code POST /api/app/labDevice/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabDeviceAppService {

    private final LabDeviceQueryService labDeviceQueryService;
    private final LabDeviceDomainService labDeviceDomainService;
    private final LabDeviceMapper labDeviceMapper;
    private final LabDeviceDisplayAssembler labDeviceDisplayAssembler;
    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final OrganizationQueryService organizationQueryService;
    private final SupplierQueryService supplierQueryService;
    /** 写路径操作人门控（B.6.5），禁止 AppService 直读 ICurrentUser。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 返回检验设备维护页初始化下拉。
     *
     * @return 初始化 DTO
     */
    public LabDeviceInitDto init() {
        LabDeviceInitDto dto = new LabDeviceInitDto();
        // Pre：术语与主数据下拉，供维护页筛选与表单
        dto.setCategoryCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.ADM_DEVICE_CATEGORY_CODE));
        dto.setStatusEnumOptions(codeSystemQueryService.getOptionListByEnum(PublicationStatus.class));
        dto.setOrganizationOptions(this.loadDepartmentOrganizationOptions());
        dto.setManufacturerOptions(this.loadManufacturerLegacyRows());
        // Asm：返回初始化 DTO
        return dto;
    }

    /**
     * 分页查询检验设备列表。
     *
     * @param request 分页与筛选条件
     * @return 分页 DTO
     */
    public PagedResult<LabDeviceDto> page(PageLabDeviceRequest request) {
        PageLabDeviceRequest safeRequest = ensurePageRequest(request);
        // Gate：归一化分页窗口，避免非法页码导致读侧异常
        int pageSize = 50;
        if (safeRequest.getPageSize() != null && safeRequest.getPageSize() > 0) {
            pageSize = safeRequest.getPageSize();
        }
        // Pre：HTTP 请求转为领域分页查询入参
        PageLabDeviceQuery query = new PageLabDeviceQuery();
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        query.setStatusEnum(trimToNull(safeRequest.getStatusEnum()));
        query.setCategoryCode(trimToNull(safeRequest.getCategoryCode()));
        query.setOrgId(TextValueConverter.toNullableLong(safeRequest.getOrgId()));
        // Cmd：单次 SQL 分页查询
        Page<LabDeviceDto> page = labDeviceQueryService.pageLabDevice(
            query,
            PageRequest.of(safeRequest.springPageIndex(), pageSize))
            .map(labDeviceMapper::toDto);
        // Asm：批量 enrich 科室/厂家名并转为统一分页响应
        labDeviceDisplayAssembler.enrichRows(page.getContent());
        return PagedResult.fromPage(page);
    }

    /**
     * 按主键查询检验设备详情。
     *
     * @param id 主键
     * @return 详情 DTO
     */
    public LabDeviceDto getLabDevice(Long id) {
        // Gate：主键不能为空，否则无法定位台账行
        if (id == null) {
            throw new ValidationException("查询检验设备失败：id 不能为空");
        }
        // Pre：读侧按主键装载详情读模型
        LabDeviceDetailQueryModel model = labDeviceQueryService.getLabDevice(id);
        if (model == null) {
            throw new EntityNotFoundException("检验设备不存在或已删除");
        }
        // Asm：读模型投影为对外 DTO 并 enrich 展示名
        LabDeviceDto dto = labDeviceMapper.toDto(model);
        labDeviceDisplayAssembler.enrichRows(Collections.singletonList(dto));
        return dto;
    }

    /**
     * 新建检验设备并回读详情。
     *
     * @param request 创建请求
     * @return 新建后详情 DTO
     */
    public LabDeviceDto createLabDevice(CreateLabDeviceRequest request) {
        // Gate：名称等必填项校验，避免脏数据落库
        validateCreateRequest(request);
        // Cmd：领域层生成 busNo 并持久化
        Long id = labDeviceDomainService.createLabDevice(labDeviceMapper.toCreateCommand(request));
        log.info("Created lab device: {}", id);
        // Asm：回读详情供前端表单回显
        return getLabDevice(id);
    }

    /**
     * 维护检验设备并回读详情。
     *
     * @param request 更新请求
     * @return 更新后详情 DTO
     */
    public LabDeviceDto updateLabDevice(UpdateLabDeviceRequest request) {
        // Gate：主键与名称校验
        validateUpdateRequest(request);
        // Cmd：领域层维护台账字段
        labDeviceDomainService.updateLabDevice(labDeviceMapper.toUpdateCommand(request));
        log.info("Updated lab device: {}", request.getId());
        // Asm：回读详情
        return getLabDevice(request.getId());
    }

    /**
     * 逻辑删除检验设备。
     *
     * @param id 主键
     */
    public void deleteLabDevice(Long id) {
        // Gate：主键与登录操作人校验
        if (id == null) {
            throw new ValidationException("删除检验设备失败：id 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT,
            "请先登录后再删除检验设备");
        // Cmd：组装删除命令并逻辑删除
        DeleteLabDeviceCommand command = new DeleteLabDeviceCommand();
        command.setOperatorId(operatorId);
        DeleteLabDeviceCommand.DeleteLabDeviceItemCommand item = new DeleteLabDeviceCommand.DeleteLabDeviceItemCommand();
        item.setId(id);
        command.setItems(Collections.singletonList(item));
        labDeviceDomainService.deleteLabDevice(command);
        log.info("Deleted lab device: {}", id);
    }

    /**
     * 启用单条检验设备。
     *
     * @param id 主键
     * @return 启用后详情 DTO
     */
    public LabDeviceDto enableLabDevice(Long id) {
        // Gate：主键不能为空
        if (id == null) {
            throw new ValidationException("启用检验设备失败：id 不能为空");
        }
        // Cmd：批量启用（单条复用）
        batchEnable(Collections.singletonList(id));
        // Asm：回读详情
        return getLabDevice(id);
    }

    /**
     * 停用单条检验设备。
     *
     * @param id 主键
     * @return 停用后详情 DTO
     */
    public LabDeviceDto disableLabDevice(Long id) {
        // Gate：主键不能为空
        if (id == null) {
            throw new ValidationException("停用检验设备失败：id 不能为空");
        }
        // Cmd：批量停用（单条复用）
        batchDisable(Collections.singletonList(id));
        // Asm：回读详情
        return getLabDevice(id);
    }

    /**
     * 批量启用检验设备。
     *
     * @param ids 主键列表
     */
    public void batchEnable(List<Long> ids) {
        // Gate：批量 id 不能为空
        requireIds(ids, "启用检验设备失败：ids 不能为空");
        // Cmd：组装启用命令并提交领域层
        ActivateLabDevicesCommand command = new ActivateLabDevicesCommand();
        List<ActivateLabDevicesCommand.ActivateLabDeviceItemCommand> items = new ArrayList<>();
        // Loop：逐 id 组装批量启停项
        for (Long id : ids) {
            ActivateLabDevicesCommand.ActivateLabDeviceItemCommand item =
                new ActivateLabDevicesCommand.ActivateLabDeviceItemCommand();
            item.setId(id);
            items.add(item);
        }
        command.setItems(items);
        labDeviceDomainService.activateLabDevices(command);
    }

    /**
     * 批量停用检验设备。
     *
     * @param ids 主键列表
     */
    public void batchDisable(List<Long> ids) {
        // Gate：批量 id 不能为空
        requireIds(ids, "停用检验设备失败：ids 不能为空");
        // Cmd：组装停用命令并提交领域层
        DeactivateLabDevicesCommand command = new DeactivateLabDevicesCommand();
        List<DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand> items = new ArrayList<>();
        // Loop：逐 id 组装批量启停项
        for (Long id : ids) {
            DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand item =
                new DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand();
            item.setId(id);
            items.add(item);
        }
        command.setItems(items);
        labDeviceDomainService.deactivateLabDevices(command);
    }

    /**
     * 关键字检索检验设备，供下拉选品。
     *
     * @param request 检索条件
     * @return 命中列表
     */
    public List<LabDeviceDto> search(SearchLabDeviceRequest request) {
        SearchLabDeviceRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new SearchLabDeviceRequest();
        }
        // Pre：检索条件转为领域查询
        SearchLabDeviceQuery query = new SearchLabDeviceQuery();
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        query.setStatusEnum(trimToNull(safeRequest.getStatusEnum()));
        query.setCategoryCode(trimToNull(safeRequest.getCategoryCode()));
        query.setLimit(safeRequest.getLimit());
        List<LabDeviceSearchItemQueryModel> models = labDeviceQueryService.searchByKeyWord(query);
        // Asm：读模型逐条投影为 DTO
        List<LabDeviceDto> result = new ArrayList<>();
        for (LabDeviceSearchItemQueryModel model : models) {
            LabDeviceDto dto = labDeviceMapper.toDto(model);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 归一化分页请求；null 时使用默认第 1 页、每页 50 条。
     *
     * @param request 原始分页请求
     * @return 非空分页请求
     */
    private PageLabDeviceRequest ensurePageRequest(PageLabDeviceRequest request) {
        if (request != null) {
            return request;
        }
        PageLabDeviceRequest safeRequest = new PageLabDeviceRequest();
        safeRequest.setPageNo(1);
        safeRequest.setPageSize(50);
        return safeRequest;
    }

    /**
     * 新建请求门禁：名称必填。
     *
     * @param request 创建请求
     */
    private void validateCreateRequest(CreateLabDeviceRequest request) {
        if (request == null) {
            throw new ValidationException("新建检验设备失败：请求体不能为空");
        }
        if (StringUtils.isEmpty(request.getName())) {
            throw new ValidationException("新建检验设备失败：设备名称不能为空");
        }
    }

    /**
     * 维护请求门禁：主键与名称必填。
     *
     * @param request 更新请求
     */
    private void validateUpdateRequest(UpdateLabDeviceRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("维护检验设备失败：id 不能为空");
        }
        if (StringUtils.isEmpty(request.getName())) {
            throw new ValidationException("维护检验设备失败：设备名称不能为空");
        }
    }

    /**
     * 批量操作门禁：id 列表不能为空。
     *
     * @param ids 主键列表
     * @param errorMessage 为空时的提示文案
     */
    private void requireIds(List<Long> ids, String errorMessage) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(errorMessage);
        }
    }

    /**
     * 加载责任科室树为下拉选项（维护页归属科室）。
     *
     * @return 科室下拉；无数据时为空列表
     */
    private List<OptionDto> loadDepartmentOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationTreeForResponsibilityDept();
        List<OptionDto> options = new ArrayList<>();
        if (organizations == null || organizations.isEmpty()) {
            return options;
        }
        long sort = 0L;
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            sort++;
            options.add(new OptionDto(sort, String.valueOf(organization.getId()), organization.getName()));
        }
        return options;
    }

    /**
     * 加载启用态生产厂家为可拼音检索的 label/value 行（兼容旧前端下拉）。
     *
     * @return 生产厂家下拉行；无数据时为空列表
     */
    private List<Map<String, Object>> loadManufacturerLegacyRows() {
        List<SupplierListItemQueryModel> manufacturers =
            supplierQueryService.listSuppliersByTypeAndActive(SupplierType.MANUFACTURER.getCode());
        List<OptionDto> options = new ArrayList<>();
        if (manufacturers == null || manufacturers.isEmpty()) {
            return OptionDtoLegacyRowUtil.toSearchableLabelValueRows(options);
        }
        for (SupplierListItemQueryModel item : manufacturers) {
            if (item == null || item.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(item.getId()));
            option.setLabel(item.getName());
            option.setBusNo(item.getBusNo());
            if (StringUtils.isNotEmpty(item.getName())) {
                option.setPyStr(SearchCodeUtil.toPyStr(item.getName()));
                option.setWbStr(SearchCodeUtil.toWbStr(item.getName()));
            }
            options.add(option);
        }
        return OptionDtoLegacyRowUtil.toSearchableLabelValueRows(options);
    }
}
