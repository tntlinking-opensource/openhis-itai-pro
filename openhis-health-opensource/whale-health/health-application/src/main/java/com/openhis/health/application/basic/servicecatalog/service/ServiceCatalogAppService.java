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

package com.openhis.health.application.basic.servicecatalog.service;

import com.openhis.health.module.common.assembler.pricing.ChargePricingCommandAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogChildDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogDetailDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogDto;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogInitDto;
import com.openhis.health.application.basic.servicecatalog.mapper.ServiceCatalogMapper;
import com.openhis.health.application.basic.servicecatalog.request.CreateServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.QueryServiceCatalogRequest;
import com.openhis.health.application.basic.servicecatalog.request.UpdateServiceCatalogRequest;
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
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionDomainService;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基础数据「诊疗目录」应用服务：面向活动定义（诊疗项目）的初始化下拉、分页、详情、创建/修改与批量启停。
 *
 * <p>初始化在 {@link #getDiseaseTreatmentInit()} 中合并枚举选项（发布状态、活动类型、是否类）、组织机构树与多类术语下拉；
 * 分页与写操作委托 {@link ActivityDefinitionQueryService} / {@link ActivityDefinitionDomainService}；
 * 零售价等定价事实写入 {@code adm_charge_item_definition}（实例表 {@link TableNameConstants#WKF_ACTIVITY_DEFINITION}，价型 {@link PriceType#SALE}）。</p>
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

    /**
     * 返回诊疗目录维护页全部初始化选项：状态、类型、执行科室树、诊疗大类、财务/医保/单位等术语下拉。
     *
     * @return 初始化 DTO
     */
    public ServiceCatalogInitDto getDiseaseTreatmentInit() {
        ServiceCatalogInitDto dto = serviceCatalogMapper.getInit();
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
        dto.setUnitCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.UNIT_CODE));
        return dto;
    }

    /**
     * 分页查询诊疗目录列表，按创建时间倒序；关键字与分类条件下推到领域分页查询。
     *
     * @param query 分页与筛选条件（须已由前端或网关填充页码与页大小）
     * @return 分页 DTO，当前实现内联从领域分页模型映射 id/busNo/name/categoryCode
     */
    public PagedResult<ServiceCatalogDto> pageServiceCatalog(QueryServiceCatalogRequest query) {
        ActivityDefinitionPageQuery pageQuery = new ActivityDefinitionPageQuery();
        pageQuery.setCategoryCode(query.getCategoryCode());
        pageQuery.setKeyword(query.getKeyword());
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT);
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
        return PagedResult.of(content, page.getTotalElements(), page.getNumber() + 1, page.getSize());
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
        Map<Long, List<ChargeItemDefinition>> priceMap = ids.isEmpty()
            ? Collections.emptyMap()
            : this.loadServicePriceMap(ids);
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
        dto.setTypeEnum(parseInteger(baseInfo.getTypeEnum()));
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
        dto.setYbFlag(this.toWhetherInteger(baseInfo.getYbFlag()));
        dto.setPricingFlag(this.toWhetherInteger(baseInfo.getPricingFlag()));
        dto.setYbMatchFlag(this.toWhetherInteger(baseInfo.getYbMatchFlag()));
        this.applyPublicationStatus(baseInfo.getStatusEnum(), dto::setStatusEnum, null);
        dto.setChildren(this.toServiceCatalogChildDtos(model.getChildren()));
        // 定价行从收费定义表加载，与药品/耗材目录维护页口径一致。
        this.enrichPriceFields(dto, this.loadPriceLines(id));
        return dto;
    }

    /**
     * 批量删除（逻辑删除）诊疗目录项。
     *
     * @param ids 活动定义主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void deleteServiceCatalog(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("删除诊疗目录失败：ids 不能为空");
        }
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
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("停用诊疗目录失败：ids 不能为空");
        }
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
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("启用诊疗目录失败：ids 不能为空");
        }
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
        this.assertItemTypeCodeWhenPricingPresent(
            request.getItemTypeCode(),
            request.getRetailPrice(),
            request.getPurchasePrice(),
            "新建诊疗目录失败");
        // 先落诊疗项目主数据，再写入实例维度定价行（含零售价 SALE）。
        Long createdId = activityDefinitionDomainService.createActivityDefinition(
            serviceCatalogMapper.converterToCreateParam(request));
        if (createdId != null) {
            this.replacePriceLines(
                createdId,
                request.getItemTypeCode(),
                request.getYbType(),
                request.getPurchasePrice(),
                request.getRetailPrice(),
                request.getMaximumRetailPrice());
            // 领域创建默认草稿；保存维护页后立即发布，避免医生站目录（仅 ACTIVE）查不到新建项。
            this.startServiceCatalog(List.of(createdId));
        }
    }

    /**
     * 编辑诊疗目录项。
     *
     * @param request 更新请求
     */
    public void editServiceCatalog(UpdateServiceCatalogRequest request) {
        this.assertItemTypeCodeWhenPricingPresent(
            request.getItemTypeCode(),
            request.getRetailPrice(),
            request.getPurchasePrice(),
            "更新诊疗目录失败");
        // 步骤一：更新诊疗项目主数据（含子项与医保属性）
        activityDefinitionDomainService.updateActivityDefinition(serviceCatalogMapper.converterToUpdateParam(request));
        // 步骤二：主键存在时同步刷新实例维度定价行
        if (request.getId() != null) {
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
     * 将领域侧字符串型枚举值解析为整型；空白或非法格式返回 {@code null}，与 Mapper 行为一致。
     *
     * @param value 数字字符串
     * @return 整型或 {@code null}
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toWhetherInteger(Boolean value) {
        if (value == null) {
            return null;
        }
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

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
