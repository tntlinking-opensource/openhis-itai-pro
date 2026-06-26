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
package com.openhis.health.application.basic.medicationcatalog.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.medicationcatalog.dto.ImportMedicationCatalogResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationManageInitDto;
import com.openhis.health.application.basic.medicationcatalog.mapper.MedicationCatalogMapper;
import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.QueryMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.assembler.option.OptionDtoAssembler;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.pricing.ChargePricingCommandAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.common.converter.TextValueConverter;
import com.openhis.health.module.querysupport.pharmacy.MedicationDefinitionExternalReferenceGuard;
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
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionDomainService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.OrderCalcRuleEnum;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    /**
     * 药品目录维护页初始化：返回各字典下拉项（术语编码系统）及供应商、库房等选项。
     *
     * @return 初始化 DTO，供前端表单渲染
     */
    public MedicationManageInitDto getMedicationInit() {
        MedicationManageInitDto dto = new MedicationManageInitDto();
        // 药品分类、发布状态、领域标识等基础字典（术语编码体系名称见 TerminologyCodeSystemNameConstants。
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
        return dto;
    }

    /**
     * 分页查询药品目录列表（读主表 {@code med_medication_definition}，定价行批量回填）。
     *
     * @param query 分页与筛选条件
     * @return 分页结果
     */
    public PagedResult<MedicationDto> pageMedicationCatalog(QueryMedicationCatalogRequest query) {
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT);
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
        Page<MedicationDefinitionPageQueryModel> pageResult = medicationDefinitionQueryService.pageMedicationCatalog(
            pageQuery, PageRequest.of(pageQuery.springPageIndex(), pageQuery.getBoundedPageSize(), sort));
        List<MedicationDto> content = pageResult.getContent().stream()
            .map(medicationCatalogMapper::toMedicationDto)
            .collect(Collectors.toCollection(ArrayList::new));
        this.enrichMedicationCatalogPageRows(content);
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
        if (id == null) {
            throw new ValidationException("查询药品详情失败：id 不能为空");
        }
        return this.getMedicationDetailFromWriteModel(id);
    }

    /**
     * 新增药品目录定义并写入关联定价行。
     *
     * @param request 新建请求体
     * @return 新建后的详情
     * @throws ValidationException 必填项校验失败
     */
    public MedicationDetailDto createMedicationCatalog(CreateMedicationCatalogRequest request) {
        this.validateCreateRequest(request);
        this.applyDefaultOrganizationIdForCreate(request);
        // 先领域建档拿到主键，再刷新实例维度定价（与编辑共用 replacePriceLines）。
        Long createdId = medicationDefinitionDomainService.createMedicationDefinition(medicationCatalogMapper.toCreateCommand(request));
        this.replacePriceLines(createdId, request.getTypeCode(), request.getYbType(), request.getPurchasePrice(),
            request.getRetailPrice(), request.getMaximumRetailPrice());
        return this.getMedicationCatalog(createdId);
    }

    /**
     * 修改药品目录主数据并同步实例维度定价行。
     *
     * @param request 药品目录信息（须含主键）
     * @return 最新详情（含定价回填）
     * @throws ValidationException 校验失败
     */
    public MedicationDetailDto editMedicationCatalog(UpdateMedicationCatalogRequest request) {
        this.validateUpdateRequest(request);
        medicationDefinitionDomainService.editMedicationDefinition(medicationCatalogMapper.toEditCommand(request));
        this.replacePriceLines(request.getId(), request.getTypeCode(), request.getYbType(), request.getPurchasePrice(),
            request.getRetailPrice(), request.getMaximumRetailPrice());
        return this.getMedicationCatalog(request.getId());
    }

    /**
     * 批量停用药品目录
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void stopMedicationCatalog(List<Long> ids) {
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "停用药品目录失败：ids 不能为空");
        medicationDefinitionDomainService.retireMedicationDefinitions(medicationCatalogMapper.toRetireCommand(ids));
    }

    /**
     * 批量启用药品目录
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void startMedicationCatalog(List<Long> ids) {
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "启用药品目录失败：ids 不能为空");
        medicationDefinitionDomainService.activateMedicationDefinitions(medicationCatalogMapper.toActivateCommand(ids));
    }

    /**
     * 批量删除（废弃）药品目录。
     *
     * @param ids 药品ID列表
     * @throws ValidationException {@code ids} 为空
     */
    public void deleteMedicationCatalog(List<Long> ids) {
        GatewayIdParseUtil.requireNonEmptyLongIds(ids, "删除药品目录失败：ids 不能为空");
        medicationDefinitionExternalReferenceGuard.assertNoExternalReference(ids);
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
        if (file == null || file.isEmpty()) {
            throw new ValidationException("导入药品目录失败：文件不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入药品目录");
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
        MedicationDefinitionTemplateQueryModel template = medicationDefinitionQueryService.downloadMedicationCatalog();
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

    private String resolveUnitLabel(Map<String, String> unitCodeMap, String unitCode) {
        if (unitCodeMap == null || unitCode == null || unitCode.isBlank()) {
            return null;
        }
        return unitCodeMap.get(unitCode.trim());
    }

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
     * 校验药品单位换算倍数：与耗材目录一致，四类相对拆零单位倍数必填且大于 0。
     */
    private void assertMedicationUnitRatiosPositive(CreateMedicationCatalogRequest request, String errPrefix) {
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "采购→拆零倍数", request.getPurchaseUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "库存→拆零倍数", request.getStockUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "销售→拆零倍数", request.getPackageUnitRatio());
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "剂量→拆零倍数", request.getDoseUnitRatio());
    }

    private static void assertPositiveUnitRatio(String errPrefix, String fieldCn, BigDecimal value) {
        if (value == null) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(errPrefix + "：" + fieldCn + "须大于 0");
        }
    }

}
