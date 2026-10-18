/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.supplier.service;


import com.zxplat.application.basic.supplier.dto.SupplierDto;
import com.zxplat.application.basic.supplier.dto.SupplierInitDto;
import com.zxplat.application.basic.supplier.mapper.SupplierMapper;
import com.zxplat.application.basic.supplier.request.CreateSupplierRequest;
import com.zxplat.application.basic.supplier.request.QuerySupplierRequest;
import com.zxplat.application.basic.supplier.request.UpdateSupplierRequest;
import com.zxplat.application.basic.supplier.support.SupplierBusNoSequenceSync;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.custom.supplier.command.ActivateSupplierCommand;
import com.zxplat.domain.custom.supplier.command.MaintainSupplierInfoCommand;
import com.zxplat.domain.custom.supplier.command.RegisterSupplierCommand;
import com.zxplat.domain.custom.supplier.command.RetireSupplierCommand;
import com.zxplat.domain.custom.supplier.command.SuspendSupplierCommand;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.custom.supplier.service.SupplierDomainService;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.share.enums.SupplierType;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.ILoginOrganizationScopeQuery;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
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
     * 当前登录会话机构解析（B.6.5）：禁止 AppService 直注 ICurrentUser；
     * 创建供应商缺省部门时取会话医院（非科室）。
     */
    @Autowired
    private CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * 登录组织树范围：自科室向上解析所属医院，保证供应商 orgId 落医院级。
     */
    @Autowired(required = false)
    private ILoginOrganizationScopeQuery loginOrganizationScopeQuery;

    /**
     * 注册前对齐 SP 序号：迁入历史 bus_no 高于序列时避免「生成供应商编号失败」。
     */
    @Autowired
    private SupplierBusNoSequenceSync supplierBusNoSequenceSync;

    /**
     * 维护页初始化：供应商类型术语选项、所属机构下拉（由组织树映射）。
     *
     * @return 初始化 DTO
     */
    public SupplierInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        SupplierInitDto initDto = new SupplierInitDto();
        // Pre：加载供应商类型术语与机构树
        List<OptionDto> supplierTypeOptions = codeSystemQueryService.getOptionListByEnum(SupplierType.class);
        initDto.setSupplierTypeOptions(supplierTypeOptions);
        List<Organization> organizationTree = organizationQueryService.getOrganizationTree();
        List<OptionDto> organizationDictDto =
            organizationTree.stream().map(supplierMapper::ConverterSupplierDto).collect(Collectors.toList());
        initDto.setOrganizationOptions(organizationDictDto);
        // Asm：映射维护页初始化 DTO
        return initDto;
    }

    /**
     * 注册新供应商并回读实体映射为 DTO。
     *
     * <p>领域层注册完成后即为启用态（ACTIVE），前端无需再调批量启用。</p>
     * <p>部门固定落医院级：缺省取会话医院；若传入科室则沿组织树上溯至 HOSPITAL，禁止落科室。</p>
     *
     * @param request 创建请求
     * @return 新建供应商展示数据
     * @throws ValidationException 请求体为空、无法解析会话医院或领域校验失败时
     */
    public SupplierDto createSupplier(CreateSupplierRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new ValidationException("创建供应商失败：请求体不能为空");
        }
        // Pre：部门归一为医院（会话医院或缺省上溯），禁止落库科室 / orgId=0
        this.resolveHospitalOrgIdForCreate(request);
        // Pre：独立事务对齐 SP 序号，避免迁入数据导致采番撞号
        supplierBusNoSequenceSync.syncFromExistingSuppliers();
        // Pre：请求映射为领域注册命令
        RegisterSupplierCommand command = supplierMapper.ConverterRegisterSupplierCommand(request);
        // Cmd：领域注册供应商
        Long id = supplierDomainService.registerSupplier(command);
        // Pre：回读实体快照
        var supplier = supplierQueryService.findSupplierById(id);
        // Asm：映射展示 DTO
        return supplierMapper.ConverterSupplierDto(supplier);
    }

    /**
     * 将创建请求的 orgId 归一为医院：优先会话医院缺省；已传值则上溯到 HOSPITAL。
     *
     * @param request 创建请求（就地回填医院 orgId）
     * @throws ValidationException 无法解析医院时
     */
    private void resolveHospitalOrgIdForCreate(CreateSupplierRequest request) {
        Long candidate = request.getOrgId();
        // 缺省或占位 0：取登录选院，绝不回落会话科室
        if (candidate == null || candidate == 0L) {
            candidate = managedOrganizationIdsResolver.resolveSessionHospitalIdOrNull();
        }
        if (candidate == null || candidate == 0L) {
            throw new ValidationException("创建供应商失败：无法解析当前登录医院，请先选择医院");
        }
        Long hospitalId = candidate;
        // 已传科室时沿组织树上溯到医院根，避免供应商挂在科室节点
        if (loginOrganizationScopeQuery != null) {
            hospitalId = loginOrganizationScopeQuery.resolveHospitalOrganizationId(candidate)
                .orElse(null);
            if (hospitalId == null) {
                hospitalId = managedOrganizationIdsResolver.resolveSessionHospitalIdOrNull();
            }
        }
        if (hospitalId == null || hospitalId == 0L) {
            throw new ValidationException("创建供应商失败：无法解析医院级机构，请选择医院");
        }
        request.setOrgId(hospitalId);
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
        // Gate：请求体与主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("编辑供应商失败：请求体或主键不能为空");
        }
        try {
            // Pre：请求映射为维护命令
            MaintainSupplierInfoCommand command = supplierMapper.ConverterMaintainSupplierInfoCommand(request);
            // Cmd：领域维护供应商信息
            supplierDomainService.maintainSupplierInfo(command);
            // Pre：回读最新快照
            var supplier = supplierQueryService.findSupplierById(request.getId());
            // Asm：映射展示 DTO
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
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("查询供应商失败：id 不能为空");
        }
        try {
            // Pre：按 id 加载供应商
            var supplier = supplierQueryService.findSupplierById(id);
            if (supplier == null) {
                throw new EntityNotFoundException("供应商不存在或已删除", id);
            }
            // Asm：映射展示 DTO
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
            // Pre：读侧全量列表（无分页）
            List<SupplierListItemQueryModel> supplierList = supplierQueryService.listSuppliers();
            if (supplierList == null || supplierList.isEmpty()) {
                return new ArrayList<>();
            }
            // Asm：映射 DTO 列表
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
        // Gate：请求体缺省时补默认分页窗口
        if (request == null) {
            request = new QuerySupplierRequest();
            request.setPageNo(1);
            request.setPageSize(50);
        }
        try {
            int pageIndex = request.springPageIndex();
            int pageSize = request.getBoundedPageSize();
            // Pre：读侧分页查询
            Page<SupplierListItemQueryModel> page =
                supplierQueryService.pageSuppliers(request.getKeyword(), pageIndex, pageSize);
            // Asm：映射分页 DTO
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
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("删除供应商失败：id 不能为空");
        }
        try {
            // Cmd：领域作废供应商
            RetireSupplierCommand command = new RetireSupplierCommand();
            RetireSupplierCommand.RetireSupplierItemCommand item = new RetireSupplierCommand.RetireSupplierItemCommand();
            item.setSupplierId(id);
            command.setItems(List.of(item));
            supplierDomainService.retireSupplier(command);
            // Pre：回读作废后快照（可能已不可见）
            var supplier = supplierQueryService.findSupplierById(id);
            if (supplier == null) {
                return null;
            }
            // Asm：映射展示 DTO
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
        // Gate：批量主键列表非空
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("批量启用供应商失败：ids 不能为空");
        }
        try {
            List<Long> validIds = ids.stream().filter(id -> id != null).toList();
            if (validIds.isEmpty()) {
                throw new ValidationException("批量启用供应商失败：ids 中无有效主键");
            }
            // Pre：组装批量激活命令
            ActivateSupplierCommand command = new ActivateSupplierCommand();
            command.setItems(validIds.stream().map(id -> {
                ActivateSupplierCommand.ActivateSupplierItemCommand item = new ActivateSupplierCommand.ActivateSupplierItemCommand();
                item.setSupplierId(id);
                return item;
            }).toList());
            // Cmd：领域批量启用
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
        // Gate：批量主键列表非空
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("批量停用供应商失败：ids 不能为空");
        }
        try {
            List<Long> validIds = ids.stream().filter(id -> id != null).toList();
            if (validIds.isEmpty()) {
                throw new ValidationException("批量停用供应商失败：ids 中无有效主键");
            }
            // Pre：组装批量暂停命令
            SuspendSupplierCommand command = new SuspendSupplierCommand();
            command.setItems(validIds.stream().map(id -> {
                SuspendSupplierCommand.SuspendSupplierItemCommand item = new SuspendSupplierCommand.SuspendSupplierItemCommand();
                item.setSupplierId(id);
                return item;
            }).toList());
            // Cmd：领域批量暂停
            supplierDomainService.suspendSupplier(command);
            return validIds.size();
        } catch (Exception e) {
            throw new RuntimeException("批量停用供应商失败：" + e);
        }
    }
}
