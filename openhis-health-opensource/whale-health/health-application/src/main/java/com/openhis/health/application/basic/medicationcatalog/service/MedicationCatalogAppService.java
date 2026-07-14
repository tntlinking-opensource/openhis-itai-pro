/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.catalog.support.CatalogEditWriteGate;
import com.openhis.health.application.basic.medicationcatalog.dto.ImportMedicationCatalogResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationManageInitDto;
import com.openhis.health.application.basic.medicationcatalog.mapper.MedicationCatalogMapper;
import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.QueryMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityQueryModel;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityResolver;
import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pricing.ChargePricingCommandAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.converter.TextValueConverter;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationDefinitionExternalReferenceGuard;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.medication.medicationdefinition.command.ImportMedicationCatalogCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.ImportMedicationCatalogResult;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionTemplateQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionDomainService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.OrderCalcRuleEnum;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.FinTypeCodeMappingSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 药品目录管理应用编排：分页列表（读主表）、详情回填定价、增删改与批量启停、导入导出辅助。
 *
 * <p>职责边界：Request/DTO ↔ 领域 Command；列表查询走 {@link MedicationDefinitionQueryService} 读主表；
 * 定价行维护模式与耗材目录一致（先清实例维度旧定价再批量写入）。不做计费规则计算。</p>
 *
 * <p>说明：库房维度下拉依赖机构上下文，当前 {@link #loadLocationOptions(Long)} 在 {@code orgId} 为空时返回空列表，
 * 与前端「库房联动」后续 story 对齐时再扩展初始化入口。</p>
 *
 * <p><b>接入方式（项目约定）</b>：由 {@code whale-web} 的 {@code AppServiceControllerRegistrar} 将 {@code *AppService}
 * 请勿为本类另写 {@code @RestController} 破坏统一网关模型。</p>
 */
@Service
@Slf4j
@AllArgsConstructor
public class MedicationCatalogAppService {

    /** 药品定义写侧：建档、编辑、批量状态迁移与导入。 */
    private final MedicationDefinitionDomainService medicationDefinitionDomainService;
    /** 药品定义读侧：模板下载等少量查询。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 请求/读模型 ↔ 领域命令与 DTO 映射。 */
    private final MedicationCatalogMapper medicationCatalogMapper;
    /** 收费定价行读侧：按实例加载采购/零售/限价行。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 收费定价行写侧：实例维度删旧建新与批量保存。 */
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    /** 术语下拉：药品维护页各编码体系选项。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 用药频次下拉：读 cus_frequency 启用态。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 供应商主数据列表 → 下拉 Option。 */
    private final SupplierQueryService supplierQueryService;
    /** 库房/药房点位：机构上下文下拉（可为空列表）。 */
    private final LocationQueryService locationQueryService;
    /** 会话科室收窄（读/写路径，{@code adm_organization.id}）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 废弃前跨聚合引用守卫。 */
    private final MedicationDefinitionExternalReferenceGuard medicationDefinitionExternalReferenceGuard;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 目录编辑业务码读侧编排。 */
    private final CatalogEditabilityResolver catalogEditabilityResolver;
    /** 目录写侧 immutable 字段门禁。 */
    private final CatalogEditWriteGate catalogEditWriteGate;

    /**
     * 药品目录维护页初始化：返回各字典下拉项（术语编码系统）及供应商、库房等选项。
     *
     * @return 初始化 DTO，供前端表单渲染
     */
    public MedicationManageInitDto getMedicationInit() {
        // Gate：只读页初始化，无写侧操作人
        MedicationManageInitDto dto = new MedicationManageInitDto();
        // Pre：加载术语下拉、供应商、频次与库房选项
        dto.setMedicationCategoryCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        dto.setStatusFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.PUBLICATION_STATUS));
        dto.setDomainFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_DOMAIN));
        // 主数据关联：供应商下拉来自自定义供应商查询（非术语表）。
        dto.setSupplierListOptions(this.loadSupplierOptions());
        dto.setStatusWeatherOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.YES_NO_FLAG));
        dto.setStatusRestrictedOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.RESTRICTED_ENUM));
        // 门诊拆分属性与住院临时医嘱拆分属性：共用门诊发药/计价取整规则枚举（非术语表）。
        List<OptionDto> orderCalcRuleOptions = EnumUtil.toOptions(OrderCalcRuleEnum.class);
        dto.setPartAttributeEnumOptions(orderCalcRuleOptions);
        dto.setTempOrderSplitPropertyEnumOptions(orderCalcRuleOptions);
        dto.setChrgitmLvOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.CHRGITM_LV));
        dto.setMrSettlementTypeCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MR_SETTLEMENT_TYPE_CODE));
        dto.setSystemCategoriesOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.SYSTEM_CATEGORIES));
        dto.setMedicinePropertiesOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICINE_PROPERTIES));
        dto.setDoseFormCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE));
        dto.setMedChrgitmTypeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MED_CHRGITM_TYPE));
        dto.setMethodCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.METHOD_CODE));
        dto.setFinTypeCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE));
        dto.setAntibioticTypeCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.ANTIBIOTIC_TYPE_CODE));
        dto.setDddCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DDD_CODE));
        dto.setDoseFromCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_FROM_CODE));
        dto.setRxFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.RX_FLAG));
        dto.setDosageInstructionOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION));
        dto.setUnitCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE));
        dto.setRateCodeOptions(frequencyOptionLoader.loadActiveRateOptions());
        // 库房/药房选项：依赖机构 id；未传入时返回空，避免误列全库点位。
        dto.setLocationOptions(this.loadLocationOptions(managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery()));
        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 分页查询药品目录列表（读主表 {@code med_medication_definition}，定价行批量回填）。
     *
     * <p>默认按 {@code busNo} 倒序；合法 {@code sortBy} 见白名单方法。</p>
     *
     * @param query 分页与筛选条件
     * @return 分页结果
     */
    public PagedResult<MedicationDto> pageMedicationCatalog(QueryMedicationCatalogRequest query) {
        // Gate：默认按业务号倒序，避免按创建/更新时间排序导致编辑后行位跳动；排序字段白名单见下方
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        if (query.getSortBy() != null && !query.getSortBy().isBlank()
            && this.isAllowedMedicationCatalogSortField(query.getSortBy())) {
            Sort.Direction direction = Sort.Direction.DESC;
            if ("ASC".equalsIgnoreCase(query.getSortDirection())) {
                direction = Sort.Direction.ASC;
            }
            sort = Sort.by(direction, query.getSortBy());
        }
        MedicationDefinitionCatalogPageQuery pageQuery = new MedicationDefinitionCatalogPageQuery();
        pageQuery.setPageNo(query.getPageNo());
        pageQuery.setPageSize(query.getPageSize());
        pageQuery.setCategoryCode(query.getCategoryCode());
        pageQuery.setKeyword(query.getKeyword());
        // Pre：读侧分页查询主表
        Page<MedicationDefinitionPageQueryModel> pageResult = medicationDefinitionQueryService.pageMedicationCatalog(
            pageQuery, PageRequest.of(pageQuery.springPageIndex(), pageQuery.getBoundedPageSize(), sort));
        List<MedicationDto> content = pageResult.getContent().stream()
            .map(medicationCatalogMapper::toMedicationDto)
            .collect(Collectors.toCollection(ArrayList::new));
        this.enrichMedicationCatalogPageRows(content);
        // Asm：映射分页响应
        return PagedResult.fromPage(new PageImpl<>(content, pageResult.getPageable(), pageResult.getTotalElements()));
    }

    /**
     * 按药品定义主键查询详情（写模型为主：字段齐全；定价行仍从收费侧加载）。
     *
     * <p>说明：{@code hisrm_product_definition} 用于列表/检索快照，列与术语展示列不完整；
     * 编辑页回显以聚合 {@code med_medication_definition} 为准，避免大量字段空白。</p>
     *
     * @param id 药品定义标识
     * @return 详情 DTO
     * @throws ValidationException {@code id} 为空；聚合不存在时由领域查询抛出业务异常
     */
    public MedicationDetailDto getMedicationCatalog(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("查询药品详情失败：id 不能为空");
        }
        // Pre/Asm：写模型组装详情并回填定价
        return this.getMedicationDetailFromWriteModel(id);
    }

    /**
     * 编辑前校验：返回业务码及说明，供维护页锁定分类/单位/拆零倍数/价格等字段。
     *
     * @param id 药品定义主键
     * @return 可编辑性模型
     */
    public CatalogEditabilityQueryModel validateMedicationCatalogEdit(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("校验药品目录编辑失败：id 不能为空");
        }
        // Pre/Asm：读侧解析可编辑性业务码
        return catalogEditabilityResolver.resolveMedicationEditability(id);
    }

    /**
     * 新增药品目录定义并写入关联定价行。
     *
     * @param request 新建请求体
     * @return 新建后的详情
     * @throws ValidationException 必填项校验失败
     */
    public MedicationDetailDto createMedicationCatalog(CreateMedicationCatalogRequest request) {
        // Gate：创建入参校验
        this.validateCreateRequest(request);
        // Pre：未传 orgId 时绑定会话科室
        this.applyDefaultOrganizationIdForCreate(request);
        // Cmd：领域建档
        Long createdId = medicationDefinitionDomainService.createMedicationDefinition(medicationCatalogMapper.toCreateCommand(request));
        // Cmd 续：实例维度定价行替换
        this.replacePriceLines(
            createdId,
            this.resolveMedicationFinTypeCodeForPricing(request.getTypeCode(), request.getCategoryCode()),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        // Asm：回读新建详情
        return this.getMedicationCatalog(createdId);
    }

    /**
     * 修改药品目录主数据（编辑路径不维护定价行）。
     *
     * <p>定价仅在 {@link #createMedicationCatalog} 写入维护态 {@code PURCHASE}/{@code SALE}/{@code LIMIT}；
     * 编辑若调用 {@link #replacePriceLines} 会误删进货入库写入的 {@code LOT_NUMBER_PRICE}，导致医生站无价。
     * 批号售价须经入库同意或调价流程维护，不在目录编辑页改价。</p>
     *
     * @param request 药品目录信息（须含主键）
     * @return 最新详情（含定价回填）
     * @throws ValidationException 校验失败
     */
    public MedicationDetailDto editMedicationCatalog(UpdateMedicationCatalogRequest request) {
        // Gate：更新入参校验
        this.validateUpdateRequest(request);
        // Pre：加载现有实体与可编辑性
        MedicationDefinition existing = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(request.getId())
            .orElseThrow(() -> new ValidationException("更新药品目录失败：记录不存在或已删除"));
        CatalogEditabilityQueryModel editability =
            catalogEditabilityResolver.resolveMedicationEditability(request.getId());
        catalogEditWriteGate.assertMedicationCatalogUpdateAllowed(
            existing,
            this.loadPriceLines(request.getId()),
            request,
            editability.getEditabilityCode());
        // Cmd：领域更新主数据（编辑路径不维护定价行）
        medicationDefinitionDomainService.editMedicationDefinition(medicationCatalogMapper.toEditCommand(request));
        // Asm：回读最新详情
        return this.getMedicationCatalog(request.getId());
    }

    /**
     * 批量停用药品目录
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void stopMedicationCatalog(List<Long> ids) {
        // Gate：批量主键非空
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "停用药品目录失败：ids 不能为空");
        // Cmd：领域批量停用
        medicationDefinitionDomainService.retireMedicationDefinitions(medicationCatalogMapper.toRetireCommand(ids));
    }

    /**
     * 批量启用药品目录
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void startMedicationCatalog(List<Long> ids) {
        // Gate：批量主键非空
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "启用药品目录失败：ids 不能为空");
        // Cmd：领域批量启用
        medicationDefinitionDomainService.activateMedicationDefinitions(medicationCatalogMapper.toActivateCommand(ids));
    }

    /**
     * 批量删除（废弃）药品目录。
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void deleteMedicationCatalog(List<Long> ids) {
        // Gate：批量主键非空
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "删除药品目录失败：ids 不能为空");
        // Gate 续：跨聚合引用守卫，有引用则阻断废弃
        medicationDefinitionExternalReferenceGuard.assertNoExternalReference(ids);
        // Cmd：领域批量废弃
        medicationDefinitionDomainService.discardMedicationDefinition(medicationCatalogMapper.toDiscardCommand(ids));
    }

    /**
     * 导入药品目录
     *
     * @param file 导入文件（CSV，首行为表头）
     * @return 导入批次统计
     * @throws ValidationException 文件为空或解析失败
     */
    public ImportMedicationCatalogResponse importMedicationCatalog(MultipartFile file) {
        // Gate：上传文件非空
        if (file == null || file.isEmpty()) {
            throw new ValidationException("导入药品目录失败：文件不能为空");
        }
        // Gate 续：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入药品目录");
        // Pre：组装导入命令并解析 CSV 行
        ImportMedicationCatalogCommand command = new ImportMedicationCatalogCommand();
        command.setBatchNo("MED-IMPORT-" + UUID.randomUUID().toString().substring(0, 8));
        command.setOperatorId(operatorId);
        command.setFileId(file.getOriginalFilename());
        try {
            command.setRows(this.parseImportRows(file));
        } catch (IOException e) {
            throw new ValidationException("导入药品目录失败：文件内容解析失败");
        }
        ImportMedicationCatalogResult result = medicationDefinitionDomainService.importMedicationCatalog(command);
        log.info("药品目录导入完成 batchNo={}, success={}, fail={}",
            command.getBatchNo(), result.getSuccessCount(), result.getFailCount());
        // Asm：映射导入统计响应
        ImportMedicationCatalogResponse response = new ImportMedicationCatalogResponse();
        response.setBatchNo(command.getBatchNo());
        response.setTotalCount(command.getRows().size());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailCount(result.getFailCount());
        return response;
    }

    /**
     * 获取导入模板
     *
     * @param response HTTP 响应（流式写出 CSV 字节）
     * @throws ValidationException 模板查询或写出失败
     */
    public void downloadMedicationCatalog(HttpServletResponse response) {
        // Pre：读侧加载 CSV 模板
        MedicationDefinitionTemplateQueryModel template = medicationDefinitionQueryService.downloadMedicationCatalog();
        // Asm：流式写出附件响应
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + template.getTemplateName() + ".csv\"");
        try {
            response.getOutputStream().write(template.getContent());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new ValidationException("下载药品目录模板失败：响应输出异常");
        }
    }

    /**
     * 从药品定义写模型组装详情并回填定价行（用于新建后立即返回等场景，不依赖读模型投影是否已落库）。
     *
     * @param medicationDefinitionId {@link com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition} 主键
     * @return 详情 DTO
     */
    private MedicationDetailDto getMedicationDetailFromWriteModel(Long medicationDefinitionId) {
        MedicationDetailDto dto = medicationCatalogMapper.toMedicationDetailDto(
            medicationDefinitionQueryService.getMedicationCatalog(medicationDefinitionId));
        this.enrichPriceFields(dto, this.loadPriceLines(medicationDefinitionId));
        this.enrichFinTypeCodeDictText(dto);
        CatalogEditabilityQueryModel editability =
            catalogEditabilityResolver.resolveMedicationEditability(medicationDefinitionId);
        dto.setEditabilityCode(editability.getEditabilityCode());
        dto.setEditabilityMessage(editability.getEditabilityMessage());
        return dto;
    }

    /**
     * 加载单药品实例下的全部定价行。
     *
     * @param medicationId 药品定义主键
     * @return 定价行列表（可能为空）
     */
    private List<ChargeItemDefinition> loadPriceLines(Long medicationId) {
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        queryCommand.setInstanceIds(List.of(medicationId));
        return chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
    }

    /**
     * 列表页批量回填定价与供应商展示名（避免 N+1，口径对齐耗材目录 {@code enrichPageFields}）。
     */
    private void enrichMedicationCatalogPageRows(List<MedicationDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(MedicationDto::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<String, String> categoryLabelMap = terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        Map<String, String> unitCodeMap = terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<Long, List<ChargeItemDefinition>> priceMap = this.loadMedicationPriceMap(ids);
        Map<Long, String> supplierNameById = supplierQueryService.listSuppliers().stream()
            .filter(Objects::nonNull)
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(SupplierListItemQueryModel::getId, SupplierListItemQueryModel::getName, (a, b) -> a));
        for (MedicationDto dto : rows) {
            if (dto == null || dto.getId() == null) {
                continue;
            }
            if (dto.getCategoryCode() != null) {
                String categoryLabel = categoryLabelMap.get(dto.getCategoryCode());
                if (categoryLabel != null) {
                    dto.setCategoryCode_dictText(categoryLabel);
                }
            }
            if (dto.getPackageUnitCode() != null) {
                String packageUnitLabel = unitCodeMap.get(dto.getPackageUnitCode());
                if (packageUnitLabel != null) {
                    dto.setPackageUnitCode_dictText(packageUnitLabel);
                }
            }
            this.enrichPriceFields(dto, priceMap.get(dto.getId()));
            if (dto.getSupplyId() != null) {
                dto.setSupplyId_dictText(supplierNameById.get(dto.getSupplyId()));
            }
            dto.setUnitBands(this.buildMedicationCatalogUnitBands(dto, unitCodeMap));
        }
    }

    /**
     * 列表行装配 unitBands：结构/槽价算法与门诊医生站目录 {@link ProductDefinitionCatalogUnitBandsAssembler#buildDoctorStationBands} 一致。
     */
    private List<ProductDefinitionCatalogUnitBandDto> buildMedicationCatalogUnitBands(
        MedicationDto dto,
        Map<String, String> unitCodeMap
    ) {
        ProductDefinitionDisplayDto productRow = this.toProductRowForUnitBands(dto, unitCodeMap);
        String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
            productRow, PriceType.SALE.getCode());
        return ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
            productRow, bandPricingUnitCode, dto.getRetailPrice());
    }

    /** 药品列表 DTO → 产品读模型（仅用于 unitBands 装配）。 */
    private ProductDefinitionDisplayDto toProductRowForUnitBands(
        MedicationDto dto,
        Map<String, String> unitCodeMap
    ) {
        ProductDefinitionDisplayDto row = new ProductDefinitionDisplayDto();
        row.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        row.setProductDefinitionId(dto.getId());
        row.setPurchaseUnitCode(dto.getPurchaseUnitCode());
        row.setPurchaseUnitCodeDictText(this.resolveUnitLabel(unitCodeMap, dto.getPurchaseUnitCode()));
        row.setPurchaseUnitRatio(dto.getPurchaseUnitRatio());
        row.setSplitUnitCode(dto.getSplitUnitCode());
        row.setSplitUnitCodeDictText(this.resolveUnitLabel(unitCodeMap, dto.getSplitUnitCode()));
        row.setPackageUnitCode(dto.getPackageUnitCode());
        row.setPackageUnitCodeDictText(
            StringUtils.firstNonBlank(dto.getPackageUnitCode_dictText(), this.resolveUnitLabel(unitCodeMap, dto.getPackageUnitCode())));
        row.setPackageUnitRatio(dto.getPackageUnitRatio());
        row.setStockUnitCode(dto.getStockUnitCode());
        row.setStockUnitCodeDictText(this.resolveUnitLabel(unitCodeMap, dto.getStockUnitCode()));
        row.setStockUnitRatio(dto.getStockUnitRatio());
        row.setDoseUnitCode(dto.getDoseUnitCode());
        row.setDoseUnitCodeDictText(this.resolveUnitLabel(unitCodeMap, dto.getDoseUnitCode()));
        row.setDoseUnitRatio(dto.getDoseUnitRatio());
        return row;
    }

    /**
     * 从单位术语 Map 解析单位码展示名。
     *
     * @param unitCodeMap 单位码 → 展示名
     * @param unitCode    待解析单位码
     * @return 展示名；入参缺失时 null
     */
    private String resolveUnitLabel(Map<String, String> unitCodeMap, String unitCode) {
        if (unitCodeMap == null || unitCode == null || unitCode.isBlank()) {
            return null;
        }
        return unitCodeMap.get(unitCode.trim());
    }

    /**
     * 批量加载药品定价行并按 medicationId 分组，供列表/详情 enrich 回填。
     *
     * @param medicationIds 药品定义主键列表
     * @return medicationId → 定价行列表
     */
    private Map<Long, List<ChargeItemDefinition>> loadMedicationPriceMap(List<Long> medicationIds) {
        Map<Long, List<ChargeItemDefinition>> grouped = new HashMap<>();
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        cmd.setInstanceIds(medicationIds);
        List<ChargeItemDefinition> priceRows = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
        for (ChargeItemDefinition row : priceRows) {
            if (row == null || row.getInstanceId() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.getInstanceId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 将定价行金额与类型码回填到药品 DTO；{@link MedicationDetailDto} 额外回填限价等扩展字段。
     *
     * @param dto  目标 DTO（就地修改）
     * @param rows 定价行集合
     */
    private void enrichPriceFields(MedicationDto dto, List<ChargeItemDefinition> rows) {
        if (dto == null || rows == null || rows.isEmpty()) {
            return;
        }
        // 逐行按 priceType 分流：与耗材目录 enrich 逻辑对称，避免错误覆盖字段。
        for (ChargeItemDefinition row : rows) {
            if (dto instanceof MedicationDetailDto detailDto) {
                if (detailDto.getTypeCode() == null && row.getTypeCode() != null) {
                    detailDto.setTypeCode(row.getTypeCode());
                }
                if (detailDto.getYbType() == null && row.getYbType() != null) {
                    detailDto.setYbType(row.getYbType());
                }
            }
            if (PriceType.PURCHASE.getCode().equals(row.getPriceTypeCode())) {
                dto.setPurchasePrice(row.getPriceValue());
                continue;
            }
            if (PriceType.LIMIT.getCode().equals(row.getPriceTypeCode())
                && dto instanceof MedicationDetailDto detailDto) {
                detailDto.setMaximumRetailPrice(row.getPriceValue());
                continue;
            }
            if (PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                dto.setRetailPrice(row.getPriceValue());
            }
        }
    }

    /**
     * 详情/列表：财务类别编码回填展示文案（与下拉 options 的 value 同源）。
     */
    private void enrichFinTypeCodeDictText(MedicationDetailDto dto) {
        if (dto == null || dto.getTypeCode() == null || dto.getTypeCode().isBlank()) {
            return;
        }
        if (dto.getTypeCode_dictText() != null && !dto.getTypeCode_dictText().isBlank()) {
            return;
        }
        Map<String, String> labelMap = terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        String label = labelMap.get(dto.getTypeCode());
        if (label != null) {
            dto.setTypeCode_dictText(label);
        }
    }

    /**
     * 重建药品实例维度定价：先删后增，写入项由 {@link ChargePricingCommandAssembler} 按非空金额生成。
     *
     * @param instanceId         药品定义主键
     * @param typeCode           财务分类编码
     * @param ybType             医保类别
     * @param purchasePrice      采购价
     * @param retailPrice        零售价
     * @param maximumRetailPrice 限价
     */
    private void replacePriceLines(
        Long instanceId,
        String typeCode,
        String ybType,
        BigDecimal purchasePrice,
        BigDecimal retailPrice,
        BigDecimal maximumRetailPrice) {
        RemovePricingByInstanceCommand removeCommand = new RemovePricingByInstanceCommand();
        removeCommand.setInstanceTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        removeCommand.setInstanceId(instanceId);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCommand);

        List<SaveAllChargeItemDefinitionsCommand.Item> items = new ArrayList<>();
        // 三类价格行按需追加；空列表则跳过批量保存。
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.MED_MEDICATION_DEFINITION, instanceId, typeCode, ybType,
            PriceType.PURCHASE.getCode(), purchasePrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.MED_MEDICATION_DEFINITION, instanceId, typeCode, ybType,
            PriceType.SALE.getCode(), retailPrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.MED_MEDICATION_DEFINITION, instanceId, typeCode, ybType,
            PriceType.LIMIT.getCode(), maximumRetailPrice, null);
        if (items.isEmpty()) {
            return;
        }
        SaveAllChargeItemDefinitionsCommand saveCommand = new SaveAllChargeItemDefinitionsCommand();
        saveCommand.setItems(items);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(saveCommand);
    }

    /**
     * 解析 CSV：跳过首行表头，至少五列（业务号、名称、分类、单位、机构）才视为有效数据行。
     *
     * <p>CSV 格式约定：首行为表头，从第二行开始解析；列顺序固定为 busNo,name,categoryCode,purchaseUnitCode,orgId。</p>
     *
     * @param file 上传文件
     * @return 领域导入行命令列表
     * @throws IOException 流读取异常
     */
    private List<ImportMedicationCatalogCommand.MedicationDefinitionImportRowCommand> parseImportRows(MultipartFile file) throws IOException {
        List<ImportMedicationCatalogCommand.MedicationDefinitionImportRowCommand> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            // Loop：逐行读取 CSV 内容
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                String[] columns = line.split(",", -1);
                if (columns.length < 5) {
                    continue;
                }
                ImportMedicationCatalogCommand.MedicationDefinitionImportRowCommand row =
                    new ImportMedicationCatalogCommand.MedicationDefinitionImportRowCommand();
                row.setBusNo(TextValueConverter.nullIfBlank(columns[0]));
                row.setName(TextValueConverter.nullIfBlank(columns[1]));
                row.setCategoryCode(TextValueConverter.nullIfBlank(columns[2]));
                row.setPurchaseUnitCode(TextValueConverter.nullIfBlank(columns[3]));
                row.setOrgId(TextValueConverter.toNullableLong(columns[4]));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 加载供应商列表为 {@link OptionDto}（value 为供应商 id 字符串）。
     *
     * @return 下拉选项列表
     */
    private List<OptionDto> loadSupplierOptions() {
        List<SupplierListItemQueryModel> suppliers = supplierQueryService.listSuppliers();
        return OptionDtoAssembler.toOptions(
            suppliers,
            SupplierListItemQueryModel::getId,
            SupplierListItemQueryModel::getName,
            0L);
    }

    /**
     * 按机构加载药房与库房点位并合并为下拉项（排序字段优先取点位展示顺序）。
     *
     * @param orgId 机构主键（{@code null} 时返回空列表）
     * @return 下拉选项
     */
    private List<OptionDto> loadLocationOptions(Long orgId) {
        if (orgId == null) {
            return new ArrayList<>();
        }
        List<OptionDto> options = new ArrayList<>();
        List<Location> pharmacies = locationQueryService.listPharmaciesByOrganization(orgId);
        List<Location> warehouses = locationQueryService.listWarehousesByOrganization(orgId);
        options.addAll(OptionDtoAssembler.toOptionsWithSortFallback(
            pharmacies,
            Location::getId,
            Location::getName,
            location -> {
                if (location.getDisplayOrder() == null) {
                    return null;
                }
                return location.getDisplayOrder().longValue();
            },
            LocationForm.PHARMACY.getSortOrder()));
        options.addAll(OptionDtoAssembler.toOptionsWithSortFallback(
            warehouses,
            Location::getId,
            Location::getName,
            location -> {
                if (location.getDisplayOrder() == null) {
                    return null;
                }
                return location.getDisplayOrder().longValue();
            },
            LocationForm.WAREHOUSE.getSortOrder()));
        return options;
    }

    /**
     * 分页排序字段白名单：仅允许读模型上存在的可排序属性，防止拼接非法属性名。
     *
     * @param sortBy 前端传入的排序字段
     * @return 是否允许用于 Sort.by
     */
    private boolean isAllowedMedicationCatalogSortField(String sortBy) {
        if ("createdAt".equals(sortBy)) {
            return true;
        }
        if ("name".equals(sortBy)) {
            return true;
        }
        if ("busNo".equals(sortBy)) {
            return true;
        }
        if ("statusEnum".equals(sortBy)) {
            return true;
        }
        return false;
    }

    /**
     * 新建药品目录前置校验（名称与单位类字段）。
     *
     * @param request 请求体
     * @throws ValidationException 不满足必填规则
     */
    private void validateCreateRequest(CreateMedicationCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("新增药品目录失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("新增药品目录失败：药品名称不能为空");
        }
        if (request.getPurchaseUnitCode() == null || request.getPurchaseUnitCode().isBlank()) {
            throw new ValidationException("新增药品目录失败：采购单位不能为空");
        }
        if (request.getSplitUnitCode() == null || request.getSplitUnitCode().isBlank()) {
            throw new ValidationException("新增药品目录失败：拆零单位不能为空");
        }
        if (request.getPackageUnitCode() == null || request.getPackageUnitCode().isBlank()) {
            throw new ValidationException("新增药品目录失败：包装单位不能为空");
        }
        this.assertMedicationUnitRatiosPositive(request, "新增药品目录失败");
        this.assertFinTypeCodeWhenPricingPresent(
            request.getTypeCode(),
            request.getCategoryCode(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            "新增药品目录失败");
    }

    /**
     * 所属机构：请求未显式传入时，与当前登录用户所选组织单元一致（口径同 {@link #getMedicationInit()} 库房下拉）。
     */
    private void applyDefaultOrganizationIdForCreate(CreateMedicationCatalogRequest request) {
        if (request.getOrgId() == null) {
            request.setOrgId(managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
                "MED-CAT-VAL-ORG",
                "新增药品目录失败：未选择机构上下文，请先在系统中选择科室/机构"));
        }
    }

    /**
     * 更新药品目录前置校验（主键与名称）。
     *
     * @param request 请求体
     * @throws ValidationException 不满足必填规则
     */
    private void validateUpdateRequest(UpdateMedicationCatalogRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新药品目录失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("更新药品目录失败：药品名称不能为空");
        }
        if (request.getPurchaseUnitCode() == null || request.getPurchaseUnitCode().isBlank()) {
            throw new ValidationException("更新药品目录失败：采购单位不能为空");
        }
        if (request.getSplitUnitCode() == null || request.getSplitUnitCode().isBlank()) {
            throw new ValidationException("更新药品目录失败：拆零单位不能为空");
        }
        if (request.getPackageUnitCode() == null || request.getPackageUnitCode().isBlank()) {
            throw new ValidationException("更新药品目录失败：包装单位不能为空");
        }
        this.assertMedicationUnitRatiosPositive(request, "更新药品目录失败");
    }

    /**
     * 药品定价 fin_type：优先请求 {@code typeCode}，否则按 {@code categoryCode} 映射 fin_type。
     *
     * @param typeCode     财务类型编码
     * @param categoryCode 药品分类短码
     * @return fin_type_code；仍无法解析时为 {@code null}
     */
    private String resolveMedicationFinTypeCodeForPricing(String typeCode, String categoryCode) {
        if (StringUtils.isNotEmpty(typeCode)) {
            return typeCode.trim();
        }
        return FinTypeCodeMappingSupport.normalizeCategoryCodeToFinType(categoryCode);
    }

    /**
     * 维护页有价格时须能解析 fin_type（显式 typeCode 或可由 categoryCode 映射）。
     *
     * @param typeCode       财务类型
     * @param categoryCode   药品分类
     * @param purchasePrice  进价
     * @param retailPrice    零售价
     * @param messagePrefix  校验失败前缀
     */
    private void assertFinTypeCodeWhenPricingPresent(
        String typeCode,
        String categoryCode,
        BigDecimal purchasePrice,
        BigDecimal retailPrice,
        String messagePrefix) {
        if (purchasePrice == null && retailPrice == null) {
            return;
        }
        String finTypeCode = this.resolveMedicationFinTypeCodeForPricing(typeCode, categoryCode);
        if (StringUtils.isEmpty(finTypeCode)) {
            throw new ValidationException(messagePrefix + "：维护价格时财务类型不能为空");
        }
    }

    /**
     * 校验药品单位换算倍数：与耗材目录一致，四类相对拆零单位倍数必填且大于 0。
     */
    private void assertMedicationUnitRatiosPositive(CreateMedicationCatalogRequest request, String errPrefix) {
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "采购→拆零倍数", request.getPurchaseUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "库存→拆零倍数", request.getStockUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "销售→拆零倍数", request.getPackageUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "剂量→拆零倍数", request.getDoseUnitRatio());
    }

    /**
     * 校验药品单位换算倍数：必填且须大于 0。
     *
     * @param errPrefix 错误前缀（含 IF 场景）
     * @param fieldCn   字段中文名
     * @param value     待校验倍数
     * @throws ValidationException 为空或不大于 0
     */
    private static void assertPositiveUnitRatio(String errPrefix, String fieldCn, BigDecimal value) {
        if (value == null) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "须大于 0");
        }
    }

}
