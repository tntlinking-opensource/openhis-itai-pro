/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicelocationtemplate.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateDto;
import com.zxplat.application.basic.servicelocationtemplate.dto.ServiceLocationTemplateInitDto;
import com.zxplat.application.basic.servicelocationtemplate.mapper.ServiceLocationTemplateMapper;
import com.zxplat.application.basic.servicelocationtemplate.request.ListServiceLocationTemplateByOrganizationRequest;
import com.zxplat.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigItemRequest;
import com.zxplat.application.basic.servicelocationtemplate.request.SaveServiceLocationTemplateConfigRequest;
import com.zxplat.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.product.service.ProductDefinitionQueryService;
import com.zxplat.domain.custom.servicelocationtemplate.command.SaveDistributionConfigCommand;
import com.zxplat.domain.custom.servicelocationtemplate.queries.model.ServiceLocationTemplateQueryModel;
import com.zxplat.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateDomainService;
import com.zxplat.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.zxplat.domain.share.constants.DoctorStationTcmConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.MedicationCommonConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 *   <li>{@link ProductDefinitionQueryService}：具体药品/耗材定义名称批量 enrich（只取品名，不预热选品全套术语）</li>
 * </ul>
 *
 * <p>分类门禁：左树开立科室仅门诊/住院；护士站、医技、财务、药房等不得作配置锚点（对齐执行科室 WH-EXE001）。</p>
 *
 * <p>接入方式：由 {@code AppServiceControllerRegistrar} 注册为
 * {@code POST /api/app/serviceLocationTemplate/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceLocationTemplateAppService {

    /** 开立科室允许的机构分类：门诊、住院。护士站、医技、财务等不得作左树锚点。 */
    private static final Set<OrganizationClassification> ORDERING_DEPARTMENT_CLASSES = Set.of(
        OrganizationClassification.CLINIC,
        OrganizationClassification.INPATIENT);

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
    /** 药品/耗材定义批量只读（配置页仅需品名）。 */
    private final ProductDefinitionQueryService productDefinitionQueryService;

    /**
     * 返回取药科室配置页初始化下拉（科室树、药房/药库、物品表类型、分类与用法分码表）。
     *
     * @return 初始化 DTO，各选项列表非 {@code null}
     */
    public ServiceLocationTemplateInitDto init() {
        // Gate：只读页初始化，不写库
        // Pre：开立科室树仅门诊/住院（上级节点保留以便成树）；装配药房/药库与分类/用法下拉
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
     * 按开立科室加载全部有效取药规则，并批量 enrich 展示名（WH-DPL001 配置列表）。
     *
     * <p>具体项仅批量取品名（{@link ProductDefinitionQueryService#batchLoadByKeys}），
     * 不走选品全套术语预热，避免单品规则较多时列表接口被术语冷启动拖慢。</p>
     *
     * @param request 须含 {@code organizationId}
     * @return 规则 DTO 列表；无规则时返回空列表
     * @throws BusinessException 开立科室不存在或非门诊/住院科室时
     */
    public List<ServiceLocationTemplateDto> listByOrganization(ListServiceLocationTemplateByOrganizationRequest request) {
        // Gate：开立科室非空且须存在
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不能为空");
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        // Gate：开立科室主键在组织主数据中须可解析
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室不存在");
        }
        // Gate：左侧只服务门诊、住院科室；其它分类即使库里有历史规则也不在本页列出
        this.assertOrderingDepartment(organization);
        // Pre：拉取该科室全部有效规则
        List<ServiceLocationTemplateQueryModel> rows =
            serviceLocationTemplateQueryService.listDistributionRulesByOrganization(request.getOrganizationId());
        // Asm：读模型转配置 DTO，再批量回填库位/术语/品名展示列
        List<ServiceLocationTemplateDto> result = new ArrayList<>();
        for (ServiceLocationTemplateQueryModel row : rows) {
            result.add(serviceLocationTemplateMapper.toDto(row));
        }
        this.enrichDtoList(result, organization.getName());
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
        // 保存锚点同样只允许门诊/住院，防止绕过左树直接提交护士站等
        this.assertOrderingDepartment(organization);
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
     * 批量 enrich 规则行展示字段（科室名、库位名、分类/用法文案、具体项品名）。
     *
     * <p>品名走 {@link ProductDefinitionQueryService#batchLoadByKeys} 轻量批量；
     * 分类/用法/种类文案走 {@link TerminologyOptionLoader} 短窗缓存，与 init 下拉同源。</p>
     *
     * @param rows 待 enrich 列表
     * @param organizationName 已选科室名称
     */
    private void enrichDtoList(List<ServiceLocationTemplateDto> rows, String organizationName) {
        // Gate：空列表无需打库/术语
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> locationIds = new HashSet<>();
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        // Pre：收集库位与单品主键，并回填科室名/物品表固定文案
        for (ServiceLocationTemplateDto row : rows) {
            // 空行占位跳过，避免 NPE
            if (row == null) {
                continue;
            }
            row.setOrganizationName(organizationName);
            // 物品表文案固定两档，与 init 下拉一致
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                row.setItemTable_dictText(MedicationCommonConstants.SERVICE_LOCATION_ITEM_TABLE_LABEL_MEDICATION);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getItemTable())) {
                row.setItemTable_dictText(MedicationCommonConstants.SERVICE_LOCATION_ITEM_TABLE_LABEL_DEVICE);
            }
            if (row.getDefLocationId() != null) {
                locationIds.add(row.getDefLocationId());
            }
            // 仅单品规则需要查定义品名；类别规则无 itemDefinitionId
            if (row.getItemDefinitionId() != null && StringUtils.isNotEmpty(row.getItemTable())) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(row.getItemTable());
                lookup.setProductDefinitionId(row.getItemDefinitionId());
                productLookups.add(lookup);
            }
        }
        // Pre：库位名 + 品名 + 五套术语映射一次预热（术语走进程内短窗缓存）
        Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);
        // 配置列表只要品名；勿走选品术语 assembler（会冷启动十余张码表）
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionQueryService.batchLoadByKeys(productLookups);
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
        // Asm：逐行回填展示列；码表按物品表分流
        for (ServiceLocationTemplateDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getDefLocationId() != null) {
                row.setDefLocationName(locationNameById.get(row.getDefLocationId()));
            }
            Map<String, String> categoryLabels;
            // 药品/耗材发放类别分属不同术语码表，须按 itemTable 分流
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                categoryLabels = medicationCategoryLabels;
            } else {
                categoryLabels = deviceCategoryLabels;
            }
            row.setDistributionCategoryCode_dictText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(categoryLabels, row.getDistributionCategoryCode()));
            // 种类仅耗材行有值时回填文案
            if (StringUtils.isNotEmpty(row.getTypeCode())) {
                row.setTypeCode_dictText(deviceTypeLabels.get(row.getTypeCode()));
            }
            Boolean herbalCategoryFlag = null;
            // 中草药用法须走 tcm_method_code 展示映射
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                herbalCategoryFlag = this.isHerbalMedicationCategory(row.getDistributionCategoryCode());
            }
            row.setMethodCode_dictText(
                TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                    tcmMethodLabels,
                    westernMethodLabels,
                    herbalCategoryFlag,
                    row.getMethodCode()));
            // 单品规则回填定义名称；查不到时保留空，前端可回落展示 id
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
     * 将机构列表组装为左侧开立科室树：仅门诊、住院科室，医院等上级保留以便挂树。
     *
     * @return 科室树根选项
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationDictTree();
        if (organizations == null || organizations.isEmpty()) {
            return Collections.emptyList();
        }
        // 左侧只保留门诊、住院科室；医院等上级留下，否则树挂不上
        organizations = this.retainOrderingDepartmentTree(organizations);
        if (organizations.isEmpty()) {
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
     * 开立科室树裁剪：保留门诊/住院科室，以及能把它们挂起来的医院/上级节点。
     *
     * @param organizations 全量机构树平铺
     * @return 裁剪后的平铺列表；无命中科室时为空列表
     */
    private List<Organization> retainOrderingDepartmentTree(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return List.of();
        }
        Map<Long, Organization> byId = new LinkedHashMap<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            byId.put(organization.getId(), organization);
        }
        LinkedHashSet<Long> keepIds = new LinkedHashSet<>();
        // 先收门诊/住院叶科室
        for (Organization organization : byId.values()) {
            // 只收门诊、住院科室；医院根靠下面补祖先
            if (this.isOrderingDepartment(organization)) {
                keepIds.add(organization.getId());
            }
        }
        if (keepIds.isEmpty()) {
            return List.of();
        }
        // 再沿 parentId 向上补医院等祖先，否则左树挂不上
        List<Long> frontier = new ArrayList<>(keepIds);
        int index = 0;
        while (index < frontier.size()) {
            Long id = frontier.get(index);
            index++;
            Organization current = byId.get(id);
            if (current == null || current.getParentId() == null) {
                continue;
            }
            Long parentId = current.getParentId();
            if (keepIds.add(parentId)) {
                frontier.add(parentId);
            }
        }
        List<Organization> retained = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization != null
                && organization.getId() != null
                && keepIds.contains(organization.getId())) {
                retained.add(organization);
            }
        }
        return retained;
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
     * 开立锚点：科室节点且分类为门诊或住院。
     *
     * @param organization 机构节点
     * @return 可作为取药规则左树锚点时为 true
     */
    private boolean isOrderingDepartment(Organization organization) {
        if (organization == null) {
            return false;
        }
        if (!OrganizationType.DEPARTMENT.getCode().equals(organization.getTypeEnum())) {
            return false;
        }
        return ORDERING_DEPARTMENT_CLASSES.contains(organization.getClassEnum());
    }

    /**
     * 开立科室只允许门诊、住院分类的科室节点，避免护士站或医技出现在左侧锚点。
     *
     * @param organization 开立科室
     * @throws BusinessException 不是科室节点或分类不是门诊/住院时
     */
    private void assertOrderingDepartment(Organization organization) {
        if (!this.isOrderingDepartment(organization)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT,
                "开立科室须为门诊或住院科室");
        }
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
        result.add(new OptionDto(
            1L,
            TableNameConstants.MED_MEDICATION_DEFINITION,
            MedicationCommonConstants.SERVICE_LOCATION_ITEM_TABLE_LABEL_MEDICATION));
        result.add(new OptionDto(
            2L,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            MedicationCommonConstants.SERVICE_LOCATION_ITEM_TABLE_LABEL_DEVICE));
        return result;
    }
}
