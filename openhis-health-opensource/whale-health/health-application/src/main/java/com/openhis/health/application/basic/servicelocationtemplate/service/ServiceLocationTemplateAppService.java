/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicelocationtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateDto;
import com.openhis.health.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateInitDto;
import com.openhis.health.application.basic.servicelocationtemplate.mapper.ServiceLocationTemplateMapper;
import com.openhis.health.application.basic.servicelocationtemplate.request.ListServiceLocationTemplateByOrganizationRequest;
import com.openhis.health.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigItemRequest;
import com.openhis.health.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigRequest;
import com.openhis.health.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.servicelocationtemplate.command.SaveDistributionConfigCommand;
import com.openhis.health.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateDomainService;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基础数据「取药科室」配置应用服务：WH-DPL001 整科覆盖维护开立科室到发药库位的路由规则。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ServiceLocationTemplateQueryService}：按科室列出有效规则（配置读侧）</li>
 *   <li>{@link ServiceLocationTemplateDomainService}：整科覆盖保存（先软删后插，单事务在领域层）</li>
 *   <li>{@link OrganizationQueryService} / {@link LocationQueryService}：科室树、药房/药库位置与展示名</li>
 *   <li>{@link ProductDefinitionDisplayTerminologyAssembler}：具体药品/耗材定义名称 enrich（复用既有目录读能力）</li>
 * </ul>
 *
 * <p>接入方式：由 {@code AppServiceControllerRegistrar} 注册为
 * {@code POST /api/app/serviceLocationTemplate/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceLocationTemplateAppService {

    /** 物品表：药品。 */
    private static final String ITEM_TABLE_LABEL_MEDICATION = "药品";
    /** 物品表：耗材。 */
    private static final String ITEM_TABLE_LABEL_DEVICE = "耗材";

    /** 取药规则读侧。 */
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;
    /** 取药规则写侧。 */
    private final ServiceLocationTemplateDomainService serviceLocationTemplateDomainService;
    /** 请求/读模型与 DTO、领域命令映射。 */
    private final ServiceLocationTemplateMapper serviceLocationTemplateMapper;
    /** 写路径操作人门控 B.6.1。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 组织机构读侧：科室树与名称解析。 */
    private final OrganizationQueryService organizationQueryService;
    /** 位置读侧：药房/药库下拉与库位校验。 */
    private final LocationQueryService locationQueryService;
    /** 术语下拉与码→文案映射。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 药品/耗材定义批量展示加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 返回取药科室配置页初始化下拉（科室树、药房/药库、物品表类型、分类与用法分码表）。
     *
     * @return 初始化 DTO，各选项列表非 {@code null}
     */
    public ServiceLocationTemplateInitDto init() {
        // Gate：只读页初始化，不写库
        // Pre：装配科室树、药房/药库、物品表与分类/用法下拉
        // Asm：映射页初始化响应
        ServiceLocationTemplateInitDto dto = new ServiceLocationTemplateInitDto();
        dto.setOrganizationOptions(this.buildOrganizationOptions());
        dto.setDefLocationOptions(this.buildDefLocationOptions());
        dto.setItemTableOptions(this.buildItemTableOptions());
        dto.setMedicationCategoryOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
        dto.setDeviceCategoryOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        dto.setMethodCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.METHOD_CODE));
        dto.setTcmMethodCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE));
        dto.setDeviceTypeCodeOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE));
        return dto;
    }

    /**
     * 按开立科室加载全部有效取药规则，并批量 enrich 展示名。
     *
     * @param request 须含 {@code organizationId}
     * @return 规则 DTO 列表；无规则时返回空列表
     * @throws BusinessException 开立科室不存在时
     */
    public List<ServiceLocationTemplateDto> listByOrganization(ListServiceLocationTemplateByOrganizationRequest request) {
        // Gate：开立科室非空且须存在
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不能为空");
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不存在");
        }
        // Pre：拉取该科室全部有效规则
        List<ServiceLocationTemplateQueryModel> rows =
            serviceLocationTemplateQueryService.listDistributionRulesByOrganization(request.getOrganizationId());
        List<ServiceLocationTemplateDto> result = new ArrayList<>();
        for (ServiceLocationTemplateQueryModel row : rows) {
            result.add(serviceLocationTemplateMapper.toDto(row));
        }
        this.enrichDtoList(result, organization.getName());
        // Asm：返回 enrich 后的规则列表
        return result;
    }

    /**
     * 整科覆盖保存取药规则：以本次提交列表原子替换指定科室下全部规则。
     *
     * @param request 开立科室与完整规则行列表；{@code items} 为空表示清空
     * @throws BusinessException 参数非法、库位无效或配置键冲突时
     */
    public void saveDistributionConfig(SaveServiceLocationTemplateConfigRequest request) {
        // Gate：开立科室非空
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
            "请先登录后再保存取药科室配置");
        // Gate：保存前复合校验（科室、物品表、库位、用法码表）
        this.validateSaveDistributionConfigRequest(request);
        SaveDistributionConfigCommand command = serviceLocationTemplateMapper.toSaveCommand(request, operatorId);
        // Cmd：整科覆盖落库（领域层单事务先软删后插）
        serviceLocationTemplateDomainService.saveDistributionConfig(command);
    }

    /**
     * 保存前复合校验：科室存在、物品表合法、发放类别与表名匹配、用法码表匹配、发药库位有效。
     *
     * @param request 保存请求
     */
    private void validateSaveDistributionConfigRequest(SaveServiceLocationTemplateConfigRequest request) {
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不存在");
        }
        Set<Long> validLocationIds = this.loadValidPharmacyWarehouseLocationIds();
        Map<String, String> medicationCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        Map<String, String> deviceCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        Map<String, String> westernMethodLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> tcmMethodLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> deviceTypeLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE);
        List<SaveServiceLocationTemplateConfigItemRequest> items = request.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (SaveServiceLocationTemplateConfigItemRequest item : items) {
            if (item == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "规则行不能为空");
            }
            String itemTable = trimToEmpty(item.getItemTable());
            // 物品表名须与消费方 TableNameConstants 完全一致，否则医生站目录匹配不上
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                    "物品表名不合法，仅支持药品或耗材定义表");
            }
            String categoryCode = trimToEmpty(item.getDistributionCategoryCode());
            if (categoryCode.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "发放类别不能为空");
            }
            // 同一 code 在药品/耗材码表可能重复，须按 itemTable 选用对应码表校验
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
                if (!medicationCategoryLabels.containsKey(categoryCode)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                        "药品发放类别编码不在字典范围内: " + categoryCode);
                }
            } else {
                if (!deviceCategoryLabels.containsKey(categoryCode)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                        "耗材发放类别编码不在字典范围内: " + categoryCode);
                }
            }
            if (item.getDefLocationId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "默认发药位置不能为空");
            }
            // 默认库位须为有效药房/药库，与 BR-SLT-008 一致
            if (!validLocationIds.contains(item.getDefLocationId())) {
                throw new BusinessException(
                    ErrorCodeConstants.LOCATION_NOT_FOUND,
                    "默认发药位置不存在或不是有效药房/药库: " + item.getDefLocationId());
            }
            if (item.getStartTime() != null && item.getEndTime() != null && !item.getStartTime().isBefore(item.getEndTime())) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                    "生效开始时间必须早于结束时间");
            }
            String typeCode = trimToNull(item.getTypeCode());
            // 种类维仅允许耗材行；药品误填拒绝，避免配置歧义（BR-SLT-014）
            if (typeCode != null) {
                if (!TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                        "种类编码仅适用于耗材规则行");
                }
                if (!deviceTypeLabels.containsKey(typeCode)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                        "耗材种类编码不在字典范围内: " + typeCode);
                }
            }
            String methodCode = trimToNull(item.getMethodCode());
            if (methodCode != null) {
                Map<String, String> methodLabels = this.resolveMethodCodeLabelMapForValidation(
                    itemTable,
                    categoryCode,
                    westernMethodLabels,
                    tcmMethodLabels);
                // 中草药须走 tcm_method_code，西药/中成药走 method_code，与医生站开立口径一致
                if (!methodLabels.containsKey(methodCode)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                        "用法编码不在对应字典范围内: " + methodCode);
                }
            }
        }
    }

    /**
     * 批量 enrich 规则行展示字段（科室名、库位名、分类/用法文案、具体项名称）。
     *
     * @param rows 待 enrich 列表
     * @param organizationName 已选科室名称
     */
    private void enrichDtoList(List<ServiceLocationTemplateDto> rows, String organizationName) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> locationIds = new HashSet<>();
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (ServiceLocationTemplateDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setOrganizationName(organizationName);
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                row.setItemTable_dictText(ITEM_TABLE_LABEL_MEDICATION);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getItemTable())) {
                row.setItemTable_dictText(ITEM_TABLE_LABEL_DEVICE);
            }
            if (row.getDefLocationId() != null) {
                locationIds.add(row.getDefLocationId());
            }
            if (row.getItemDefinitionId() != null && StringUtils.isNotEmpty(row.getItemTable())) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(row.getItemTable());
                lookup.setProductDefinitionId(row.getItemDefinitionId());
                productLookups.add(lookup);
            }
        }
        Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        Map<String, String> medicationCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        Map<String, String> deviceCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        Map<String, String> westernMethodLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> tcmMethodLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> deviceTypeLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE);
        for (ServiceLocationTemplateDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getDefLocationId() != null) {
                row.setDefLocationName(locationNameById.get(row.getDefLocationId()));
            }
            Map<String, String> categoryLabels;
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                categoryLabels = medicationCategoryLabels;
            } else {
                categoryLabels = deviceCategoryLabels;
            }
            row.setDistributionCategoryCode_dictText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(categoryLabels, row.getDistributionCategoryCode()));
            if (StringUtils.isNotEmpty(row.getTypeCode())) {
                row.setTypeCode_dictText(deviceTypeLabels.get(row.getTypeCode()));
            }
            Boolean herbalCategoryFlag = null;
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                herbalCategoryFlag = this.isHerbalMedicationCategory(row.getDistributionCategoryCode());
            }
            row.setMethodCode_dictText(
                TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                    tcmMethodLabels,
                    westernMethodLabels,
                    herbalCategoryFlag,
                    row.getMethodCode()));
            if (row.getItemDefinitionId() != null && StringUtils.isNotEmpty(row.getItemTable())) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(row.getItemTable(), row.getItemDefinitionId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    row.setItemDefinitionName(product.getProductName());
                }
            }
        }
    }

    /**
     * 判断药品发放类别是否为中草药（术语 {@code medication_category_code} 码值 {@code 4}）。
     *
     * @param distributionCategoryCode 发放类别编码
     * @return 中草药类别时 {@code true}
     */
    private boolean isHerbalMedicationCategory(String distributionCategoryCode) {
        return DoctorStationTcmConstants.HERBAL_CATEGORY_CODE.equals(trimToEmpty(distributionCategoryCode));
    }

    /**
     * 保存前按物品表与发放类别选取用法码表，供 methodCode 合法性校验。
     *
     * @param itemTable             物品表名
     * @param distributionCategoryCode 发放类别
     * @param westernMethodLabels   {@code method_code} 码表
     * @param tcmMethodLabels       {@code tcm_method_code} 码表
     * @return 当前行应使用的码表
     */
    private Map<String, String> resolveMethodCodeLabelMapForValidation(
        String itemTable,
        String distributionCategoryCode,
        Map<String, String> westernMethodLabels,
        Map<String, String> tcmMethodLabels
    ) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)
            && this.isHerbalMedicationCategory(distributionCategoryCode)) {
            return tcmMethodLabels;
        }
        return westernMethodLabels;
    }

    /**
     * 加载全部有效药房/药库位置主键集合，供保存前 BR-SLT-008 校验。
     *
     * @return 有效库位 id 集合
     */
    private Set<Long> loadValidPharmacyWarehouseLocationIds() {
        List<Location> locations = locationQueryService.listAllWarehouseAndPharmacyLocations();
        Set<Long> ids = new HashSet<>();
        if (locations == null) {
            return ids;
        }
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                ids.add(location.getId());
            }
        }
        return ids;
    }

    /**
     * 构建开立科室树形下拉（当前组织为平铺/树形混合，不做叶节点限制）。
     *
     * @return 科室树根选项
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationDictTree();
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
        List<Organization> roots = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            Long parentId = organization.getParentId();
            if (parentId == null || !organizationIdSet.contains(parentId)) {
                roots.add(organization);
            }
        }
        return this.buildOrganizationOptionTree(roots, childrenByParentId);
    }

    /**
     * 递归构建机构树 Option。
     *
     * @param organizations 当前层节点
     * @param childrenByParentId 父 id → 子列表
     * @return 当前层选项
     */
    private List<OptionDto> buildOrganizationOptionTree(
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
                children = this.buildOrganizationOptionTree(childOrganizations, childrenByParentId);
            }
            OptionDto option = new OptionDto(
                sort,
                String.valueOf(organization.getId()),
                organization.getName(),
                organization.getBusNo(),
                children
            );
            result.add(option);
        }
        return result;
    }

    /**
     * 构建药房/药库位置下拉。
     *
     * @return 库位选项列表
     */
    private List<OptionDto> buildDefLocationOptions() {
        List<Location> locations = locationQueryService.listAllWarehouseAndPharmacyLocations();
        if (locations == null || locations.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> result = new ArrayList<>();
        long sort = 0L;
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            sort++;
            result.add(new OptionDto(sort, String.valueOf(location.getId()), location.getName()));
        }
        return result;
    }

    /**
     * 构建物品表类型固定下拉（与消费方 {@link TableNameConstants} 一致）。
     *
     * @return 物品表选项
     */
    private List<OptionDto> buildItemTableOptions() {
        List<OptionDto> result = new ArrayList<>();
        result.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, ITEM_TABLE_LABEL_MEDICATION));
        result.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, ITEM_TABLE_LABEL_DEVICE));
        return result;
    }
}
