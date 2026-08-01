/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.service;

import com.openhis.health.application.shared.pricing.ChargePricingCommandAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.CatalogEditabilityConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.catalog.support.CatalogEditWriteGate;
import com.openhis.health.application.basic.servicecatalog.dto.ExportServiceCatalogResponse;
import com.openhis.health.application.basic.servicecatalog.dto.ImportServiceCatalogErrorItemDto;
import com.openhis.health.application.basic.servicecatalog.dto.ImportServiceCatalogResponse;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogChildDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogDetailDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogImportFileResponse;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogImportProgressResponse;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogInitDto;
import com.openhis.health.application.basic.servicecatalog.dto.StartServiceCatalogImportResponse;
import com.openhis.health.application.basic.servicecatalog.mapper.ServiceCatalogMapper;
import com.openhis.health.application.basic.servicecatalog.request.CreateServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.DownloadServiceCatalogImportErrorRequest;
import com.openhis.health.application.basic.servicecatalog.request.ExportServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.GetServiceCatalogImportProgressRequest;
import com.openhis.health.application.basic.servicecatalog.request.ImportServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.QueryServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.UpdateServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogBusNoSequenceSync;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogExportRowAssembler;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportConceptEnsureService;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportErrorFileService;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportJob;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportRow;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportRowValidator;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportTemplateBuilder;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportTerminologyIndex;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportWorkbookParser;
import com.openhis.health.application.basic.servicecatalog.support.ServiceCatalogImportWriteSupport;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceCatalogImportConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.Whether;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.command.ActivateActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.RetireActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.command.DeleteActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionDomainService;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityQueryModel;
import com.openhis.health.application.basic.catalog.support.CatalogEditabilityResolver;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基础数据「诊疗目录」应用服务：面向活动定义（诊疗项目）的初始化下拉、分页、详情、创建/修改、批量启停与批量导入。
 *
 * <p>初始化在 {@link #getDiseaseTreatmentInit()} 中合并枚举选项（发布状态、活动类型、是否类）、组织机构树与多类术语下拉；
 * 分页与写操作委托 {@link ActivityDefinitionQueryService} / {@link ActivityDefinitionDomainService}；
 * 零售价等定价事实写入 {@code adm_charge_item_definition}（实例表 {@link TableNameConstants#WKF_ACTIVITY_DEFINITION}，价型 {@link PriceType#SALE}）。
 * 批量导入复用 {@link #createOneForImport}（建档 + 定价 + 立即发布）。</p>
 *
 * <p><b>接入方式（项目约定）</b>：由 {@code whale-web} 的 {@code AppServiceControllerRegistrar} 将 {@code *AppService}
 * 请勿为本类另写 {@code @RestController} 破坏统一网关模型。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogAppService {

    /** 活动定义写侧：创建、修改与批量启停。 */
    private final ActivityDefinitionDomainService activityDefinitionDomainService;
    /** 活动定义读侧：分页与详情聚合查询。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 诊疗目录 DTO 与领域命令映射。 */
    private final ServiceCatalogMapper serviceCatalogMapper;
    /** 术语读侧：财务类型、单位等初始化下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 组织读侧：执行科室树形下拉。 */
    private final OrganizationQueryService organizationQueryService;
    /** 收费定价行读侧：详情回显采购/零售/限价。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 收费定价行写侧：新建/编辑后按实例维度删旧建新。 */
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    /** 目录编辑业务码读侧编排。 */
    private final CatalogEditabilityResolver catalogEditabilityResolver;
    /** 目录写侧 immutable 字段门禁。 */
    private final CatalogEditWriteGate catalogEditWriteGate;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 会话科室收窄（导入写路径绑定机构）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 导入模板生成。 */
    private final ServiceCatalogImportTemplateBuilder serviceCatalogImportTemplateBuilder;
    /** 导入 xlsx 解析。 */
    private final ServiceCatalogImportWorkbookParser serviceCatalogImportWorkbookParser;
    /** 导入行校验。 */
    private final ServiceCatalogImportRowValidator serviceCatalogImportRowValidator;
    /** 导入术语索引。 */
    private final ServiceCatalogImportTerminologyIndex serviceCatalogImportTerminologyIndex;
    /** 导入前补齐缺失计价单位到 unit_code。 */
    private final ServiceCatalogImportConceptEnsureService serviceCatalogImportConceptEnsureService;
    /** 导入失败标注文件。 */
    private final ServiceCatalogImportErrorFileService serviceCatalogImportErrorFileService;
    /** 导入事务写段。 */
    private final ServiceCatalogImportWriteSupport serviceCatalogImportWriteSupport;
    /** 导入异步工作流。 */
    private final ServiceCatalogImportAsyncWorkflow serviceCatalogImportAsyncWorkflow;
    /** 新建/导入前对齐 AD 序号。 */
    private final ServiceCatalogBusNoSequenceSync serviceCatalogBusNoSequenceSync;
    /** 诊疗目录导出行组装。 */
    private final ServiceCatalogExportRowAssembler serviceCatalogExportRowAssembler;

    /**
     * 返回诊疗目录维护页全部初始化选项：状态、类型、执行科室树、诊疗大类、财务/医保/单位等术语下拉。
     *
     * @return 初始化 DTO
     */
    public ServiceCatalogInitDto getDiseaseTreatmentInit() {
        // Gate：只读页初始化，无写侧操作人
        ServiceCatalogInitDto dto = serviceCatalogMapper.getInit();
        // Pre：合并枚举、组织树与术语下拉
        List<OptionDto> statusOptions = Stream.of(PublicationStatus.values())
            .map(item -> new OptionDto(item.getSortOrder(), item.getCode(), item.getDisplay()))
            .collect(Collectors.toList());
        dto.setStatusFlagOptions(statusOptions);
        dto.setTypeEnumOptions(Stream.of(ActivityType.values())
            .map(item -> new OptionDto(item.getSortOrder(), item.getCode(), item.getDisplay()))
            .collect(Collectors.toList()));
        dto.setExeOrganizations(this.toOrganizationOptions(organizationQueryService.getOrganizationDictTree()));
        dto.setDiagnosisCategoryOptions(codeSystemQueryService.getOptionListByCodeSystemName("activity_category_code"));
        dto.setStatusWeatherOption(Stream.of(Whether.values())
            .map(item -> new OptionDto(item.getSortOrder(), item.getCode(), item.getDisplay()))
            .collect(Collectors.toList()));
        dto.setFinTypeCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE));
        dto.setMedChrgitmTypeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.MED_CHRGITM_TYPE));
        dto.setMrSettlementTypeCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(
                TerminologyCodeSystemNameConstants.MR_SETTLEMENT_TYPE_CODE));
        dto.setUnitCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.UNIT_CODE));
        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 分页查询诊疗目录列表，按业务号倒序；关键字与分类条件下推到领域分页查询。
     *
     * @param query 分页与筛选条件（须已由前端或网关填充页码与页大小）
     * @return 分页 DTO，当前实现内联从领域分页模型映射 id/busNo/name/categoryCode
     */
    public PagedResult<ServiceCatalogDto> pageServiceCatalog(QueryServiceCatalogRequest query) {
        // Gate：分页窗口由 request 提供
        // Pre：组装领域分页 Query
        ActivityDefinitionPageQuery pageQuery = new ActivityDefinitionPageQuery();
        pageQuery.setCategoryCode(query.getCategoryCode());
        pageQuery.setKeyword(query.getKeyword());
        // 本 IF 主场景是目录维护：未传参时默认含明细子项；组套等复用须显式传 false
        Boolean includePackageChildren = query.getIncludeReferencedPackageChildren();
        if (includePackageChildren == null) {
            includePackageChildren = Boolean.TRUE;
        }
        pageQuery.setIncludeReferencedPackageChildren(includePackageChildren);
        // 默认按业务号倒序，避免按创建/更新时间排序导致编辑后行位跳动
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        Page<?> page = activityDefinitionQueryService.pageServiceCatalog(
            pageQuery, PageRequest.of(query.springPageIndex(), query.getBoundedPageSize(), sort));
        List<ServiceCatalogDto> content = page.getContent().stream()
            .map(item -> {
                ServiceCatalogDto dto = new ServiceCatalogDto();
                // 领域分页返回统一条目类型，此处收窄为活动定义分页行
                var model =
                    (com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel) item;
                dto.setId(model.getId());
                dto.setBusNo(model.getBusNo());
                dto.setName(model.getName());
                dto.setCategoryCode(model.getCategoryCode());
                dto.setPackageUnitCode(model.getPackageUnitCode());
                dto.setDefEncounterUnitCode(model.getDefEncounterUnitCode());
                this.applyPublicationStatus(model.getStatusEnum(), dto::setStatusEnum, dto::setStatusEnum_enumText);
                return dto;
            })
            .collect(Collectors.toList());
        this.enrichServiceCatalogPageFields(content);
        // Asm：映射分页 DTO
        return PagedResultBuilder.build(new PagedResult<>(), content, page);
    }

    /**
     * 分页列表补齐分类展示文案、零售价与单位等术语展示文案。
     *
     * @param rows 当前页 DTO 列表（就地修改）
     */
    private void enrichServiceCatalogPageFields(List<ServiceCatalogDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, String> categoryMap = codeSystemQueryService
            .getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE)
            .stream()
            .filter(item -> item.getValue() != null)
            .collect(Collectors.toMap(OptionDto::getValue, OptionDto::getLabel, (a, b) -> a));
        Map<String, String> unitCodeMap = codeSystemQueryService
            .getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.UNIT_CODE)
            .stream()
            .filter(item -> item.getValue() != null)
            .collect(Collectors.toMap(OptionDto::getValue, OptionDto::getLabel, (a, b) -> a));
        List<Long> ids = rows.stream()
            .map(ServiceCatalogDto::getId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, List<ChargeItemDefinition>> priceMap;
        if (ids.isEmpty()) {
            priceMap = Collections.emptyMap();
        } else {
            priceMap = this.loadServicePriceMap(ids);
        }
        for (ServiceCatalogDto dto : rows) {
            if (dto == null) {
                continue;
            }
            String categoryLabel = categoryMap.get(dto.getCategoryCode());
            if (categoryLabel != null) {
                dto.setCategoryCode_dictText(categoryLabel);
            }
            String packageLabel = unitCodeMap.get(dto.getPackageUnitCode());
            if (packageLabel != null) {
                dto.setPackageUnitCode_dictText(packageLabel);
            }
            String encounterLabel = unitCodeMap.get(dto.getDefEncounterUnitCode());
            if (encounterLabel != null) {
                dto.setDefEncounterUnitCode_dictText(encounterLabel);
            }
            if (dto.getId() != null) {
                this.enrichListRetailPrice(dto, priceMap.get(dto.getId()));
            }
        }
    }

    /**
     * 列表页批量加载诊疗项目定价行。
     */
    private Map<Long, List<ChargeItemDefinition>> loadServicePriceMap(List<Long> activityIds) {
        Map<Long, List<ChargeItemDefinition>> grouped = new HashMap<>();
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        cmd.setInstanceIds(activityIds);
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
     * 列表行回填零售价。
     */
    private void enrichListRetailPrice(ServiceCatalogDto dto, List<ChargeItemDefinition> rows) {
        if (dto == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (ChargeItemDefinition row : rows) {
            if (PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                dto.setRetailPrice(row.getPriceValue());
                return;
            }
        }
    }

    /**
     * 按主键加载诊疗目录详情，将聚合查询模型中的基础信息平铺到详情 DTO（含类型、部位、医保等级等）。
     *
     * @param id 活动定义主键
     * @return 详情 DTO
     */
    public ServiceCatalogDetailDto getServiceCatalog(Long id) {
        // Pre：读侧加载诊疗目录详情聚合
        ActivityDefinitionDetailQueryModel model = activityDefinitionQueryService.getServiceCatalog(id);
        ActivityDefinitionBaseQueryModel baseInfo = model.getBaseInfo();
        ServiceCatalogDetailDto dto = new ServiceCatalogDetailDto();
        if (baseInfo == null) {
            throw new ValidationException("查询诊疗目录详情失败：基础信息不存在");
        }
        dto.setId(baseInfo.getId());
        dto.setCategoryCode(baseInfo.getCategoryCode());
        dto.setBusNo(baseInfo.getBusNo());
        dto.setName(baseInfo.getName());
        dto.setPyStr(baseInfo.getPyStr());
        dto.setWbStr(baseInfo.getWbStr());
        dto.setPackageUnitCode(baseInfo.getPackageUnitCode());
        dto.setActivityVersion(baseInfo.getActivityVersion());
        dto.setMerchandiseName(baseInfo.getMerchandiseName());
        dto.setMerchandisePyStr(baseInfo.getMerchandisePyStr());
        dto.setMerchandiseWbStr(baseInfo.getMerchandiseWbStr());
        dto.setDefEncounterUnitCode(baseInfo.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(baseInfo.getDefInhospitalUnitCode());
        dto.setTypeEnum(EnumUtil.toDisplayIntegerFromCodeOrNumber(ActivityType.class, baseInfo.getTypeEnum()));
        dto.setDescriptionText(baseInfo.getDescriptionText());
        dto.setBodySiteCode(baseInfo.getBodySiteCode());
        dto.setSpecimenCode(baseInfo.getSpecimenCode());
        dto.setOrgId(baseInfo.getOrgId());
        dto.setLocationId(baseInfo.getLocationId());
        if (baseInfo.getRuleId() == null) {
            dto.setRuleId(null);
        } else {
            dto.setRuleId(Long.valueOf(baseInfo.getRuleId()));
        }
        dto.setYbNo(baseInfo.getYbNo());
        dto.setChrgitmLv(baseInfo.getChrgitmLv());
        dto.setMrSettlementTypeCode(baseInfo.getMrSettlementTypeCode());
        dto.setYbFlag(this.toWhetherInteger(baseInfo.getYbFlag()));
        dto.setPricingFlag(this.toWhetherInteger(baseInfo.getPricingFlag()));
        dto.setYbMatchFlag(this.toWhetherInteger(baseInfo.getYbMatchFlag()));
        this.applyPublicationStatus(baseInfo.getStatusEnum(), dto::setStatusEnum, null);
        dto.setChildren(this.toServiceCatalogChildDtos(model.getChildren()));
        // 定价行从收费定义表加载，与药品/耗材目录维护页口径一致。
        this.enrichPriceFields(dto, this.loadPriceLines(id));
        CatalogEditabilityQueryModel editability = catalogEditabilityResolver.resolveActivityEditability(id);
        dto.setEditabilityCode(editability.getEditabilityCode());
        dto.setEditabilityMessage(editability.getEditabilityMessage());
        // Asm：映射详情 DTO
        return dto;
    }

    /**
     * 编辑前校验：返回业务码及说明。
     *
     * @param id 活动定义主键
     * @return 可编辑性模型
     */
    public CatalogEditabilityQueryModel validateServiceCatalogEdit(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("校验诊疗目录编辑失败：id 不能为空");
        }
        // Pre/Asm：读侧解析可编辑性
        return catalogEditabilityResolver.resolveActivityEditability(id);
    }

    /**
     * 批量删除（逻辑删除）诊疗目录项。
     *
     * @param ids 活动定义主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void deleteServiceCatalog(List<Long> ids) {
        // Gate：批量主键非空
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("删除诊疗目录失败：ids 不能为空");
        }
        // Gate 续：复用编辑第一道业务码——锁定态禁止删除
        for (Long activityId : ids) {
            CatalogEditabilityQueryModel editability =
                catalogEditabilityResolver.resolveActivityEditability(activityId);
            if (CatalogEditabilityConstants.isLocked(editability.getEditabilityCode())) {
                throw new ValidationException("删除诊疗目录失败：已发生业务引用，不允许删除");
            }
        }
        // Cmd：领域批量逻辑删除
        DeleteActivityDefinitionsCommand command = new DeleteActivityDefinitionsCommand();
        DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand item =
            new DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand();
        item.setIdList(new ArrayList<>(ids));
        command.setItem(item);
        activityDefinitionDomainService.deleteActivityDefinitions(command);
    }

    /**
     * 批量停用（退休）诊疗目录项。
     *
     * @param ids 活动定义主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void stopServiceCatalog(List<Long> ids) {
        // Gate：批量主键非空
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("停用诊疗目录失败：ids 不能为空");
        }
        // Cmd：领域批量停用
        RetireActivityDefinitionsCommand command = new RetireActivityDefinitionsCommand();
        RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand item =
            new RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand();
        item.setIdList(new ArrayList<>(ids));
        command.setItem(item);
        activityDefinitionDomainService.retireActivityDefinitions(command);
    }

    /**
     * 批量启用诊疗目录项（草稿发布或停用恢复为已发布）。
     *
     * @param ids 活动定义主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void startServiceCatalog(List<Long> ids) {
        // Gate：批量主键非空
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("启用诊疗目录失败：ids 不能为空");
        }
        // Cmd：领域批量启用
        ActivateActivityDefinitionsCommand command = new ActivateActivityDefinitionsCommand();
        ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand item =
            new ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand();
        item.setIdList(new ArrayList<>(ids));
        command.setItem(item);
        activityDefinitionDomainService.activateActivityDefinitions(command);
    }

    /**
     * 新建诊疗目录项（活动定义）。
     *
     * @param request 创建请求
     */
    public void createServiceCatalog(CreateServiceCatalogRequest request) {
        // Pre：对齐 AD 序号，避免与库内已有 bus_no 撞号
        serviceCatalogBusNoSequenceSync.syncFromExistingDefinitions();
        // Cmd：与导入共用单行创建路径（建档 + 维护态定价 + 立即发布）
        this.createOneForImport(request);
    }

    /**
     * 启动诊疗目录异步导入（返回 jobId，配合进度轮询）。
     *
     * @param request 含 xlsx 字节与可选文件名
     * @return 含 jobId 的启动响应
     */
    public StartServiceCatalogImportResponse startImportServiceCatalog(ImportServiceCatalogRequest request) {
        // Gate：上传字节
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入诊疗目录失败：文件不能为空");
        }
        // Gate 续：操作人与机构（启动线程捕获，异步还原）
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入诊疗目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "SVC-CAT-IMP-ORG",
            "导入诊疗目录失败：未选择医院上下文，请先登录并选择医院");
        // Cmd：提交异步任务
        String jobId = serviceCatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(),
            request.getFileName(),
            orgId,
            operatorId,
            this::createOneForImport);
        // Asm：返回 jobId
        StartServiceCatalogImportResponse response = new StartServiceCatalogImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询诊疗目录导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public ServiceCatalogImportProgressResponse getImportServiceCatalogProgress(
        GetServiceCatalogImportProgressRequest request) {
        // Gate：jobId 必填
        if (request == null || StringUtils.isEmpty(request.getJobId())) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_TOKEN,
                "查询导入进度失败：jobId 不能为空");
        }
        // Pre：取任务快照
        ServiceCatalogImportJob job = serviceCatalogImportAsyncWorkflow.getJob(request.getJobId());
        // Asm：映射进度响应
        ServiceCatalogImportProgressResponse response = new ServiceCatalogImportProgressResponse();
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
     * 批量导入诊疗目录（同步；兼容旧调用。页面推荐 {@link #startImportServiceCatalog} + 进度轮询）。
     *
     * @param request 含 xlsx 字节
     * @return 成功摘要（batchNo / 成功条数）
     */
    public ImportServiceCatalogResponse importServiceCatalog(ImportServiceCatalogRequest request) {
        // Gate：上传字节非空且体积受限
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入诊疗目录失败：文件不能为空");
        }
        if (request.getFileBytes().length > ServiceCatalogImportConstants.ERROR_FILE_MAX_BYTES) {
            throw new ValidationException("导入诊疗目录失败：文件过大，请拆分后重试");
        }
        // Gate 续：写路径操作人与机构
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "请先登录后再导入诊疗目录");
        Long orgId = managedOrganizationIdsResolver.requireSessionHospitalId(
            "SVC-CAT-IMP-ORG",
            "导入诊疗目录失败：未选择医院上下文，请先登录并选择医院");
        // Pre：解析 xlsx
        ServiceCatalogImportWorkbookParser.ParseResult parseResult =
            serviceCatalogImportWorkbookParser.parse(request.getFileBytes());
        // Pre 续：缺失计价单位补齐 unit_code 后全量行校验（不写库）
        serviceCatalogImportConceptEnsureService.ensureMissingConcepts(
            parseResult.getRows(), serviceCatalogImportTerminologyIndex);
        List<ImportServiceCatalogErrorItemDto> errors = serviceCatalogImportRowValidator.validate(
            parseResult.getRows(),
            orgId,
            serviceCatalogImportTerminologyIndex,
            activityDefinitionQueryService);
        // Gate：任一行失败则生成标注文件并拒绝整批
        if (errors != null && !errors.isEmpty()) {
            String token = serviceCatalogImportErrorFileService.buildAndStore(
                parseResult.getOriginalBytes(), errors);
            Map<String, String> errorMap = new LinkedHashMap<>();
            errorMap.put("errorFileToken", token);
            int index = 0;
            for (ImportServiceCatalogErrorItemDto item : errors) {
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
        String batchNo = ServiceCatalogImportConstants.BATCH_NO_PREFIX
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<ServiceCatalogImportRow> rows = parseResult.getRows();
        serviceCatalogImportWriteSupport.importRows(
            batchNo, operatorId, rows, this::createOneForImport);
        // Asm：成功响应
        ImportServiceCatalogResponse response = new ImportServiceCatalogResponse();
        response.setBatchNo(batchNo);
        response.setTotalCount(rows.size());
        response.setSuccessCount(rows.size());
        response.setFailCount(0);
        return response;
    }

    /**
     * 下载诊疗目录导入模板。
     *
     * @return xlsx 字节包装，供前端 base64 下载
     */
    public ServiceCatalogImportFileResponse downloadImportTemplate() {
        // Pre：生成易用型 xlsx 模板
        byte[] bytes = serviceCatalogImportTemplateBuilder.build();
        // Asm：包装下载响应
        ServiceCatalogImportFileResponse response = new ServiceCatalogImportFileResponse();
        response.setFileName(ServiceCatalogImportConstants.TEMPLATE_FILE_NAME);
        response.setContentType(ServiceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 按筛选条件导出诊疗目录（单 Sheet；首列编号 + 导入模板字段序）。
     *
     * <p>维护态价经 {@code findPriceLinesByProjects} 批量回填；触达
     * {@link ServiceCatalogImportConstants#EXPORT_MAX_ROWS} 时截断并标记 truncated。
     * 默认含套餐明细子项，与目录维护列表 IF 一致。</p>
     *
     * @param request 关键字与分类；可空（空=当前租户未删除全量，仍受行数上限）
     * @return xlsx 文件响应
     */
    public ExportServiceCatalogResponse exportServiceCatalog(ExportServiceCatalogRequest request) {
        // Gate：空请求视为无筛选，仍允许导出
        ExportServiceCatalogRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new ExportServiceCatalogRequest();
        }
        // Pre：组装与列表同口径的领域查询（多取 1 条用于判定是否截断）
        ActivityDefinitionPageQuery pageQuery = new ActivityDefinitionPageQuery();
        pageQuery.setKeyword(safeRequest.getKeyword());
        pageQuery.setCategoryCode(safeRequest.getCategoryCode());
        pageQuery.setIncludeReferencedPackageChildren(Boolean.TRUE);
        int fetchLimit = ServiceCatalogImportConstants.EXPORT_MAX_ROWS + 1;
        List<ActivityDefinitionBaseQueryModel> details =
            activityDefinitionQueryService.listServiceCatalogForExport(pageQuery, fetchLimit);
        boolean truncated = false;
        List<ActivityDefinitionBaseQueryModel> exportDetails = details;
        if (details.size() > ServiceCatalogImportConstants.EXPORT_MAX_ROWS) {
            truncated = true;
            exportDetails = details.subList(0, ServiceCatalogImportConstants.EXPORT_MAX_ROWS);
        }
        List<Long> ids = new ArrayList<>();
        for (ActivityDefinitionBaseQueryModel detail : exportDetails) {
            if (detail != null && detail.getId() != null) {
                ids.add(detail.getId());
            }
        }
        Map<Long, List<ChargeItemDefinition>> priceMap;
        if (ids.isEmpty()) {
            priceMap = new HashMap<>();
        } else {
            priceMap = this.loadServicePriceMap(ids);
        }
        serviceCatalogImportTerminologyIndex.load();
        List<String[]> dataRows = new ArrayList<>();
        for (ActivityDefinitionBaseQueryModel detail : exportDetails) {
            if (detail == null) {
                continue;
            }
            List<ChargeItemDefinition> priceLines = priceMap.get(detail.getId());
            dataRows.add(serviceCatalogExportRowAssembler.toColumnValues(
                detail, priceLines, serviceCatalogImportTerminologyIndex));
        }
        byte[] bytes = serviceCatalogImportTemplateBuilder.buildExport(dataRows);
        String fileName = ServiceCatalogImportConstants.EXPORT_FILE_NAME_PREFIX
            + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(ServiceCatalogImportConstants.EXPORT_FILE_NAME_TIME_PATTERN))
            + ".xlsx";
        ExportServiceCatalogResponse response = new ExportServiceCatalogResponse();
        response.setFileName(fileName);
        response.setContentType(ServiceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        response.setExportedRowCount(dataRows.size());
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 下载诊疗目录导入错误标注文件。
     *
     * @param request 含短期 token
     * @return 标注后的 xlsx
     */
    public ServiceCatalogImportFileResponse downloadImportErrorFile(
        DownloadServiceCatalogImportErrorRequest request) {
        // Gate：token 必填
        if (request == null || StringUtils.isEmpty(request.getToken())) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_TOKEN,
                "下载错误标注文件失败：token 不能为空");
        }
        // Pre：按 token 取临时文件
        byte[] bytes = serviceCatalogImportErrorFileService.getBytes(request.getToken().trim());
        // Asm：包装下载响应
        ServiceCatalogImportFileResponse response = new ServiceCatalogImportFileResponse();
        response.setFileName(
            "诊疗目录导入_错误标注_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".xlsx");
        response.setContentType(ServiceCatalogImportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(bytes);
        return response;
    }

    /**
     * 导入/新建单行创建：领域建档 + 维护态定价 + 立即发布，供写段回调与 {@link #createServiceCatalog} 共用。
     *
     * @param request 已映射的新建请求
     * @return 新建活动定义主键；创建失败时为 {@code null}
     */
    Long createOneForImport(CreateServiceCatalogRequest request) {
        // Gate：有价格时须携带 fin_type
        this.assertItemTypeCodeWhenPricingPresent(
            request.getItemTypeCode(),
            request.getRetailPrice(),
            request.getPurchasePrice(),
            "新建诊疗目录失败");
        // Pre：绑定会话机构
        this.applyDefaultOrganizationIdForCreate(request);
        // Cmd：领域创建活动定义
        Long createdId = activityDefinitionDomainService.createActivityDefinition(
            serviceCatalogMapper.converterToCreateParam(request));
        if (createdId == null) {
            return null;
        }
        // Cmd 续：实例维度定价行替换
        this.replacePriceLines(
            createdId,
            request.getItemTypeCode(),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        // Cmd 续：创建后立即发布，供医生站 ACTIVE 目录可见
        this.startServiceCatalog(List.of(createdId));
        return createdId;
    }

    /**
     * 新建时目录主数据统一挂当前会话医院（不使用会话科室）。
     *
     * @param request 创建请求（就地修改）
     */
    private void applyDefaultOrganizationIdForCreate(CreateServiceCatalogRequest request) {
        request.setOrgId(managedOrganizationIdsResolver.requireSessionHospitalId(
            "SVC-CAT-VAL-ORG",
            "新增诊疗目录失败：未选择医院上下文，请先登录并选择医院"));
    }

    /**
     * 编辑诊疗目录项。
     *
     * @param request 更新请求
     */
    public void editServiceCatalog(UpdateServiceCatalogRequest request) {
        // Gate：有价格时须携带 fin_type 与主键
        this.assertItemTypeCodeWhenPricingPresent(
            request.getItemTypeCode(),
            request.getRetailPrice(),
            request.getPurchasePrice(),
            "更新诊疗目录失败");
        if (request.getId() == null) {
            throw new ValidationException("更新诊疗目录失败：id 不能为空");
        }
        // Pre：加载现有实体、定价行与可编辑性
        ActivityDefinition existing = activityDefinitionQueryService.findOptionalActivityDefinitionEntityById(request.getId())
            .orElseThrow(() -> new ValidationException("更新诊疗目录失败：记录不存在或已删除"));
        CatalogEditabilityQueryModel editability =
            catalogEditabilityResolver.resolveActivityEditability(request.getId());
        catalogEditWriteGate.assertServiceCatalogUpdateAllowed(
            existing,
            this.loadPriceLines(request.getId()),
            request,
            editability.getEditabilityCode());
        // Cmd：领域更新主数据
        activityDefinitionDomainService.updateActivityDefinition(serviceCatalogMapper.converterToUpdateParam(request));
        // 业务分支：非锁定态才维护定价行
        if (!CatalogEditabilityConstants.isLocked(editability.getEditabilityCode())) {
            this.replacePriceLines(
                request.getId(),
                request.getItemTypeCode(),
                request.getYbType(),
                request.getPurchasePrice(),
                request.getRetailPrice(),
                request.getMaximumRetailPrice());
        }
    }

    /**
     * 布尔是否标记转为目录 DTO 用 0/1 整型；null 保持 null。
     *
     * @param value 源布尔
     * @return 1=是、0=否；null 时 null
     */
    private Integer toWhetherInteger(Boolean value) {
        if (value == null) {
            return null;
        }
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        return 0;
    }

    /**
     * 将发布状态码回填到 DTO 的枚举序与展示文案 setter。
     *
     * @param statusCode        发布状态码
     * @param statusEnumSetter  枚举序回填（可 null）
     * @param statusTextSetter  展示文案回填（可 null）
     */
    private void applyPublicationStatus(
        String statusCode,
        java.util.function.Consumer<Integer> statusEnumSetter,
        java.util.function.Consumer<String> statusTextSetter) {
        PublicationStatus publicationStatus = EnumUtil.fromCode(PublicationStatus.class, statusCode);
        if (publicationStatus == null) {
            return;
        }
        if (statusEnumSetter != null) {
            statusEnumSetter.accept(publicationStatus.getSortOrder().intValue());
        }
        if (statusTextSetter != null) {
            statusTextSetter.accept(publicationStatus.getDisplay());
        }
    }

    /**
     * 诊疗子项读模型列表映射为服务目录子项 DTO。
     *
     * @param children 活动定义子项查询结果
     * @return 子项 DTO 列表；空输入时空列表
     */
    private List<ServiceCatalogChildDto> toServiceCatalogChildDtos(List<ActivityDefinitionChildQueryModel> children) {
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServiceCatalogChildDto> result = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child == null) {
                continue;
            }
            ServiceCatalogChildDto childDto = new ServiceCatalogChildDto();
            childDto.setId(child.getId());
            childDto.setActivityId(child.getActivityId());
            childDto.setChildrenId(child.getChildrenId());
            childDto.setChildrenNum(child.getChildrenNum());
            childDto.setChildrenName(child.getChildrenName());
            result.add(childDto);
        }
        return result;
    }

    /**
     * 加载单条诊疗项目实例下的全部定价行。
     *
     * @param activityDefinitionId 活动定义主键
     * @return 定价行列表（可能为空）
     */
    private List<ChargeItemDefinition> loadPriceLines(Long activityDefinitionId) {
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        queryCommand.setInstanceIds(List.of(activityDefinitionId));
        return chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
    }

    /**
     * 将定价行金额与财务/医保类型码回填到详情 DTO。
     *
     * @param dto  详情 DTO（就地修改）
     * @param rows 定价行集合
     */
    private void enrichPriceFields(ServiceCatalogDetailDto dto, List<ChargeItemDefinition> rows) {
        if (dto == null || rows == null || rows.isEmpty()) {
            return;
        }
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
    }

    /**
     * 重建诊疗项目实例维度定价：先删后增，写入项由 {@link ChargePricingCommandAssembler} 按非空金额生成。
     *
     * @param instanceId         活动定义主键
     * @param itemTypeCode       财务分类编码
     * @param ybType             医保类别
     * @param purchasePrice      购入价（可空）
     * @param retailPrice        零售价（可空；页面上维护的零售价对应 {@link PriceType#SALE}）
     * @param maximumRetailPrice 最高零售价（可空）
     */
    private void replacePriceLines(
        Long instanceId,
        String itemTypeCode,
        String ybType,
        BigDecimal purchasePrice,
        BigDecimal retailPrice,
        BigDecimal maximumRetailPrice) {
        // 步骤一：移除该活动实例下全部旧定价，避免重复 priceType 行叠加
        RemovePricingByInstanceCommand removeCommand = new RemovePricingByInstanceCommand();
        removeCommand.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        removeCommand.setInstanceId(instanceId);
        chargeItemDefinitionDomainService.removePricingByInstance(removeCommand);

        // 步骤二：按三类价格分别追加（工具类内跳过金额为空的项）
        List<SaveAllChargeItemDefinitionsCommand.Item> items = new ArrayList<>();
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.PURCHASE.getCode(), purchasePrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.SALE.getCode(), retailPrice, null);
        ChargePricingCommandAssembler.appendPriceLineIfPresent(items,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, instanceId, itemTypeCode, ybType,
            PriceType.LIMIT.getCode(), maximumRetailPrice, null);
        if (items.isEmpty()) {
            return;
        }
        // 步骤三：批量写入收费定义（集合落库，符合批量 I/O 门禁）
        SaveAllChargeItemDefinitionsCommand saveCommand = new SaveAllChargeItemDefinitionsCommand();
        saveCommand.setItems(items);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(saveCommand);
    }

    /**
     * 维护页有价格时须显式携带 fin_type（itemTypeCode）。
     *
     * @param itemTypeCode  财务类型
     * @param retailPrice   零售价
     * @param purchasePrice 进价
     * @param messagePrefix 校验失败前缀
     */
    private void assertItemTypeCodeWhenPricingPresent(
        String itemTypeCode,
        BigDecimal retailPrice,
        BigDecimal purchasePrice,
        String messagePrefix) {
        if (retailPrice == null && purchasePrice == null) {
            return;
        }
        if (itemTypeCode == null || itemTypeCode.trim().isEmpty()) {
            throw new ValidationException(messagePrefix + "：维护价格时财务类型不能为空");
        }
    }

    /**
     * 将组织机构扁平列表组装为树形 {@link OptionDto}，根节点为无父或在集合中找不到父的节点。
     *
     * @param organizations 组织机构字典树扁平结果
     * @return 树根选项列表
     */
    private List<OptionDto> toOrganizationOptions(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Organization>> childrenByParentId = new HashMap<>();
        Set<Long> organizationIdSet = new HashSet<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            organizationIdSet.add(organization.getId());
            List<Organization> children = childrenByParentId.computeIfAbsent(
                organization.getParentId(), ignored -> new ArrayList<>());
            children.add(organization);
        }

        List<Organization> rootOrganizations = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            Long parentId = organization.getParentId();
            if (parentId == null || !organizationIdSet.contains(parentId)) {
                rootOrganizations.add(organization);
            }
        }
        return this.buildOrganizationOptions(rootOrganizations, childrenByParentId);
    }

    /**
     * 递归构建机构树选项：value 为本地序号，code 为机构 id 字符串，label 为名称，busNo 携带业务号。
     *
     * @param organizations        当前层节点
     * @param childrenByParentId   父 id 到子列表的索引
     * @return 当前层选项列表
     */
    private List<OptionDto> buildOrganizationOptions(
        List<Organization> organizations,
        Map<Long, List<Organization>> childrenByParentId
    ) {
        List<OptionDto> result = new ArrayList<>();
        long sort = 0L;
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            sort++;
            List<OptionDto> children = Collections.emptyList();
            List<Organization> childOrganizations = childrenByParentId.get(organization.getId());
            if (childOrganizations != null && !childOrganizations.isEmpty()) {
                children = this.buildOrganizationOptions(childOrganizations, childrenByParentId);
            }
            result.add(new OptionDto(
                sort,
                String.valueOf(organization.getId()),
                organization.getName(),
                organization.getBusNo(),
                children
            ));
        }
        return result;
    }
}
