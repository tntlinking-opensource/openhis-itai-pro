/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

/* (C)2026 */
package com.openhis.health.application.basic.supplier.service;


import com.openhis.health.application.basic.supplier.dto.SupplierDto;
import com.openhis.health.application.basic.supplier.dto.SupplierInitDto;
import com.openhis.health.application.basic.supplier.mapper.SupplierMapper;
import com.openhis.health.application.basic.supplier.request.CreateSupplierRequest;
import com.openhis.health.application.basic.supplier.request.QuerySupplierRequest;
import com.openhis.health.application.basic.supplier.request.UpdateSupplierRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.supplier.command.ActivateSupplierCommand;
import com.openhis.health.domain.custom.supplier.command.MaintainSupplierInfoCommand;
import com.openhis.health.domain.custom.supplier.command.RegisterSupplierCommand;
import com.openhis.health.domain.custom.supplier.command.RetireSupplierCommand;
import com.openhis.health.domain.custom.supplier.command.SuspendSupplierCommand;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierDomainService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商应用服务：初始化下拉、增删改查、分页与批量启停，编排 {@link SupplierDomainService} 与 {@link SupplierQueryService}。
 *
 * <p>出参经 {@link SupplierMapper} 转为应用层 DTO；机构树选项由组织查询拍平为 {@link OptionDto}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAppService {

    /** 供应商读侧：列表、分页与按 id 详情。 */
    @Autowired
    private SupplierQueryService supplierQueryService;
    /** 供应商写侧：注册、维护、启停与作废。 */
    @Autowired
    private SupplierDomainService supplierDomainService;
    /** 术语读侧：供应商类型等初始化下拉。 */
    @Autowired
    private CodeSystemQueryService codeSystemQueryService;
    /** 组织读侧：所属机构树映射为下拉。 */
    @Autowired
    private OrganizationQueryService organizationQueryService;

    /** 供应商 DTO 与领域命令映射。 */
    @Autowired
    private SupplierMapper supplierMapper;

    /**
     * 维护页初始化：供应商类型术语选项、所属机构下拉（由组织树映射）。
     *
     * @return 初始化 DTO
     */
    public SupplierInitDto init() {
        SupplierInitDto initDto = new SupplierInitDto();
        // 类型枚举走术语系统，保证与领域 SupplierType 展示一致。
        List<OptionDto> supplierTypeOptions = codeSystemQueryService.getOptionListByEnum(SupplierType.class);
        initDto.setSupplierTypeOptions(supplierTypeOptions);
        List<Organization> organizationTree = organizationQueryService.getOrganizationTree();
        // 机构树当前为平铺列表，逐项转为 OptionDto 供前端下拉。
        List<OptionDto> organizationDictDto =
            organizationTree.stream().map(supplierMapper::ConverterSupplierDto).collect(Collectors.toList());
        initDto.setOrganizationOptions(organizationDictDto);
        return initDto;
    }

    /**
     * 注册新供应商并回读实体映射为 DTO。
     *
     * @param request 创建请求
     * @return 新建供应商展示数据
     * @throws ValidationException 请求体为空或领域校验失败时
     */
    public SupplierDto createSupplier(CreateSupplierRequest request) {
        if (request == null) {
            throw new ValidationException("创建供应商失败：请求体不能为空");
        }
        RegisterSupplierCommand command = supplierMapper.ConverterRegisterSupplierCommand(request);
        // 领域注册返回技术主键，再回读实体装配完整 DTO
        Long id = supplierDomainService.registerSupplier(command);
        var supplier = supplierQueryService.findSupplierById(id);
        return supplierMapper.ConverterSupplierDto(supplier);
    }

    /**
     * 维护供应商基础信息并回读最新快照。
     *
     * @param request 编辑请求（须含主键 id）
     * @return 更新后 DTO
     * @throws ValidationException 请求体为空或主键缺失时
     * @throws RuntimeException 领域或查询失败时由当前实现包装抛出
     */
    public SupplierDto editSupplier(UpdateSupplierRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("编辑供应商失败：请求体或主键不能为空");
        }
        try {
            MaintainSupplierInfoCommand command = supplierMapper.ConverterMaintainSupplierInfoCommand(request);
            supplierDomainService.maintainSupplierInfo(command);
            var supplier = supplierQueryService.findSupplierById(request.getId());
            return supplierMapper.ConverterSupplierDto(supplier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 按主键加载供应商详情。
     *
     * @param id 供应商主键
     * @return 供应商 DTO
     * @throws ValidationException id 为空时
     * @throws EntityNotFoundException 供应商不存在时
     * @throws RuntimeException 查询失败时
     */
    public SupplierDto getSupplier(Long id) {
        if (id == null) {
            throw new ValidationException("查询供应商失败：id 不能为空");
        }
        try {
            var supplier = supplierQueryService.findSupplierById(id);
            if (supplier == null) {
                throw new EntityNotFoundException("供应商不存在或已删除", id);
            }
            return supplierMapper.ConverterSupplierDto(supplier);
        } catch (Exception e) {
            throw new RuntimeException("查询供应商失败：" + e);
        }
    }

    /**
     * 全量列表（无分页），来自查询服务列表模型映射。
     *
     * <p>注意：数据量较大时应改为分页接口 {@link #pageSupplierList(QuerySupplierRequest)}，避免内存溢出。</p>
     *
     * @return 供应商 DTO 列表；无数据时返回空列表（非 {@code null}）
     * @throws RuntimeException 查询失败
     */
    public List<SupplierDto> getSupplierList() {
        try {
            List<SupplierListItemQueryModel> supplierList = supplierQueryService.listSuppliers();
            if (supplierList == null || supplierList.isEmpty()) {
                return new ArrayList<>();
            }
            return supplierList.stream()
                .map(supplierMapper::ConverterSupplierDto)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("查询供应商失败：" + e);
        }
    }

    /**
     * 分页查询供应商，关键字与页码取自 {@link QuerySupplierRequest}。
     *
     * @param request 分页与检索条件；{@code null} 时使用默认第 1 页、每页 50 条
     * @return 分页结果
     * @throws RuntimeException 查询失败
     */
    public PagedResult<SupplierDto> pageSupplierList(QuerySupplierRequest request) {
        if (request == null) {
            request = new QuerySupplierRequest();
            request.setPageNo(1);
            request.setPageSize(50);
        }
        try {
            int pageIndex = request.springPageIndex();
            int pageSize = request.getBoundedPageSize();
            Page<SupplierListItemQueryModel> page =
                supplierQueryService.pageSuppliers(request.getKeyword(), pageIndex, pageSize);
            return PagedResult.fromPage(page.map(supplierMapper::ConverterSupplierDto));
        } catch (Exception e) {
            throw new RuntimeException("查询供应商失败：" + e);
        }
    }

    /**
     * 作废（停用语义由领域 retire 承载）供应商后回读实体。
     *
     * <p>注意：领域作废后该供应商对后续业务不可见，但历史数据仍保留。</p>
     *
     * @param id 供应商主键
     * @return 操作后 DTO（以查询服务为准，可能为 null 表示已不可见）
     * @throws ValidationException id 为空时
     * @throws RuntimeException 领域或查询失败
     */
    public SupplierDto deleteSupplier(Long id) {
        if (id == null) {
            throw new ValidationException("删除供应商失败：id 不能为空");
        }
        try {
            RetireSupplierCommand command = new RetireSupplierCommand();
            RetireSupplierCommand.RetireSupplierItemCommand item = new RetireSupplierCommand.RetireSupplierItemCommand();
            item.setSupplierId(id);
            command.setItems(List.of(item));
            supplierDomainService.retireSupplier(command);
            var supplier = supplierQueryService.findSupplierById(id);
            if (supplier == null) {
                return null;
            }
            return supplierMapper.ConverterSupplierDto(supplier);
        } catch (Exception e) {
            throw new RuntimeException("删除供应商失败：" + e);
        }
    }

    /**
     * 批量启用供应商，将列表中每个 id 展开为激活命令项后批量下发。
     *
     * @param ids 主键列表，须非空
     * @return 实际处理的条数（已过滤 null 元素）
     * @throws ValidationException ids 为空或全部无效时
     * @throws RuntimeException 领域失败
     */
    public int batchEnableSupplier(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("批量启用供应商失败：ids 不能为空");
        }
        try {
            List<Long> validIds = ids.stream().filter(id -> id != null).toList();
            if (validIds.isEmpty()) {
                throw new ValidationException("批量启用供应商失败：ids 中无有效主键");
            }
            ActivateSupplierCommand command = new ActivateSupplierCommand();
            command.setItems(validIds.stream().map(id -> {
                ActivateSupplierCommand.ActivateSupplierItemCommand item = new ActivateSupplierCommand.ActivateSupplierItemCommand();
                item.setSupplierId(id);
                return item;
            }).toList());
            supplierDomainService.activateSupplier(command);
            return validIds.size();
        } catch (Exception e) {
            throw new RuntimeException("批量启用供应商失败：" + e);
        }
    }

    /**
     * 批量暂停（停用）供应商，将列表中每个 id 展开为暂停命令项后批量下发。
     *
     * @param ids 主键列表，须非空
     * @return 实际处理的条数（已过滤 null 元素）
     * @throws ValidationException ids 为空或全部无效时
     * @throws RuntimeException 领域失败
     */
    public int batchDisableSupplier(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("批量停用供应商失败：ids 不能为空");
        }
        try {
            List<Long> validIds = ids.stream().filter(id -> id != null).toList();
            if (validIds.isEmpty()) {
                throw new ValidationException("批量停用供应商失败：ids 中无有效主键");
            }
            SuspendSupplierCommand command = new SuspendSupplierCommand();
            command.setItems(validIds.stream().map(id -> {
                SuspendSupplierCommand.SuspendSupplierItemCommand item = new SuspendSupplierCommand.SuspendSupplierItemCommand();
                item.setSupplierId(id);
                return item;
            }).toList());
            supplierDomainService.suspendSupplier(command);
            return validIds.size();
        } catch (Exception e) {
            throw new RuntimeException("批量停用供应商失败：" + e);
        }
    }
}
