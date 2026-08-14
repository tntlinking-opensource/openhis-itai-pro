/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.CatalogEditabilityConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.catalog.dto.CatalogLotPriceRowDto;
import com.openhis.health.application.basic.catalog.support.CatalogEditWriteGate;
import com.openhis.health.application.basic.catalog.support.CatalogLotPriceAssembler;
import com.openhis.health.application.basic.medicationcatalog.dto.ExportMedicationCatalogResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.ImportMedicationCatalogErrorItemDto;
import com.openhis.health.application.basic.medicationcatalog.dto.ImportMedicationCatalogResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationCatalogImportFileResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationCatalogImportProgressResponse;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationDto;
import com.openhis.health.application.basic.medicationcatalog.dto.MedicationManageInitDto;
import com.openhis.health.application.basic.medicationcatalog.dto.StartMedicationCatalogImportResponse;
import com.openhis.health.application.basic.medicationcatalog.mapper.MedicationCatalogMapper;
import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.DownloadMedicationCatalogImportErrorRequest;
import com.openhis.health.application.basic.medicationcatalog.request.ExportMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.GetMedicationCatalogImportProgressRequest;
import com.openhis.health.application.basic.medicationcatalog.request.ImportMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.ListMedicationLotPriceRequest;
import com.openhis.health.application.basic.medicationcatalog.request.QueryMedicationCatalogRequest;
import com.openhis.health.application.basic.medicationcatalog.request.UpdateMedicationCatalogRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
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
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogBusNoSequenceSync;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogExportRowAssembler;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportConceptEnsureService;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportErrorFileService;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportJob;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportRow;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportRowValidator;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportTemplateBuilder;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportTerminologyIndex;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportWorkbookParser;
import com.openhis.health.application.basic.medicationcatalog.support.MedicationCatalogImportWriteSupport;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionDomainService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.OrderCalcRuleEnum;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.FinTypeCodeMappingSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * 药品目录管理应用编排：分页列表（读主表）、详情回填维护态参考价、批号真实进价/售价查询、增删改与批量启停、
 * 导入（AS-BAS-CAT-002 IMP）与按筛选导出（IF-BAS-CAT-EXP-01，同导入模板列）。
 *
 * <p>职责边界：Request/DTO ↔ 领域 Command；列表查询走 {@link MedicationDefinitionQueryService} 读主表；
 * 定价行维护模式与耗材目录一致（先清实例维度旧定价再批量写入）。不做计费规则计算。
 * 维护态 {@code PURCHASE}/{@code SALE} 为采购参考价/销售参考价；真实售卖价见 {@link #listMedicationLotPrices}。</p>
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
    /** 收费定价行读侧：按实例加载采购参考价/销售参考价/限价及批号进价售价行。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 收费定价行写侧：实例维度删旧建新与批量保存。 */
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    /** 术语下拉：药品维护页各编码体系选项。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 用药频次下拉：读 cus_frequency 启用态。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 库房/药房点位：机构上下文下拉（可为空列表）。 */
    private final LocationQueryService locationQueryService;
    /** 会话科室收窄（读/写路径，{@code adm_organization.id}）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 目录编辑业务码读侧编排。 */
    private final CatalogEditabilityResolver catalogEditabilityResolver;
    /** 目录写侧 immutable 字段门禁。 */
    private final CatalogEditWriteGate catalogEditWriteGate;
    /** 导入模板生成（AS-BAS-CAT-002 IMP-01 / EXP-01 共用列骨架）。 */
    private final MedicationCatalogImportTemplateBuilder medicationCatalogImportTemplateBuilder;
    /** 导出行：详情+维护态价 → 模板 54 列中文。 */
    private final MedicationCatalogExportRowAssembler medicationCatalogExportRowAssembler;
    /** 导入 xlsx 解析。 */
    private final MedicationCatalogImportWorkbookParser medicationCatalogImportWorkbookParser;
    /** 导入行校验。 */
    private final MedicationCatalogImportRowValidator medicationCatalogImportRowValidator;
    /** 导入术语索引。 */
    private final MedicationCatalogImportTerminologyIndex medicationCatalogImportTerminologyIndex;
    /** 导入前按 Excel 补齐缺失单位/用法术语。 */
    private final MedicationCatalogImportConceptEnsureService medicationCatalogImportConceptEnsureService;
    /** 导入失败标注文件。 */
    private final MedicationCatalogImportErrorFileService medicationCatalogImportErrorFileService;
    /** 导入事务写段。 */
    private final MedicationCatalogImportWriteSupport medicationCatalogImportWriteSupport;
    /** 异步导入工作流（进度轮询）。 */
    private final MedicationCatalogImportAsyncWorkflow medicationCatalogImportAsyncWorkflow;
    /** 手工新建前对齐 MD 业务序号。 */
    private final MedicationCatalogBusNoSequenceSync medicationCatalogBusNoSequenceSync;

    /**
     * 药品目录维护页初始化：返回各字典下拉项（术语编码系统）及频次、库房等选项。
     *
     * <p>供应商改由进货/退供等业务单据维护，本初始化不再下发供应商下拉。</p>
     *
     * @return 初始化 DTO，供前端表单渲染
     */
    public MedicationManageInitDto getMedicationInit() {
        // Gate：只读页初始化，无写侧操作人
        MedicationManageInitDto dto = new MedicationManageInitDto();
        // Pre：加载术语下拉、频次与库房选项（不含供应商）
        dto.setMedicationCategoryCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        dto.setStatusFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.PUBLICATION_STATUS));
        dto.setDomainFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_DOMAIN));
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
        dto.setDddUnitCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DDD_UNIT));
        dto.setDoseFromCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_FROM_CODE));
        dto.setRxFlagOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.RX_FLAG));
        dto.setDosageInstructionOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION));
        dto.setUnitCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE));
        dto.setDoseUnitCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_UNIT));
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
        pageQuery.setBasicFlag(query.getBasicFlag());
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
     * 查询药品批号维真实进价/售价（当前 ACTIVE 价）。
     *
     * <p>与详情回填的维护态 {@code PURCHASE}/{@code SALE}（采购参考价/销售参考价）分离：
     * 本接口仅返回 {@code LOT_NUMBER_COST}（进价）与 {@code LOT_NUMBER_PRICE}（售价），金额为每包装单位。</p>
     *
     * @param request 药品主键；{@code lotNumber} 可空（空=全部批号）
     * @return 批号价行列表；无批号定价时为空列表
     * @throws ValidationException {@code id} 为空或药品不存在
     */
    public List<CatalogLotPriceRowDto> listMedicationLotPrices(ListMedicationLotPriceRequest request) {
        // Gate：请求与药品主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("查询药品批号价格失败：id 不能为空");
        }
        Long medicationId = request.getId();
        // Pre：确认药品主档存在，避免对已删定义返回假空列表
        medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(medicationId)
            .orElseThrow(() -> new ValidationException("查询药品批号价格失败：记录不存在或已删除"));
        // Asm：当前价定价行 → 按批号归并进价/售价
        return CatalogLotPriceAssembler.assembleFromActivePriceLines(
            this.loadPriceLines(medicationId), request.getLotNumber());
    }

    /**
     * 新增药品目录定义并写入关联定价行。
     *
     * @param request 新建请求体
     * @return 新建后的详情
     * @throws ValidationException 必填项校验失败
     */
    public MedicationDetailDto createMedicationCatalog(CreateMedicationCatalogRequest request) {
        // Pre：对齐 MD 序号，避免与库内已有 bus_no 撞号
        medicationCatalogBusNoSequenceSync.syncFromExistingDefinitions();
        // Cmd：与导入共用单行创建路径（建档 + 维护态定价）
        Long createdId = this.createOneForImport(request);
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
        // Pre：药品目录不再采集供应商，写路径清空 supplyId（忽略请求入参与历史回显）
        request.setSupplyId(null);
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
        // Gate 续：删除与编辑不同——任一业务引用（临床或入库）即整单禁删；编辑才分二级/一级与字段裁剪
        for (Long medicationId : ids) {
            CatalogEditabilityQueryModel editability =
                catalogEditabilityResolver.resolveMedicationEditability(medicationId);
            if (CatalogEditabilityConstants.isLocked(editability.getEditabilityCode())) {
                throw new ValidationException("删除药品目录失败：已发生业务引用，不允许删除");
            }
        }
        // Cmd：领域批量废弃
        medicationDefinitionDomainService.discardMedicationDefinition(medicationCatalogMapper.toDiscardCommand(ids));
    }

    /**
     * 启动药品目录异步导入（含进度轮询，AS-BAS-CAT-002 进度条）。
     *
     * @param request 含 fileBytes
     * @return jobId
     */
    public StartMedicationCatalogImportResponse startImportMedicationCatalog(
        ImportMedicationCatalogRequest request) {
        // Gate：上传字节
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入药品目录失败：文件不能为空");
        }
        // Gate 续：操作人与机构（启动线程捕获，异步还原）
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入药品目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "MED-CAT-IMP-ORG",
            "导入药品目录失败：未选择医院上下文，请先登录并选择医院");
        // Cmd：提交异步任务
        String jobId = medicationCatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(),
            request.getFileName(),
            orgId,
            operatorId,
            this::createOneForImport);
        // Asm
        StartMedicationCatalogImportResponse response = new StartMedicationCatalogImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询药品目录导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public MedicationCatalogImportProgressResponse getImportMedicationCatalogProgress(
        GetMedicationCatalogImportProgressRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getJobId())) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_TOKEN,
                "查询导入进度失败：jobId 不能为空");
        }
        // Pre
        MedicationCatalogImportJob job = medicationCatalogImportAsyncWorkflow.getJob(request.getJobId());
        // Asm
        MedicationCatalogImportProgressResponse response = new MedicationCatalogImportProgressResponse();
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
     * 批量导入药品目录（同步；兼容旧调用。页面推荐 {@link #startImportMedicationCatalog} + 进度轮询）。
     *
     * <p>网关无法注入 {@code MultipartFile}，入参为 xlsx 字节。先全量校验；失败整批不落库并返回
     * {@link ValidationException#getErrors()}（含 {@code errorFileToken} 与分行错误）。通过后事务内逐行复用创建+定价。</p>
     *
     * @param request 含 fileBytes 的导入请求
     * @return 批次号与成功条数
     * @throws ValidationException 校验失败（errors 含行错误与 token）
     * @throws BusinessException   模板/文件 Gate 失败
     */
    public ImportMedicationCatalogResponse importMedicationCatalog(ImportMedicationCatalogRequest request) {
        // Gate：上传字节非空且体积受限
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入药品目录失败：文件不能为空");
        }
        if (request.getFileBytes().length > MedicationCatalogImportConstants.ERROR_FILE_MAX_BYTES) {
            throw new ValidationException("导入药品目录失败：文件过大，请拆分后重试");
        }
        // Gate 续：写路径操作人与机构
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入药品目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "MED-CAT-IMP-ORG",
            "导入药品目录失败：未选择医院上下文，请先登录并选择医院");
        // Pre：解析 xlsx
        MedicationCatalogImportWorkbookParser.ParseResult parseResult =
            medicationCatalogImportWorkbookParser.parse(request.getFileBytes());
        // Pre 续：按 Excel 补齐缺失单位/用法术语，再全量行校验（不写目录主数据）
        medicationCatalogImportConceptEnsureService.ensureMissingConcepts(
            parseResult.getRows(), medicationCatalogImportTerminologyIndex);
        List<ImportMedicationCatalogErrorItemDto> errors = medicationCatalogImportRowValidator.validate(
            parseResult.getRows(),
            orgId,
            medicationCatalogImportTerminologyIndex,
            medicationDefinitionQueryService);
        // Gate：任一行失败则生成标注文件并拒绝整批
        if (errors != null && !errors.isEmpty()) {
            String token = medicationCatalogImportErrorFileService.buildAndStore(
                parseResult.getOriginalBytes(), errors);
            Map<String, String> errorMap = new LinkedHashMap<>();
            errorMap.put("errorFileToken", token);
            int index = 0;
            for (ImportMedicationCatalogErrorItemDto item : errors) {
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
        String batchNo = MedicationCatalogImportConstants.BATCH_NO_PREFIX
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<MedicationCatalogImportRow> rows = parseResult.getRows();
        int created = medicationCatalogImportWriteSupport.importRows(
            batchNo, operatorId, orgId, rows, this::createOneForImport, null);
        int skipped = rows.size() - created;
        log.info("药品目录导入完成 batchNo={}, success={}, skippedExisting={}", batchNo, created, skipped);
        // Asm：成功响应
        ImportMedicationCatalogResponse response = new ImportMedicationCatalogResponse();
        response.setBatchNo(batchNo);
        response.setTotalCount(rows.size());
        response.setSuccessCount(created);
        response.setFailCount(0);
        return response;
    }

    /**
     * 下载药品目录导入模板（AS-BAS-CAT-002 · IF-BAS-CAT-IMP-01）。
     *
     * <p>返回 xlsx 字节包装，供前端 base64 下载；替代旧 CSV 流式写出。</p>
     *
     * @return 模板文件响应
     */
    public MedicationCatalogImportFileResponse downloadImportTemplate() {
        // Pre：生成易用型 xlsx 模板
        byte[] bytes = medicationCatalogImportTemplateBuilder.build();
        // Asm：包装下载响应
        MedicationCatalogImportFileResponse response = new MedicationCatalogImportFileResponse();
        response.setFileName(MedicationCatalogImportConstants.TEMPLATE_FILE_NAME);
        response.setContentType(MedicationCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 按筛选条件导出药品目录（AS-BAS-CAT-002 · IF-BAS-CAT-EXP-01）。
     *
     * <p>列序与导入模板 v1.6 字段名一致，但版式为<strong>单 Sheet</strong>（表头 + 数据紧挨，无示例/指导页）；
     * 维护态价经 {@code findPriceLinesByProjects} 批量回填（§2.2.3 目录维护当前价）。
     * 触达 {@link MedicationCatalogImportConstants#EXPORT_MAX_ROWS} 时截断并标记 truncated。</p>
     *
     * @param request 关键字与分类；可空（空=当前租户未删除全量，仍受行数上限）
     * @return xlsx 文件响应
     */
    public ExportMedicationCatalogResponse exportMedicationCatalog(ExportMedicationCatalogRequest request) {
        // Gate：空请求视为无筛选，仍允许导出
        ExportMedicationCatalogRequest safeRequest = request;
        if (safeRequest == null) {
            // 无 body 时按「不限关键字/分类」导出，避免网关空 JSON 拒答
            safeRequest = new ExportMedicationCatalogRequest();
        }
        // Pre：组装与列表同口径的领域查询（多取 1 条用于判定是否截断）
        MedicationDefinitionCatalogPageQuery pageQuery = new MedicationDefinitionCatalogPageQuery();
        pageQuery.setKeyword(safeRequest.getKeyword());
        pageQuery.setCategoryCode(safeRequest.getCategoryCode());
        pageQuery.setBasicFlag(safeRequest.getBasicFlag());
        int fetchLimit = MedicationCatalogImportConstants.EXPORT_MAX_ROWS + 1;
        List<MedicationDefinitionDetailQueryModel> details =
            medicationDefinitionQueryService.listMedicationCatalogForExport(pageQuery, fetchLimit);
        boolean truncated = false;
        List<MedicationDefinitionDetailQueryModel> exportDetails = details;
        if (details.size() > MedicationCatalogImportConstants.EXPORT_MAX_ROWS) {
            // 触顶：截断后仍交付文件，由 truncated 提示前端缩小筛选
            truncated = true;
            exportDetails = details.subList(0, MedicationCatalogImportConstants.EXPORT_MAX_ROWS);
        }
        // Pre 续：收集主键后批量加载维护态定价行（目录导出合法当前价场景）
        List<Long> ids = new ArrayList<>();
        for (MedicationDefinitionDetailQueryModel detail : exportDetails) {
            if (detail != null && detail.getId() != null) {
                ids.add(detail.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceMap;
        if (ids.isEmpty()) {
            // 无命中行：跳过定价查询，避免空 instanceIds 无意义往返
            priceMap = new HashMap<>();
        } else {
            priceMap = this.loadMedicationPriceMap(ids);
        }
        medicationCatalogImportTerminologyIndex.load();
        // Asm：详情 + 价 → 模板列 → xlsx
        List<String[]> dataRows = new ArrayList<>();
        for (MedicationDefinitionDetailQueryModel detail : exportDetails) {
            if (detail == null) {
                // 跳过空元素，保证列行数与有效药品一致
                continue;
            }
            List<ChargeItemDefinition> priceLines = priceMap.get(detail.getId());
            dataRows.add(medicationCatalogExportRowAssembler.toColumnValues(
                detail, priceLines, medicationCatalogImportTerminologyIndex));
        }
        byte[] bytes = medicationCatalogImportTemplateBuilder.buildExport(dataRows);
        String fileName = MedicationCatalogImportConstants.EXPORT_FILE_NAME_PREFIX
            + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(MedicationCatalogImportConstants.EXPORT_FILE_NAME_TIME_PATTERN))
            + ".xlsx";
        ExportMedicationCatalogResponse response = new ExportMedicationCatalogResponse();
        response.setFileName(fileName);
        response.setContentType(MedicationCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        response.setExportedRowCount(dataRows.size());
        response.setTruncated(truncated);
        log.info("药品目录导出完成 rows={}, truncated={}, keyword={}, categoryCode={}",
            dataRows.size(), truncated, safeRequest.getKeyword(), safeRequest.getCategoryCode());
        return response;
    }

    /**
     * 兼容旧方法名：下载导入模板（同 {@link #downloadImportTemplate()}）。
     *
     * @return 模板文件响应
     */
    public MedicationCatalogImportFileResponse downloadMedicationCatalog() {
        return this.downloadImportTemplate();
    }

    /**
     * 下载导入失败标注文件（AS-BAS-CAT-002 · IF-BAS-CAT-IMP-03）。
     *
     * @param request 含 errorFileToken
     * @return 标注后的 xlsx
     * @throws BusinessException token 无效或过期
     */
    public MedicationCatalogImportFileResponse downloadImportErrorFile(
        DownloadMedicationCatalogImportErrorRequest request) {
        // Gate：token 必填
        if (request == null || StringUtils.isEmpty(request.getToken())) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_TOKEN,
                "下载错误标注文件失败：token 不能为空");
        }
        // Pre：按 token 取临时文件
        byte[] bytes = medicationCatalogImportErrorFileService.getBytes(request.getToken().trim());
        // Asm：包装下载响应
        MedicationCatalogImportFileResponse response = new MedicationCatalogImportFileResponse();
        response.setFileName(
            "药品目录导入_错误标注_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".xlsx");
        response.setContentType(MedicationCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 导入/新建单行创建：领域建档 + 维护态定价，供写段回调与 {@link #createMedicationCatalog} 共用。
     *
     * @param request 已映射的新建请求
     * @return 新建药品定义主键
     */
    Long createOneForImport(CreateMedicationCatalogRequest request) {
        // Gate：创建入参校验（含单位倍数与定价财务类型）
        this.validateCreateRequest(request);
        // Pre：绑定会话机构；清空供应商
        this.applyDefaultOrganizationIdForCreate(request);
        request.setSupplyId(null);
        // Cmd：领域建档（默认 ACTIVE）
        Long createdId = medicationDefinitionDomainService.createMedicationDefinition(
            medicationCatalogMapper.toCreateCommand(request));
        // Cmd 续：维护态 PURCHASE/SALE/LIMIT
        this.replacePriceLines(
            createdId,
            this.resolveMedicationFinTypeCodeForPricing(request.getTypeCode(), request.getCategoryCode()),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        return createdId;
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
     * 列表页批量回填定价与分类/单位展示名（避免 N+1，口径对齐耗材目录 {@code enrichPageFields}）。
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
        // 计量/剂量/DDD 合并映射，避免 code 回显不到中文名
        Map<String, String> unitCodeMap = this.buildMedicationUnitLabelMap();
        Map<Long, List<ChargeItemDefinition>> priceMap = this.loadMedicationPriceMap(ids);
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
            // 追溯码单位与包装等同属 unit_code 术语
            if (dto.getTraceUnitCode() != null) {
                String traceUnitLabel = unitCodeMap.get(dto.getTraceUnitCode());
                if (traceUnitLabel != null) {
                    dto.setTraceUnitCode_dictText(traceUnitLabel);
                }
            }
            this.enrichPriceFields(dto, priceMap.get(dto.getId()));
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
     * 药品单位展示名：合并 {@code unit_code} + {@code dose_unit} + {@code ddd_unit}。
     */
    private Map<String, String> buildMedicationUnitLabelMap() {
        Map<String, String> merged = new HashMap<>();
        this.putAllUnitLabels(merged, TerminologyCodeSystemNameConstants.UNIT_CODE);
        this.putAllUnitLabels(merged, TerminologyCodeSystemNameConstants.DOSE_UNIT);
        this.putAllUnitLabels(merged, TerminologyCodeSystemNameConstants.DDD_UNIT);
        return merged;
    }

    private void putAllUnitLabels(Map<String, String> target, String codeSystemName) {
        Map<String, String> labels = terminologyOptionLoader.buildLabelMap(codeSystemName);
        if (labels == null || labels.isEmpty()) {
            return;
        }
        target.putAll(labels);
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
     * @param purchasePrice      采购参考价（维护态 {@code PURCHASE}）
     * @param retailPrice        销售参考价（维护态 {@code SALE}）
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
        this.assertMedicationTraceUnitRequired(request, "新增药品目录失败");
        this.assertFinTypeCodeWhenPricingPresent(
            request.getTypeCode(),
            request.getCategoryCode(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            "新增药品目录失败");
    }

    /**
     * 所属机构：目录主数据统一挂当前会话医院（不使用会话科室）。
     */
    private void applyDefaultOrganizationIdForCreate(CreateMedicationCatalogRequest request) {
        request.setOrgId(managedOrganizationIdsResolver.requireSessionHospitalId(
            "MED-CAT-VAL-ORG",
            "新增药品目录失败：未选择医院上下文，请先登录并选择医院"));
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
        this.assertMedicationTraceUnitRequired(request, "更新药品目录失败");
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
     * @param purchasePrice  采购参考价
     * @param retailPrice    销售参考价
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
     * 药品追溯码单位闸门：{@code traceUnitCode} 与 {@code traceUnitRatio} 均必填，且倍数须 &gt; 0。
     *
     * <p>贴码单位可游离于五类业务单位；同意入库/期初扫码按药典该字段计码。</p>
     *
     * @param request   创建或更新请求
     * @param errPrefix 异常前缀（新建/更新场景）
     * @throws ValidationException 缺字段或倍数非正
     */
    private void assertMedicationTraceUnitRequired(CreateMedicationCatalogRequest request, String errPrefix) {
        String traceUnitCode = request.getTraceUnitCode();
        // 缺追溯码单位则无法在入库/发药侧解析贴码口径
        if (StringUtils.isEmpty(traceUnitCode) || traceUnitCode.isBlank()) {
            throw new ValidationException(errPrefix + "：追溯码单位不能为空");
        }
        MedicationCatalogAppService.assertPositiveUnitRatio(errPrefix, "追溯码单位→拆零倍数", request.getTraceUnitRatio());
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
