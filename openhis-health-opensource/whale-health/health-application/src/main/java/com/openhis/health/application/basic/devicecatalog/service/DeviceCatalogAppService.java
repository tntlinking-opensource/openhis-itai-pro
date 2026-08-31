/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.service;

import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.CatalogEditabilityConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.shared.pricing.ChargePricingCommandAssembler;
import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.basic.catalog.dto.CatalogLotPriceRowDto;
import com.openhis.health.application.basic.catalog.support.CatalogEditWriteGate;
import com.openhis.health.application.basic.catalog.support.CatalogLotPriceAssembler;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogDto;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogImportFileResponse;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogImportProgressResponse;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogInitDto;
import com.openhis.health.application.basic.devicecatalog.dto.ExportDeviceCatalogResponse;
import com.openhis.health.application.basic.devicecatalog.dto.ImportDeviceCatalogErrorItemDto;
import com.openhis.health.application.basic.devicecatalog.dto.ImportDeviceCatalogResponse;
import com.openhis.health.application.basic.devicecatalog.dto.PageDeviceCatalogResponse;
import com.openhis.health.application.basic.devicecatalog.dto.StartDeviceCatalogImportResponse;
import com.openhis.health.application.basic.devicecatalog.mapper.DeviceCatalogMapper;
import com.openhis.health.application.basic.devicecatalog.request.CreateDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.DownloadDeviceCatalogImportErrorRequest;
import com.openhis.health.application.basic.devicecatalog.request.ExportDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.GetDeviceCatalogImportProgressRequest;
import com.openhis.health.application.basic.devicecatalog.request.ImportDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.ListDeviceLotPriceRequest;
import com.openhis.health.application.basic.devicecatalog.request.PageDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.UpdateDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogBusNoSequenceSync;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogExportRowAssembler;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportConceptEnsureService;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportErrorFileService;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportJob;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportRow;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportRowValidator;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportTemplateBuilder;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportTerminologyIndex;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportWorkbookParser;
import com.openhis.health.application.basic.devicecatalog.support.DeviceCatalogImportWriteSupport;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.devicedefinition.command.*;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionDomainService;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.utils.OptionDtoLegacyRowUtil;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityQueryModel;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityResolver;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.util.ChineseConvertUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 耗材（器材）目录管理应用服务：分页列表、详情、批号真实进价/售价查询、增删改、批量启停、收费定价行维护与批量导入。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>网关字符串 ID → long、批量 ID 合法性：委托 {@link GatewayIdParseUtil}；</li>
 *   <li>定价金额按元写入 {@code adm_charge_item_definition}；批量写入定价行：{@link ChargePricingCommandAssembler}；</li>
 *   <li>维护态 {@code PURCHASE}/{@code SALE} 为采购参考价/销售参考价；真实售卖价见 {@link #listDeviceLotPrices}；</li>
 *   <li>领域持久化与状态迁移：{@link DeviceDefinitionDomainService}；定价明细：{@link ChargeItemDefinitionDomainService}；</li>
 *   <li>批量导入镜像药品目录：解析 → 全量校验 → 复用 {@link #createOneForImport} 写路径。</li>
 * </ul>
 *
 * <p><b>接入方式（项目约定）</b>：由 {@code whale-web} 的 {@code AppServiceControllerRegistrar} 将 {@code *AppService}
 * 请勿为本类另写 {@code @RestController} 破坏统一网关模型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCatalogAppService {

    /** 耗材定义读侧：分页与按 id 取实体。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    /** 耗材定义写侧：创建、更新、批量启停删。 */
    private final DeviceDefinitionDomainService deviceDefinitionDomainService;
    /** 收费定价行读侧。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 收费定价行写侧：实例维度替换定价、批量保存。 */
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    /** 请求/实体 ↔ DTO 与领域命令映射。 */
    private final DeviceCatalogMapper deviceCatalogMapper;
    /** 术语体系名称解析：下拉展示文案与数值编码分离。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 供应商主数据列表 → 生产厂家/供应商下拉（与药品目录初始化一致）。 */
    private final SupplierQueryService supplierQueryService;
    /** 目录编辑业务码读侧编排。 */
    private final CatalogEditabilityResolver catalogEditabilityResolver;
    /** 目录写侧 immutable 字段门禁。 */
    private final CatalogEditWriteGate catalogEditWriteGate;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 会话科室收窄（导入写路径绑定机构）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 导入模板生成。 */
    private final DeviceCatalogImportTemplateBuilder deviceCatalogImportTemplateBuilder;
    /** 导入 xlsx 解析。 */
    private final DeviceCatalogImportWorkbookParser deviceCatalogImportWorkbookParser;
    /** 导入行校验。 */
    private final DeviceCatalogImportRowValidator deviceCatalogImportRowValidator;
    /** 导入术语索引。 */
    private final DeviceCatalogImportTerminologyIndex deviceCatalogImportTerminologyIndex;
    /** 导入前补齐缺失 unit_code。 */
    private final DeviceCatalogImportConceptEnsureService deviceCatalogImportConceptEnsureService;
    /** 导入失败标注文件。 */
    private final DeviceCatalogImportErrorFileService deviceCatalogImportErrorFileService;
    /** 导入事务写段。 */
    private final DeviceCatalogImportWriteSupport deviceCatalogImportWriteSupport;
    /** 导入异步工作流。 */
    private final DeviceCatalogImportAsyncWorkflow deviceCatalogImportAsyncWorkflow;
    /** 新建/导入前对齐 DD 序号。 */
    private final DeviceCatalogBusNoSequenceSync deviceCatalogBusNoSequenceSync;
    /** 耗材目录导出行组装。 */
    private final DeviceCatalogExportRowAssembler deviceCatalogExportRowAssembler;

    /**
     * 耗材维护页初始化：单位类下拉、供应商列表、生产厂家（type=1）、耗材分类（{@code device_category_code}）、耗材种类（{@code device_type_code}）、财务类型（{@code fin_type_code}）、医保类别/等级等。
     *
     * <p>返回结构中部分列表为 {@link OptionDtoLegacyRowUtil} 映射的 label/value 行，兼容旧前端表格数据源。</p>
     *
     * @return 各计量维度下拉数据聚合（若术语缺失则对应列为空列表）
     */
    public DeviceCatalogInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        DeviceCatalogInitDto dto = DeviceCatalogInitDto.empty();

        // Pre：加载计量单位、供应商/生产厂家与耗材分类/种类/财务类型下拉
        // 计量单位统一 unit_code；剂量单独 dose_unit
        List<Map<String, Object>> unitOptions =
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.UNIT_CODE);
        dto.setUnitCodeOptions(unitOptions);
        dto.setPackUnits(unitOptions);
        dto.setSaleUnits(unitOptions);
        dto.setMinUnits(unitOptions);
        dto.setStockUnits(unitOptions);
        dto.setDoseUnits(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.DOSE_UNIT));

        dto.setSupplierListOptions(loadSupplierLegacyRows());
        // 生产厂家：仅 type=1 启用主数据，与进货供应商（type=2）分离
        dto.setManufacturerOptions(loadManufacturerLegacyRows());
        dto.setDeviceCategories(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        dto.setEquipmentTypes(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE));
        dto.setFinancialTypes(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE));
        dto.setYbCategories(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.MED_CHRGITM_TYPE));
        dto.setYbGrades(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.CHRGITM_LV));
        dto.setMrSettlementTypeCodeOptions(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.MR_SETTLEMENT_TYPE_CODE));

        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 加载供应商列表为旧前端约定的 label/value 行（value 为供应商 id 字符串）。
     *
     * @return 供应商下拉行列表，无数据时为空列表
     */
    private List<Map<String, Object>> loadSupplierLegacyRows() {
        List<SupplierListItemQueryModel> suppliers = supplierQueryService.listSuppliers();
        List<OptionDto> options = OptionDtoAssembler.toOptions(
            suppliers,
            SupplierListItemQueryModel::getId,
            SupplierListItemQueryModel::getName,
            0L);
        return OptionDtoLegacyRowUtil.toLabelValueRows(options);
    }

    /**
     * 加载启用态生产厂家下拉为可拼音/五笔过滤的 label/value 行。
     *
     * <p>value=供应商主键字符串；迁移占位拼音 {@code MFG} 按名称现场重算。</p>
     *
     * @return 生产厂家下拉行列表；无数据时为空列表
     */
    private List<Map<String, Object>> loadManufacturerLegacyRows() {
        List<SupplierListItemQueryModel> manufacturers =
            supplierQueryService.listSuppliersByTypeAndActive(SupplierType.MANUFACTURER.getCode());
        List<OptionDto> options = new ArrayList<>();
        if (manufacturers == null || manufacturers.isEmpty()) {
            return OptionDtoLegacyRowUtil.toSearchableLabelValueRows(options);
        }
        // Loop：装配可检索厂家选项（名称 + 拼音/五笔 + 编号）
        for (SupplierListItemQueryModel item : manufacturers) {
            if (item == null || item.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(item.getId()));
            option.setLabel(item.getName());
            option.setBusNo(item.getBusNo());
            option.setPyStr(this.resolveManufacturerPyStr(item));
            option.setWbStr(this.resolveManufacturerWbStr(item));
            options.add(option);
        }
        return OptionDtoLegacyRowUtil.toSearchableLabelValueRows(options);
    }

    /**
     * 解析厂家拼音检索码：空或迁移占位 {@code MFG} 时按名称重算。
     *
     * @param item 厂家列表行
     * @return 拼音码；名称也空时返回原值（可空）
     */
    private String resolveManufacturerPyStr(SupplierListItemQueryModel item) {
        if (item == null) {
            return null;
        }
        String pyStr = item.getPyStr();
        if (StringUtils.isNotEmpty(pyStr) && !"MFG".equals(pyStr)) {
            return pyStr;
        }
        String name = item.getName();
        if (StringUtils.isEmpty(name)) {
            return pyStr;
        }
        return ChineseConvertUtils.toPinyinFirstLetter(name.trim());
    }

    /**
     * 解析厂家五笔检索码：空或迁移占位 {@code MFG} 时按名称重算。
     *
     * @param item 厂家列表行
     * @return 五笔码；名称也空时返回原值（可空）
     */
    private String resolveManufacturerWbStr(SupplierListItemQueryModel item) {
        if (item == null) {
            return null;
        }
        String wbStr = item.getWbStr();
        if (StringUtils.isNotEmpty(wbStr) && !"MFG".equals(wbStr)) {
            return wbStr;
        }
        String name = item.getName();
        if (StringUtils.isEmpty(name)) {
            return wbStr;
        }
        return ChineseConvertUtils.toWBFirstLetter(name.trim());
    }

    /**
     * 分页查询耗材目录。
     *
     * <p>页码与 Spring Data 约定对齐：请求体使用 1-based 页号，领域查询接口使用 0-based page index。</p>
     *
     * @param request 分页参数（可为 {@code null}，此时使用默认页码与页大小）
     * @return 当前页内容与总条数、总页数
     */
    public PageDeviceCatalogResponse pageDeviceCatalog(PageDeviceCatalogRequest request) {
        // Gate：分页参数归一（非法值回落默认）
        int pageNo = 1;
        if (request != null && request.getPageNo() != null) {
            pageNo = request.getPageNo();
        }
        int pageSize = 50;
        if (request != null && request.getPageSize() != null) {
            pageSize = request.getPageSize();
        }
        // 防御性约束：非法分页参数回落到安全默认值，避免查询侧出现负数或零页大小。
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize < 1) {
            pageSize = 50;
        }
        String keyword = null;
        String categoryCode = null;
        String typeCode = null;
        if (request != null) {
            keyword = request.getKeyword();
            categoryCode = request.getCategoryCode();
            typeCode = request.getTypeCode();
        }
        // Pre：读侧分页并 enrich 定价与字典文案
        Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
            pageNo - 1, pageSize, keyword, categoryCode, typeCode);
        List<DeviceCatalogDto> content = page.getContent().stream()
            .filter(Objects::nonNull)
            .map(deviceCatalogMapper::toDto)
            .collect(Collectors.toList());
        this.enrichPageFields(content);

        // Asm：PagedResultBuilder 构建分页响应
        return PagedResultBuilder.build(
            new PageDeviceCatalogResponse(),
            content,
            page.getTotalElements(),
            pageNo,
            pageSize
        );
    }

    /**
     * 按字符串主键查询耗材详情，并合并收费定价行到 DTO。
     *
     * @param id 网关传入的设备主键字符串（非空且可解析为 long）
     * @return 设备目录 DTO（含采购参考价、销售参考价、限价等回填字段）
     * @throws ValidationException {@code id} 为空或非法
     * @throws EntityNotFoundException 设备不存在或已删除
     */
    public DeviceCatalogDto getDeviceCatalog(String id) {
        // Gate：网关 id 可解析为 long
        long deviceId = GatewayIdParseUtil.parseRequiredLong(id, "查询耗材详情失败：id 不能为空");
        // Pre：加载实体、定价行与可编辑性
        DeviceDefinition entity = deviceDefinitionQueryService.getDeviceDefinition(Long.valueOf(deviceId));
        if (entity == null) {
            throw new EntityNotFoundException("耗材不存在或已删除");
        }
        DeviceCatalogDto dto = deviceCatalogMapper.toDto(entity);
        this.enrichPageFields(List.of(dto));
        CatalogEditabilityQueryModel editability =
            catalogEditabilityResolver.resolveDeviceEditability(deviceId);
        dto.setEditabilityCode(editability.getEditabilityCode());
        dto.setEditabilityMessage(editability.getEditabilityMessage());
        // Asm：映射详情 DTO
        return dto;
    }

    /**
     * 编辑前校验：返回业务码及说明。
     *
     * @param id 设备定义主键字符串
     * @return 可编辑性模型
     */
    public CatalogEditabilityQueryModel validateDeviceCatalogEdit(String id) {
        // Gate：网关 id 可解析
        long deviceId = GatewayIdParseUtil.parseRequiredLong(id, "校验耗材目录编辑失败：id 不能为空");
        // Pre/Asm：读侧解析可编辑性
        return catalogEditabilityResolver.resolveDeviceEditability(deviceId);
    }

    /**
     * 查询耗材批号维真实进价/售价（当前 ACTIVE 价）。
     *
     * <p>与详情回填的维护态 {@code PURCHASE}/{@code SALE}（采购参考价/销售参考价）分离：
     * 本接口仅返回 {@code LOT_NUMBER_COST}（进价）与 {@code LOT_NUMBER_PRICE}（售价），金额为每包装单位。</p>
     *
     * @param request 耗材主键字符串；{@code lotNumber} 可空（空=全部批号）
     * @return 批号价行列表；无批号定价时为空列表
     * @throws ValidationException {@code id} 为空或非法
     * @throws EntityNotFoundException 耗材不存在或已删除
     */
    public List<CatalogLotPriceRowDto> listDeviceLotPrices(ListDeviceLotPriceRequest request) {
        // Gate：请求体与网关 id 可解析
        if (request == null) {
            throw new ValidationException("查询耗材批号价格失败：请求体不能为空");
        }
        long deviceId = GatewayIdParseUtil.parseRequiredLong(request.getId(), "查询耗材批号价格失败：id 不能为空");
        // Pre：确认耗材主档存在
        DeviceDefinition entity = deviceDefinitionQueryService.getDeviceDefinition(Long.valueOf(deviceId));
        if (entity == null) {
            throw new EntityNotFoundException("耗材不存在或已删除");
        }
        // Asm：当前价定价行 → 按批号归并进价/售价
        return CatalogLotPriceAssembler.assembleFromActivePriceLines(
            this.loadPriceLines(deviceId), request.getLotNumber());
    }

    /**
     * 新增耗材目录。
     *
     * @param request 创建请求（名称、单位等必填项见体内校验）
     * @return 新建设备的详情（含定价行）
     * @throws ValidationException 请求或必填字段不合法
     */

    public DeviceCatalogDto createDeviceCatalog(CreateDeviceCatalogRequest request) {
        // Pre：对齐 DD 序号，避免与库内已有 bus_no 撞号
        deviceCatalogBusNoSequenceSync.syncFromExistingDefinitions();
        // Cmd：与导入共用单行创建路径（建档 + 维护态定价）
        Long createdId = this.createOneForImport(request);
        // Asm：回读新建详情
        return this.getDeviceCatalog(String.valueOf(createdId));
    }

    /**
     * 编辑耗材目录（编辑路径不维护定价行）。
     *
     * <p>定价仅在 {@link #createDeviceCatalog} 写入维护态 {@code PURCHASE}/{@code SALE}/{@code LIMIT}；
     * 编辑若调用 {@link #replacePriceLines} 会误删进货入库写入的 {@code LOT_NUMBER_PRICE}，导致医生站无价。
     * 批号售价须经入库同意或调价流程维护，不在目录编辑页改价。</p>
     *
     * @param request 更新请求（须含字符串 {@code id} 及名称等）
     * @return 最新详情（含定价行）
     * @throws ValidationException 参数不合法
     */

    public DeviceCatalogDto editDeviceCatalog(UpdateDeviceCatalogRequest request) {
        // Gate：更新入参校验
        if (request == null || request.getId() == null || request.getId().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：耗材名称不能为空");
        }
        if (request.getCategoryCode() == null || request.getCategoryCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：耗材分类不能为空");
        }
        // Gate：种类必填（取药科室 type_code 中间维依赖目录 typeCode）
        if (request.getTypeCode() == null || request.getTypeCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：耗材种类不能为空");
        }
        this.normalizeUnitFieldsForWrite(request);
        this.normalizeDoseUnitDefaultsForWrite(request);
        if (request.getPurchaseUnitCode() == null || request.getPurchaseUnitCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：采购单位不能为空");
        }
        if (request.getSplitUnitCode() == null || request.getSplitUnitCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：拆零单位不能为空");
        }
        if (request.getPackageUnitCode() == null || request.getPackageUnitCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：包装单位不能为空");
        }
        if (request.getStockUnitCode() == null || request.getStockUnitCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：库存单位不能为空");
        }
        this.assertDeviceUnitRatiosPositive(request, "更新耗材失败");
        this.assertDeviceTraceUnitOptionalPair(request, "更新耗材失败");
        if (request.getEditabilityCode() == null || request.getEditabilityCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：editabilityCode 不能为空");
        }
        long id = GatewayIdParseUtil.parseRequiredLong(request.getId(), "更新耗材失败：id 不能为空");

        // Pre：加载现有实体、定价行与可编辑性
        DeviceDefinition existing = deviceDefinitionQueryService.getDeviceDefinition(id);
        if (existing == null) {
            throw new EntityNotFoundException("耗材不存在或已删除");
        }
        CatalogEditabilityQueryModel editability = catalogEditabilityResolver.resolveDeviceEditability(id);
        catalogEditWriteGate.assertDeviceCatalogUpdateAllowed(
            existing,
            this.loadPriceLines(id),
            request,
            editability.getEditabilityCode());

        this.normalizeManufacturerSupplyForWrite(request);
        UpdateDeviceDefinitionCommand cmd = deviceCatalogMapper.toUpdateCommand(request);
        cmd.getItems().get(0).setEditabilityCode(editability.getEditabilityCode());
        deviceDefinitionDomainService.updateDeviceDefinition(cmd);
        // Asm：回读最新详情（编辑路径不维护定价行）
        return this.getDeviceCatalog(String.valueOf(id));
    }

    /**
     * 批量启用耗材目录。
     *
     * @param ids 设备主键字符串列表（非空且均可解析为 long）
     * @throws ValidationException {@code ids} 为空或存在非法项
     */
    public void startDeviceCatalog(List<String> ids) {
        // Gate：批量 id 非空且可解析
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "启用耗材目录失败：ids 不能为空",
            "启用耗材目录失败：ids 存在非法值");
        // Cmd：领域批量启用
        ActivateDeviceDefinitionsCommand cmd = new ActivateDeviceDefinitionsCommand();
        cmd.setItems(deviceCatalogMapper.toActivateItems(ids));
        deviceDefinitionDomainService.activateDeviceDefinitions(cmd);
    }

    /**
     * 批量停用耗材目录。
     *
     * @param ids 设备主键字符串列表（非空且均可解析为 long）
     * @throws ValidationException {@code ids} 为空或存在非法项
     */
    public void stopDeviceCatalog(List<String> ids) {
        // Gate：批量 id 非空且可解析
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "停用耗材目录失败：ids 不能为空",
            "停用耗材目录失败：ids 存在非法值");
        // Cmd：领域批量停用
        RetireDeviceDefinitionsCommand cmd = new RetireDeviceDefinitionsCommand();
        cmd.setItems(deviceCatalogMapper.toRetireItems(ids));
        deviceDefinitionDomainService.retireDeviceDefinitions(cmd);
    }

    /**
     * 批量删除耗材目录（逻辑删除）。
     *
     * @param ids 设备主键字符串列表（非空且均可解析为 long）
     * @throws ValidationException {@code ids} 为空或存在非法项
     */
    public void deleteDeviceCatalog(List<String> ids) {
        // Gate：批量 id 非空且可解析
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "删除耗材目录失败：ids 不能为空",
            "删除耗材目录失败：ids 存在非法值");
        // Gate 续：删除与编辑不同——任一业务引用（临床或入库）即整单禁删；编辑才分二级/一级与字段裁剪
        for (String id : ids) {
            long deviceId = GatewayIdParseUtil.parseRequiredLong(id, "删除耗材目录失败：ids 存在非法值");
            CatalogEditabilityQueryModel editability =
                catalogEditabilityResolver.resolveDeviceEditability(deviceId);
            if (CatalogEditabilityConstants.isLocked(editability.getEditabilityCode())) {
                throw new ValidationException("删除耗材目录失败：已发生业务引用，不允许删除");
            }
        }
        // Cmd：领域批量逻辑删除
        DeleteDeviceDefinitionsCommand cmd = new DeleteDeviceDefinitionsCommand();
        cmd.setItems(deviceCatalogMapper.toDeleteItems(ids));
        deviceDefinitionDomainService.deleteDeviceDefinitions(cmd);
    }

    /**
     * 启动耗材目录异步导入（返回 jobId，配合进度轮询）。
     *
     * @param request 含 xlsx 字节与可选文件名
     * @return 含 jobId 的启动响应
     */
    public StartDeviceCatalogImportResponse startImportDeviceCatalog(ImportDeviceCatalogRequest request) {
        // Gate：上传字节
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入耗材目录失败：文件不能为空");
        }
        // Gate 续：操作人与机构（启动线程捕获，异步还原）
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入耗材目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "DEV-CAT-IMP-ORG",
            "导入耗材目录失败：未选择医院上下文，请先登录并选择医院");
        // Cmd：提交异步任务
        String jobId = deviceCatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(),
            request.getFileName(),
            orgId,
            operatorId,
            this::createOneForImport);
        // Asm：返回 jobId
        StartDeviceCatalogImportResponse response = new StartDeviceCatalogImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询耗材目录导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public DeviceCatalogImportProgressResponse getImportDeviceCatalogProgress(
        GetDeviceCatalogImportProgressRequest request) {
        // Gate：jobId 必填
        if (request == null || StringUtils.isEmpty(request.getJobId())) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_TOKEN,
                "查询导入进度失败：jobId 不能为空");
        }
        // Pre：取任务快照
        DeviceCatalogImportJob job = deviceCatalogImportAsyncWorkflow.getJob(request.getJobId());
        // Asm：映射进度响应
        DeviceCatalogImportProgressResponse response = new DeviceCatalogImportProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setStage(job.getStage());
        response.setPercent(job.getPercent());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setMessage(job.getMessage());
        response.setBatchNo(job.getBatchNo());
        response.setSuccessCount(job.getSuccessCount());
        response.setErrorFileToken(job.getErrorFileToken());
        response.setErrorCount(job.getErrorCount());
        response.setErrorMessages(job.copyErrorMessages());
        return response;
    }

    /**
     * 批量导入耗材目录（同步；兼容旧调用。页面推荐 {@link #startImportDeviceCatalog} + 进度轮询）。
     *
     * @param request 含 xlsx 字节
     * @return 成功摘要（batchNo / 成功条数）
     */
    public ImportDeviceCatalogResponse importDeviceCatalog(ImportDeviceCatalogRequest request) {
        // Gate：上传字节非空且体积受限
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入耗材目录失败：文件不能为空");
        }
        if (request.getFileBytes().length > DeviceCatalogImportConstants.ERROR_FILE_MAX_BYTES) {
            throw new ValidationException("导入耗材目录失败：文件过大，请拆分后重试");
        }
        // Gate 续：写路径操作人与机构
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入耗材目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "DEV-CAT-IMP-ORG",
            "导入耗材目录失败：未选择医院上下文，请先登录并选择医院");
        // Pre：解析 xlsx
        DeviceCatalogImportWorkbookParser.ParseResult parseResult =
            deviceCatalogImportWorkbookParser.parse(request.getFileBytes());
        // Pre 续：按 Excel 补齐缺失 unit_code，再全量行校验（不写库）
        deviceCatalogImportConceptEnsureService.ensureMissingConcepts(
            parseResult.getRows(), deviceCatalogImportTerminologyIndex);
        List<ImportDeviceCatalogErrorItemDto> errors = deviceCatalogImportRowValidator.validate(
            parseResult.getRows(),
            orgId,
            deviceCatalogImportTerminologyIndex,
            deviceDefinitionQueryService);
        // Gate：任一行失败则生成标注文件并拒绝整批
        if (errors != null && !errors.isEmpty()) {
            String token = deviceCatalogImportErrorFileService.buildAndStore(
                parseResult.getOriginalBytes(), errors);
            Map<String, String> errorMap = new LinkedHashMap<>();
            errorMap.put("errorFileToken", token);
            int index = 0;
            for (ImportDeviceCatalogErrorItemDto item : errors) {
                String key = "e" + index;
                StringBuilder line = new StringBuilder();
                if (item.getExcelRowNum() != null) {
                    line.append("第").append(item.getExcelRowNum()).append("行");
                }
                if (StringUtils.isNotEmpty(item.getColumnName())) {
                    if (line.length() > 0) {
                        line.append(" ");
                    }
                    line.append("[").append(item.getColumnName()).append("]");
                }
                if (line.length() > 0) {
                    line.append("：");
                }
                line.append(item.getMessage());
                errorMap.put(key, line.toString());
                index = index + 1;
            }
            throw new ValidationException(
                "导入失败，共 " + errors.size() + " 处错误，已全部回滚", errorMap);
        }
        // Cmd：事务内逐行创建（ACTIVE + 维护态价）
        String batchNo = DeviceCatalogImportConstants.BATCH_NO_PREFIX
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<DeviceCatalogImportRow> rows = parseResult.getRows();
        deviceCatalogImportWriteSupport.importRows(
            batchNo, operatorId, rows, this::createOneForImport);
        log.info("耗材目录导入完成 batchNo={}, success={}", batchNo, rows.size());
        // Asm：成功响应
        ImportDeviceCatalogResponse response = new ImportDeviceCatalogResponse();
        response.setBatchNo(batchNo);
        response.setTotalCount(rows.size());
        response.setSuccessCount(rows.size());
        response.setFailCount(0);
        return response;
    }

    /**
     * 下载耗材目录导入模板。
     *
     * @return xlsx 字节包装，供前端 base64 下载
     */
    public DeviceCatalogImportFileResponse downloadImportTemplate() {
        // Pre：生成易用型 xlsx 模板
        byte[] bytes = deviceCatalogImportTemplateBuilder.build();
        // Asm：包装下载响应
        DeviceCatalogImportFileResponse response = new DeviceCatalogImportFileResponse();
        response.setFileName(DeviceCatalogImportConstants.TEMPLATE_FILE_NAME);
        response.setContentType(DeviceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 按筛选条件导出耗材目录（单 Sheet；首列编号 + 导入模板字段序）。
     *
     * <p>维护态价经 {@code findPriceLinesByProjects} 批量回填；触达
     * {@link DeviceCatalogImportConstants#EXPORT_MAX_ROWS} 时截断并标记 truncated。</p>
     *
     * @param request 关键字/分类/种类；可空（空=当前租户未删除全量，仍受行数上限）
     * @return xlsx 文件响应
     */
    public ExportDeviceCatalogResponse exportDeviceCatalog(ExportDeviceCatalogRequest request) {
        // Gate：空请求视为无筛选，仍允许导出
        ExportDeviceCatalogRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new ExportDeviceCatalogRequest();
        }
        // Pre：与列表同口径分页取前 N+1 条，用于判定截断
        int fetchLimit = DeviceCatalogImportConstants.EXPORT_MAX_ROWS + 1;
        Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
            0,
            fetchLimit,
            safeRequest.getKeyword(),
            safeRequest.getCategoryCode(),
            safeRequest.getTypeCode());
        List<DeviceDefinition> details = page.getContent();
        boolean truncated = false;
        List<DeviceDefinition> exportDetails = details;
        if (details.size() > DeviceCatalogImportConstants.EXPORT_MAX_ROWS) {
            truncated = true;
            exportDetails = details.subList(0, DeviceCatalogImportConstants.EXPORT_MAX_ROWS);
        }
        List<Long> ids = new ArrayList<>();
        for (DeviceDefinition detail : exportDetails) {
            if (detail != null && detail.getId() != null) {
                ids.add(detail.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceMap;
        if (ids.isEmpty()) {
            priceMap = new HashMap<>();
        } else {
            priceMap = this.loadPriceMap(ids);
        }
        deviceCatalogImportTerminologyIndex.load();
        List<String[]> dataRows = new ArrayList<>();
        for (DeviceDefinition detail : exportDetails) {
            if (detail == null) {
                continue;
            }
            List<ChargeItemDefinition> priceLines = priceMap.get(detail.getId());
            dataRows.add(deviceCatalogExportRowAssembler.toColumnValues(
                detail, priceLines, deviceCatalogImportTerminologyIndex));
        }
        byte[] bytes = deviceCatalogImportTemplateBuilder.buildExport(
            dataRows, safeRequest.getExportColumns());
        String fileName = DeviceCatalogImportConstants.EXPORT_FILE_NAME_PREFIX
            + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(DeviceCatalogImportConstants.EXPORT_FILE_NAME_TIME_PATTERN))
            + ".xlsx";
        ExportDeviceCatalogResponse response = new ExportDeviceCatalogResponse();
        response.setFileName(fileName);
        response.setContentType(DeviceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        response.setExportedRowCount(dataRows.size());
        response.setTruncated(truncated);
        log.info("耗材目录导出完成 rows={}, truncated={}, keyword={}, categoryCode={}, typeCode={}",
            dataRows.size(), truncated, safeRequest.getKeyword(),
            safeRequest.getCategoryCode(), safeRequest.getTypeCode());
        return response;
    }

    /**
     * 下载耗材目录导入错误标注文件。
     *
     * @param request 含短期 token
     * @return 标注后的 xlsx
     */
    public DeviceCatalogImportFileResponse downloadImportErrorFile(
        DownloadDeviceCatalogImportErrorRequest request) {
        // Gate：token 必填
        if (request == null || StringUtils.isEmpty(request.getToken())) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_TOKEN,
                "下载错误标注文件失败：token 不能为空");
        }
        // Pre：按 token 取临时文件
        byte[] bytes = deviceCatalogImportErrorFileService.getBytes(request.getToken().trim());
        // Asm：包装下载响应
        DeviceCatalogImportFileResponse response = new DeviceCatalogImportFileResponse();
        response.setFileName(
            "耗材目录导入_错误标注_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".xlsx");
        response.setContentType(DeviceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 导入/新建单行创建：领域建档 + 维护态定价，供写段回调与 {@link #createDeviceCatalog} 共用。
     *
     * @param request 已映射的新建请求
     * @return 新建耗材定义主键
     */
    Long createOneForImport(CreateDeviceCatalogRequest request) {
        // Gate：创建入参校验（名称、单位、价格、财务类型等）
        if (request == null) {
            throw new ValidationException("创建耗材失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：耗材名称不能为空");
        }
        if (request.getCategoryCode() == null || request.getCategoryCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：耗材分类不能为空");
        }
        // Gate：种类必填（取药科室 type_code 中间维依赖目录 typeCode）
        if (request.getTypeCode() == null || request.getTypeCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：耗材种类不能为空");
        }
        this.normalizeUnitFieldsForWrite(request);
        this.normalizeDoseUnitDefaultsForWrite(request);
        if (request.getPurchaseUnitCode() == null || request.getPurchaseUnitCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：采购单位不能为空");
        }
        if (request.getSplitUnitCode() == null || request.getSplitUnitCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：拆零单位不能为空");
        }
        if (request.getPackageUnitCode() == null || request.getPackageUnitCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：包装单位不能为空");
        }
        if (request.getStockUnitCode() == null || request.getStockUnitCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：库存单位不能为空");
        }
        this.assertDeviceUnitRatiosPositive(request, "创建耗材失败");
        this.assertDeviceTraceUnitOptionalPair(request, "创建耗材失败");
        if (request.getPurchasePrice() == null) {
            throw new ValidationException("创建耗材失败：采购参考价不能为空");
        }
        if (request.getRetailPrice() == null) {
            throw new ValidationException("创建耗材失败：销售参考价不能为空");
        }
        if (StringUtils.isEmpty(request.getItemTypeCode())) {
            throw new ValidationException("创建耗材失败：财务类型不能为空");
        }
        // Pre：绑定会话机构；清空供应商；收敛厂家文案
        this.applyDefaultOrganizationIdForCreate(request);
        request.setSupplyId(null);
        this.normalizeManufacturerSupplyForWrite(request);
        // Cmd：领域创建耗材主数据
        CreateDeviceDefinitionCommand cmd = deviceCatalogMapper.toCreateCommand(request);
        Long createdId = deviceDefinitionDomainService.createDeviceDefinition(cmd);
        // Cmd 续：实例维度定价行替换（财务类型用 itemTypeCode）
        this.replacePriceLines(
            createdId,
            request.getItemTypeCode(),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        return createdId;
    }

    /**
     * 新建时目录主数据统一挂当前会话医院（不使用会话科室）。
     *
     * @param request 创建请求（就地修改）
     */
    private void applyDefaultOrganizationIdForCreate(CreateDeviceCatalogRequest request) {
        request.setOrgId(managedOrganizationIdsResolver.requireSessionHospitalId(
            "DEV-CAT-VAL-ORG",
            "新增耗材目录失败：未选择医院上下文，请先登录并选择医院"));
    }

    /**
     * 校验三类「相对拆零单位」换算倍数：必填且须大于 0（剂量倍数由界面隐藏，不在此校验）。
     *
     * @param request   创建或更新请求体
     * @param errPrefix 异常前缀（如 {@code 创建耗材失败}）
     */
    private void assertDeviceUnitRatiosPositive(CreateDeviceCatalogRequest request, String errPrefix) {
        DeviceCatalogAppService.assertPositiveUnitRatio(errPrefix, "采购→拆零倍数", request.getPurchaseUnitRatio());
        DeviceCatalogAppService.assertPositiveUnitRatio(errPrefix, "库存→拆零倍数", request.getStockUnitRatio());
        DeviceCatalogAppService.assertPositiveUnitRatio(errPrefix, "包装→拆零倍数", request.getPackageUnitRatio());
    }

    /**
     * 耗材追溯码单位闸门：两字段均可空；任一侧有值则须成对，且倍数 &gt; 0。
     *
     * @param request   创建或更新请求
     * @param errPrefix 异常前缀
     * @throws ValidationException 只填一侧或倍数非正
     */
    private void assertDeviceTraceUnitOptionalPair(CreateDeviceCatalogRequest request, String errPrefix) {
        String traceUnitCode = request.getTraceUnitCode();
        BigDecimal traceUnitRatio = request.getTraceUnitRatio();
        boolean codeBlank = StringUtils.isEmpty(traceUnitCode) || traceUnitCode.isBlank();
        boolean ratioMissing = traceUnitRatio == null;
        // 两侧皆空：耗材允许不维护追溯码单位
        if (codeBlank && ratioMissing) {
            return;
        }
        // 只填倍数未填单位：入库侧无法解析贴码口径
        if (codeBlank) {
            throw new ValidationException(errPrefix + "：填写追溯码倍数时须同时指定追溯码单位");
        }
        // 只填单位未填倍数：无法换算应扫码数
        if (ratioMissing) {
            throw new ValidationException(errPrefix + "：填写追溯码单位时须同时指定追溯码单位→拆零倍数");
        }
        if (traceUnitRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(errPrefix + "：追溯码单位→拆零倍数须大于 0");
        }
    }

    /**
     * 校验单个单位换算倍数：必填且须严格大于 0，否则阻断保存。
     *
     * @param errPrefix 异常消息前缀（创建/更新场景）
     * @param fieldCn   字段中文名，拼入提示语
     * @param value     待校验倍数
     * @throws ValidationException 为空或非正数时
     */
    private static void assertPositiveUnitRatio(String errPrefix, String fieldCn, BigDecimal value) {
        if (value == null) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "须大于 0");
        }
    }

    /**
     * 按设备实例加载关联收费定价行（采购/零售/限价等多条）。
     *
     * @param deviceId 设备定义主键
     * @return 定价行列表（可能为空）
     */
    private List<ChargeItemDefinition> loadPriceLines(Long deviceId) {
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        cmd.setInstanceIds(List.of(deviceId));
        return chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
    }

    /**
     * 为列表页批量补齐定价金额、术语展示文案与供应商名称（避免 N+1，术语与供应商映射按页各构建一次）。
     *
     * <p>性能说明：本方法一次性构建全页所需的术语映射和供应商映射，然后逐行回填，避免每行单独查询。</p>
     *
     * @param rows 当前页 DTO 列表（就地修改）
     */
    private void enrichPageFields(List<DeviceCatalogDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(DeviceCatalogDto::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<ChargeItemDefinition>> priceMap = this.loadPriceMap(ids);
        Map<String, String> financialTypeTextMap = DeviceCatalogAppService.terminologyOptionLabelByValue(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE));
        Map<String, String> categoryMap = DeviceCatalogAppService.terminologyOptionLabelByValue(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        Map<String, String> deviceTypeMap = DeviceCatalogAppService.terminologyOptionLabelByValue(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE));
        // 五类单位展示名统一走 unit_code，与药品目录、选品读模型口径一致。
        Map<String, String> unitCodeMap = DeviceCatalogAppService.terminologyOptionLabelByValue(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE));
        Map<Long, String> supplierNameById = supplierQueryService.listSuppliers().stream()
            .filter(Objects::nonNull)
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(SupplierListItemQueryModel::getId, SupplierListItemQueryModel::getName, (a, b) -> a));

        // Loop：逐行回填当前页各 DTO 的定价金额与展示文案
        for (DeviceCatalogDto dto : rows) {
            if (dto == null || dto.getId() == null) {
                continue;
            }
            this.enrichPriceFields(dto, priceMap.get(dto.getId()));

            if (dto.getManufacturerId() == null && dto.getSupplyId() != null) {
                dto.setManufacturerId(dto.getSupplyId());
            }

            String catLabel = DeviceCatalogAppService.dictLabel(categoryMap, dto.getCategoryCode());
            if (catLabel != null) {
                dto.setCategoryCode_dictText(catLabel);
            }
            String typeLabel = DeviceCatalogAppService.dictLabel(deviceTypeMap, dto.getTypeCode());
            if (typeLabel != null) {
                dto.setTypeCode_dictText(typeLabel);
            }
            String purchaseLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getPurchaseUnitCode());
            if (purchaseLabel != null) {
                dto.setPurchaseUnitCode_dictText(purchaseLabel);
                dto.setUnitCode_dictText(purchaseLabel);
            }
            String splitLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getSplitUnitCode());
            if (splitLabel != null) {
                dto.setSplitUnitCode_dictText(splitLabel);
                dto.setMinUnitCode_dictText(splitLabel);
            }
            String packageLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getPackageUnitCode());
            if (packageLabel != null) {
                dto.setPackageUnitCode_dictText(packageLabel);
            }
            String stockLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getStockUnitCode());
            if (stockLabel != null) {
                dto.setStockUnitCode_dictText(stockLabel);
            }
            String doseLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getDoseUnitCode());
            if (doseLabel != null) {
                dto.setDoseUnitCode_dictText(doseLabel);
            }
            // 追溯码单位与业务单位共用 unit_code 术语
            String traceLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getTraceUnitCode());
            if (traceLabel != null) {
                dto.setTraceUnitCode_dictText(traceLabel);
            }
            String encounterLabel = DeviceCatalogAppService.dictLabel(unitCodeMap, dto.getDefEncounterUnitCode());
            if (encounterLabel != null) {
                dto.setDefEncounterUnitCode_dictText(encounterLabel);
            }

            if (dto.getItemTypeCode() != null) {
                String finLabel = financialTypeTextMap.get(dto.getItemTypeCode());
                if (finLabel != null) {
                    dto.setItemTypeCode_dictText(finLabel);
                }
            }

            if (dto.getSupplyId() != null) {
                String supName = supplierNameById.get(dto.getSupplyId());
                if (supName != null) {
                    dto.setSupplyId_dictText(supName);
                }
            }
            // 厂家展示名：优先按 manufacturerId 解析；未填时不回落 supply 名称到 manufacturerText
            if (dto.getManufacturerId() != null) {
                String mfgName = supplierNameById.get(dto.getManufacturerId());
                if (mfgName != null) {
                    dto.setManufacturerText(mfgName);
                }
            }
        }
    }

    /**
     * 写入前归一化五类单位字段：{@code purchaseUnitCode}/{@code splitUnitCode} 与历史 {@code unitCode}/{@code minUnitCode} 双向对齐。
     *
     * @param request 创建或更新请求（就地修改）
     */
    private void normalizeUnitFieldsForWrite(CreateDeviceCatalogRequest request) {
        if (request == null) {
            return;
        }
        String purchaseUnitCode = request.getPurchaseUnitCode();
        if (purchaseUnitCode == null || purchaseUnitCode.isBlank()) {
            purchaseUnitCode = request.getUnitCode();
        }
        if (purchaseUnitCode != null && !purchaseUnitCode.isBlank()) {
            String trimmed = purchaseUnitCode.trim();
            request.setPurchaseUnitCode(trimmed);
            request.setUnitCode(trimmed);
        }
        String splitUnitCode = request.getSplitUnitCode();
        if (splitUnitCode == null || splitUnitCode.isBlank()) {
            splitUnitCode = request.getMinUnitCode();
        }
        if (splitUnitCode != null && !splitUnitCode.isBlank()) {
            String trimmed = splitUnitCode.trim();
            request.setSplitUnitCode(trimmed);
            request.setMinUnitCode(trimmed);
        }
        if (request.getPackageUnitCode() != null) {
            request.setPackageUnitCode(request.getPackageUnitCode().trim());
        }
        if (request.getStockUnitCode() != null) {
            request.setStockUnitCode(request.getStockUnitCode().trim());
        }
        if (request.getDoseUnitCode() != null) {
            request.setDoseUnitCode(request.getDoseUnitCode().trim());
        }
    }

    /**
     * 剂量单位/倍数不在维护界面展示：未传时回落为拆零单位与 1，保证领域五类单位完整。
     *
     * @param request 创建或更新请求（就地修改）
     */
    private void normalizeDoseUnitDefaultsForWrite(CreateDeviceCatalogRequest request) {
        if (request == null) {
            return;
        }
        if (request.getDoseUnitCode() == null || request.getDoseUnitCode().isBlank()) {
            String splitUnitCode = request.getSplitUnitCode();
            if (splitUnitCode == null || splitUnitCode.isBlank()) {
                splitUnitCode = request.getMinUnitCode();
            }
            if (splitUnitCode != null && !splitUnitCode.isBlank()) {
                request.setDoseUnitCode(splitUnitCode.trim());
            }
        }
        if (request.getDoseUnitRatio() == null) {
            request.setDoseUnitRatio(BigDecimal.ONE);
        }
    }

    /**
     * 写入前收敛生产厂家主键：manufacturer_id 空而 supply_id 有值时，用 supply_id 回填 manufacturer_id。
     * 不再把厂家名称写入实体；展示名由读路径按 id 解析。
     *
     * @param request 创建或更新请求（就地修改）
     */
    private void normalizeManufacturerSupplyForWrite(CreateDeviceCatalogRequest request) {
        if (request == null) {
            return;
        }
        Long supplyId = request.getSupplyId();
        Long manufacturerId = request.getManufacturerId();
        // 历史兼容：仅填了供应商时，把 supplyId 视为厂家主键写入 manufacturerId
        if (supplyId != null && manufacturerId == null) {
            request.setManufacturerId(supplyId);
        }
    }

    /**
     * 将术语 {@link OptionDto} 列表转为 value→label 映射，供列表页批量回填字典展示文案。
     *
     * @param options 术语下拉选项
     * @return 码值到展示名的映射；入参为空时返回不可变空 Map
     */
    private static Map<String, String> terminologyOptionLabelByValue(List<OptionDto> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        return options.stream()
            .filter(Objects::nonNull)
            .filter(o -> o.getValue() != null)
            .collect(Collectors.toMap(o -> String.valueOf(o.getValue()), OptionDto::getLabel, (a, b) -> a));
    }

    /**
     * 按码值从预构建映射中取展示 label；码为空或映射缺失时返回 {@code null}。
     *
     * @param valueToLabel 码值→展示名索引
     * @param code         持久化业务码
     * @return 展示文案或 {@code null}
     */
    private static String dictLabel(Map<String, String> valueToLabel, String code) {
        if (code == null || code.isBlank() || valueToLabel == null || valueToLabel.isEmpty()) {
            return null;
        }
        return valueToLabel.get(code);
    }

    /**
     * 批量查询多台设备的定价行并按 {@code instanceId} 分组。
     *
     * @param ids 设备主键集合
     * @return 实例 id → 定价行列表（无数据的 key 不会出现）
     */
    private Map<Long, List<ChargeItemDefinition>> loadPriceMap(List<Long> ids) {
        Map<Long, List<ChargeItemDefinition>> grouped = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return grouped;
        }
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        cmd.setInstanceIds(ids);
        List<ChargeItemDefinition> rows = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
        for (ChargeItemDefinition row : rows) {
            grouped.computeIfAbsent(row.getInstanceId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 将收费价格行回填到 DTO，金额与 {@code adm_charge_item_definition.price_value} 同为元。
     *
     * @param dto   目标 DTO（就地修改）
     * @param rows  定价行集合
     * @return 同一 {@code dto} 引用便于链式调用
     */
    private DeviceCatalogDto enrichPriceFields(DeviceCatalogDto dto, List<ChargeItemDefinition> rows) {
        if (rows == null || rows.isEmpty()) {
            return dto;
        }
        // 按价格类型码分支回填：采购参考价、限价、销售参考价（维护态单价）互不覆盖错误字段。
        for (ChargeItemDefinition row : rows) {
            if (dto.getItemTypeCode() == null && row.getTypeCode() != null) {
                dto.setItemTypeCode(row.getTypeCode());
            }
            if (dto.getYbType() == null && row.getYbType() != null) {
                dto.setYbType(row.getYbType());
            }
            if (PriceType.PURCHASE.getCode().equals(row.getPriceTypeCode())) {
                dto.setPurchasePrice(row.getPriceValue());
                continue;
            }
            if (PriceType.LIMIT.getCode().equals(row.getPriceTypeCode())) {
                dto.setMaximumRetailPrice(row.getPriceValue());
                continue;
            }
            if (PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                dto.setRetailPrice(row.getPriceValue());
            }
        }
        return dto;
    }

    /**
     * 一次性重建设备实例下的定价行：先清空实例维度旧定价，再按页面传入的非空金额写入新行。
     *
     * @param instanceId        设备定义主键
     * @param itemTypeCode      财务分类编码（fin_type_code，必填）
     * @param ybType            医保类别（可空）
     * @param purchasePrice     采购参考价（必填，每包装单位，维护态 {@code PURCHASE}）
     * @param retailPrice       销售参考价（必填，每包装单位，维护态 {@code SALE}）
     * @param maximumRetailPrice 限价（可空）
     */
    private void replacePriceLines(
        Long instanceId,
        String itemTypeCode,
        String ybType,
        BigDecimal purchasePrice,
        BigDecimal retailPrice,
        BigDecimal maximumRetailPrice) {
        // 步骤一：移除该设备实例下全部旧定价，避免重复 priceType 行叠加。
        RemovePricingByInstanceCommand removeCmd = new RemovePricingByInstanceCommand();
        removeCmd.setInstanceTable(TableNameConstants.ADM_DEVICE_DEFINITION);
        removeCmd.setInstanceId(instanceId);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCmd);

        // 步骤二：按三类价格分别追加（工具类内跳过金额为空的项）。
        List<SaveAllChargeItemDefinitionsCommand.Item> items = new ArrayList<>();
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.ADM_DEVICE_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.PURCHASE.getCode(), purchasePrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.ADM_DEVICE_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.SALE.getCode(), retailPrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.ADM_DEVICE_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.LIMIT.getCode(), maximumRetailPrice, null);
        if (items.isEmpty()) {
            return;
        }
        // 步骤三：批量写入收费定义（集合落库，符合批量 I/O 门禁）。
        SaveAllChargeItemDefinitionsCommand saveCmd = new SaveAllChargeItemDefinitionsCommand();
        saveCmd.setItems(items);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(saveCmd);
    }
}
