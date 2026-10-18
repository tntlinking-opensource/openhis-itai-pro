/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.application.basic.chargediscountconfig.dto.ChargeDiscountConfigDto;
import com.zxplat.application.basic.chargediscountconfig.dto.ChargeDiscountConfigPageInitDto;
import com.zxplat.application.basic.chargediscountconfig.dto.DiscountCatalogOptionDto;
import com.zxplat.application.basic.chargediscountconfig.mapper.ChargeDiscountConfigMapper;
import com.zxplat.application.basic.chargediscountconfig.request.ChangeChargeDiscountConfigStatusRequest;
import com.zxplat.application.basic.chargediscountconfig.request.CreateChargeDiscountConfigRequest;
import com.zxplat.application.basic.chargediscountconfig.request.DeleteChargeDiscountConfigRequest;
import com.zxplat.application.basic.chargediscountconfig.request.ModifyChargeDiscountConfigRequest;
import com.zxplat.application.basic.chargediscountconfig.request.PageChargeDiscountConfigsRequest;
import com.zxplat.application.basic.chargediscountconfig.request.SearchDiscountCatalogOptionsRequest;
import com.zxplat.application.basic.chargediscountconfig.response.CreateChargeDiscountConfigResponse;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.custom.chargediscountconfig.command.ChangeChargeDiscountConfigStatusCommand;
import com.zxplat.domain.custom.chargediscountconfig.command.DeleteChargeDiscountConfigCommand;
import com.zxplat.domain.custom.chargediscountconfig.queries.ChargeDiscountConfigPageQuery;
import com.zxplat.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;
import com.zxplat.domain.custom.chargediscountconfig.service.ChargeDiscountConfigDomainService;
import com.zxplat.domain.custom.chargediscountconfig.service.ChargeDiscountConfigQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.zxplat.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.ChargeDiscountConfigConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.ChargeDiscountConfigStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基础配置「收费折扣配置」应用服务（AS-BAS-CFG-002）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ChargeDiscountConfigQueryService}：分页/详情读侧（仅本聚合）</li>
 *   <li>{@link ChargeDiscountConfigDomainService}：创建/修改/启停/软删写侧</li>
 *   <li>三大目录 QueryService + {@link TerminologyOptionLoader}：目录存在性 Gate 与选品/大类检索</li>
 *   <li>{@link PageWindowGate} / {@link CurrentPractitionerResolver}：分页与写路径操作人门控</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar → POST /api/app/chargeDiscountConfig/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeDiscountConfigAppService {

    /** 收费折扣配置读侧。 */
    private final ChargeDiscountConfigQueryService chargeDiscountConfigQueryService;
    /** 收费折扣配置写侧。 */
    private final ChargeDiscountConfigDomainService chargeDiscountConfigDomainService;
    /** 请求/读模型与 DTO、命令映射。 */
    private final ChargeDiscountConfigMapper chargeDiscountConfigMapper;
    /** 药品目录读侧（单项存在性与选品）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 诊疗目录读侧（单项存在性与选品）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 耗材目录读侧（单项存在性与选品）。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    /** 术语大类下拉与码→名映射。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 分页窗口门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 加载配置页初始化数据（IF-CDC-01）：三大目录表选项与状态枚举；不下发 scope/mode。
     *
     * @return 初始化 DTO
     */
    public ChargeDiscountConfigPageInitDto loadChargeDiscountConfigPageInit() {
        // Gate：只读初始化，不写库
        // Asm：组装三大目录 Option + 状态 Option
        ChargeDiscountConfigPageInitDto dto = new ChargeDiscountConfigPageInitDto();
        dto.setProductTableOptions(this.buildProductTableOptions());
        dto.setStatusOptions(EnumUtil.toOptions(ChargeDiscountConfigStatus.class));
        return dto;
    }

    /**
     * 分页查询收费折扣配置（IF-CDC-02），并 enrich 目录名与状态文案。
     *
     * @param request 筛选与分页；pageNo/pageSize 必填
     * @return 分页 DTO
     */
    public PagedResult<ChargeDiscountConfigDto> pageChargeDiscountConfigs(PageChargeDiscountConfigsRequest request) {
        // Gate：分页窗口必填且 ≥1，避免非法页参打库
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "分页查询失败：pageNo/pageSize 须为正整数");
        // Pre：装配领域分页条件并读侧查询
        ChargeDiscountConfigPageQuery pageQuery = new ChargeDiscountConfigPageQuery();
        pageQuery.setPageNo(request.getPageNo());
        pageQuery.setPageSize(request.getPageSize());
        pageQuery.setProductTable(trimToNull(request.getProductTable()));
        pageQuery.setStatusEnum(trimToNull(request.getStatusEnum()));
        pageQuery.setKeyword(trimToNull(request.getKeyword()));
        Page<ChargeDiscountConfigQueryModel> page =
            chargeDiscountConfigQueryService.pageChargeDiscountConfigs(pageQuery);
        // Asm：投影 DTO 并补目录/状态展示名
        List<ChargeDiscountConfigDto> rows = new ArrayList<>();
        for (ChargeDiscountConfigQueryModel model : page.getContent()) {
            ChargeDiscountConfigDto dto = chargeDiscountConfigMapper.toDto(model);
            if (dto != null) {
                rows.add(dto);
            }
        }
        this.enrichConfigRows(rows);
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 新增收费折扣配置（IF-CDC-03）：大类行或单项行，可选折扣率。
     *
     * @param request 创建请求
     * @return 新行主键
     */
    public CreateChargeDiscountConfigResponse createChargeDiscountConfig(CreateChargeDiscountConfigRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "新增失败：请求体不能为空");
        }
        // Gate：互斥行形态、费率区间、表名白名单
        this.assertCreateOrModifyShape(request.getProductTable(), request.getCategoryCode(),
            request.getProductId(), request.getDiscountRate(), "新增");
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "请先登录后再新增收费折扣配置");
        // Pre：目录大类或产品定义须存在，防止悬空引用
        this.assertCatalogExists(request.getProductTable(), request.getCategoryCode(), request.getProductId());
        // Cmd：领域创建（唯一键与不变式二次校验）
        Long id = chargeDiscountConfigDomainService.createChargeDiscountConfig(
            chargeDiscountConfigMapper.toCreateCommand(request, operatorId));
        log.info("Created charge discount config: {}", id);
        // Asm：仅回传主键
        CreateChargeDiscountConfigResponse response = new CreateChargeDiscountConfigResponse();
        response.setId(id);
        return response;
    }

    /**
     * 修改收费折扣配置（IF-CDC-04）：命中键与折扣率全量覆盖。
     *
     * @param request 修改请求（含 id）
     */
    public void modifyChargeDiscountConfig(ModifyChargeDiscountConfigRequest request) {
        // Gate：主键、互斥行形态与费率
        if (request == null || request.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "修改失败：配置主键不能为空");
        }
        this.assertCreateOrModifyShape(request.getProductTable(), request.getCategoryCode(),
            request.getProductId(), request.getDiscountRate(), "修改");
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "请先登录后再修改收费折扣配置");
        // Pre：行须存在；目录键须可解析
        chargeDiscountConfigQueryService.getChargeDiscountConfig(request.getId());
        this.assertCatalogExists(request.getProductTable(), request.getCategoryCode(), request.getProductId());
        // Cmd：领域修改并发布变更事件
        chargeDiscountConfigDomainService.modifyChargeDiscountConfig(
            chargeDiscountConfigMapper.toModifyCommand(request, operatorId));
        log.info("Modified charge discount config: {}", request.getId());
    }

    /**
     * 启停收费折扣配置（IF-CDC-05），影响 ACTIVE 配置计数（从而决定折扣相关显隐）。
     *
     * @param request 含 id 与目标 statusEnum
     */
    public void changeChargeDiscountConfigStatus(ChangeChargeDiscountConfigStatusRequest request) {
        // Gate：主键与目标状态非空
        if (request == null || request.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "启停失败：配置主键不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_STATUS,
                "启停失败：目标状态不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "请先登录后再启停收费折扣配置");
        // Cmd：领域启停（校验 ACTIVE/INACTIVE）
        ChangeChargeDiscountConfigStatusCommand command = new ChangeChargeDiscountConfigStatusCommand();
        command.setId(request.getId());
        command.setStatusEnum(request.getStatusEnum().trim());
        command.setOperatorId(operatorId);
        chargeDiscountConfigDomainService.changeChargeDiscountConfigStatus(command);
        log.info("Changed charge discount config status: id={}, status={}",
            request.getId(), request.getStatusEnum());
    }

    /**
     * 软删收费折扣配置（IF-CDC-06）。
     *
     * @param request 含 id
     */
    public void deleteChargeDiscountConfig(DeleteChargeDiscountConfigRequest request) {
        // Gate：主键非空与登录操作人
        if (request == null || request.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "删除失败：配置主键不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "请先登录后再删除收费折扣配置");
        // Cmd：领域软删并发布变更事件
        DeleteChargeDiscountConfigCommand command = new DeleteChargeDiscountConfigCommand();
        command.setId(request.getId());
        command.setOperatorId(operatorId);
        chargeDiscountConfigDomainService.deleteChargeDiscountConfig(command);
        log.info("Deleted charge discount config: {}", request.getId());
    }

    /**
     * 检索可选大类或目录项目（IF-CDC-07），供配置页选键。
     *
     * @param request 含 productTable、searchMode、keyword、分页
     * @return 选项分页
     */
    public PagedResult<DiscountCatalogOptionDto> searchDiscountCatalogOptions(
        SearchDiscountCatalogOptionsRequest request
    ) {
        // Gate：分页、表名、检索模式
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "选品检索失败：pageNo/pageSize 须为正整数");
        if (request == null || StringUtils.isEmpty(request.getProductTable())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "选品检索失败：产品表名不能为空");
        }
        String productTable = request.getProductTable().trim();
        this.assertAllowedProductTable(productTable);
        String searchMode = trimToNull(request.getSearchMode());
        if (searchMode == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "选品检索失败：searchMode 不能为空");
        }
        // Pre：按模式委托术语大类或三大目录分页
        if (ChargeDiscountConfigConstants.SEARCH_MODE_CATEGORY.equals(searchMode)) {
            return this.searchCategoryOptions(productTable, trimToNull(request.getKeyword()),
                request.getPageNo(), request.getPageSize());
        }
        if (ChargeDiscountConfigConstants.SEARCH_MODE_ITEM.equals(searchMode)) {
            return this.searchItemOptions(productTable, trimToNull(request.getKeyword()),
                request.getPageNo(), request.getPageSize());
        }
        // Gate：未知模式拒绝，避免误查全库
        throw new BusinessException(
            ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
            "选品检索失败：searchMode 仅允许 CATEGORY 或 ITEM");
    }

    /**
     * 创建/修改共用：行形态互斥、表名白名单、折扣率区间（对齐 BR-CDC-001～003 / EX-CDC-001～002/004）。
     *
     * @param productTable  产品表
     * @param categoryCode  大类码
     * @param productId     产品 id
     * @param discountRate  折扣率
     * @param actionLabel   动作文案（新增/修改）
     */
    private void assertCreateOrModifyShape(
        String productTable,
        String categoryCode,
        Long productId,
        BigDecimal discountRate,
        String actionLabel
    ) {
        this.assertAllowedProductTable(productTable);
        boolean hasCategory = !StringUtils.isEmpty(categoryCode);
        boolean hasProduct = productId != null;
        // 同填或同空均非法（BR-CDC-001）
        if (hasCategory == hasProduct) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                actionLabel + "失败：大类编码与产品主键须恰好其一非空");
        }
        // 有值须 (0,1]；空=款员定率（BR-CDC-003）
        if (discountRate != null) {
            if (discountRate.compareTo(BigDecimal.ZERO) <= 0
                || discountRate.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                    actionLabel + "失败：折扣率须为空或属于 (0,1]");
            }
        }
    }

    /**
     * 断言产品表属于三大目录白名单（BR-CDC-002）。
     *
     * @param productTable 产品表名
     */
    private void assertAllowedProductTable(String productTable) {
        if (StringUtils.isEmpty(productTable)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "产品表名不能为空");
        }
        if (!ChargeDiscountConfigConstants.ALLOWED_PRODUCT_TABLES.contains(productTable.trim())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "产品表名必须是药品/诊疗/耗材三大目录之一");
        }
    }

    /**
     * 目录存在性：大类码须在对应术语体系；单项须能加载产品定义（EX-CDC-005）。
     *
     * @param productTable 产品表
     * @param categoryCode 大类码（大类行）
     * @param productId    产品 id（单项行）
     */
    private void assertCatalogExists(String productTable, String categoryCode, Long productId) {
        String table = productTable.trim();
        // 大类行：术语码须存在
        if (!StringUtils.isEmpty(categoryCode)) {
            Map<String, String> labelMap = this.categoryLabelMapForTable(table);
            if (!labelMap.containsKey(categoryCode.trim())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_CATALOG_NOT_FOUND,
                    "大类编码不存在或不属于当前目录");
            }
            return;
        }
        // 单项行：产品定义须存在
        if (productId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "产品主键不能为空");
        }
        boolean found = this.productExists(table, productId);
        if (!found) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_CATALOG_NOT_FOUND,
                "目录项目不存在或已删除");
        }
    }

    /**
     * 按表判断产品定义是否存在。
     *
     * @param productTable 产品表
     * @param productId    产品主键
     * @return true 表示存在
     */
    private boolean productExists(String productTable, Long productId) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            Optional<MedicationDefinition> med =
                medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(productId);
            return med.isPresent();
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            Optional<ActivityDefinition> act =
                activityDefinitionQueryService.findOptionalActivityDefinitionEntityById(productId);
            return act.isPresent();
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(productId);
            return device != null;
        }
        return false;
    }

    /**
     * 为列表行补产品表名、大类名、产品名与状态文案。
     *
     * @param rows 已投影的 DTO 列表
     */
    private void enrichConfigRows(List<ChargeDiscountConfigDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, String> medCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        Map<String, String> actCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        Map<String, String> deviceCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);

        Set<Long> medIds = new HashSet<>();
        Set<Long> actIds = new HashSet<>();
        Set<Long> deviceIds = new HashSet<>();
        for (ChargeDiscountConfigDto row : rows) {
            if (row.getProductId() == null || StringUtils.isEmpty(row.getProductTable())) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getProductTable())) {
                medIds.add(row.getProductId());
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTable())) {
                actIds.add(row.getProductId());
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTable())) {
                deviceIds.add(row.getProductId());
            }
        }
        Map<Long, String> medNames = this.loadMedicationNames(medIds);
        Map<Long, String> actNames = this.loadActivityNames(actIds);
        Map<Long, String> deviceNames = this.loadDeviceNames(deviceIds);

        for (ChargeDiscountConfigDto row : rows) {
            row.setProductTableName(this.resolveProductTableName(row.getProductTable()));
            // 大类名：按表取对应术语映射
            if (!StringUtils.isEmpty(row.getCategoryCode())) {
                Map<String, String> labels = this.pickCategoryLabels(
                    row.getProductTable(), medCategoryLabels, actCategoryLabels, deviceCategoryLabels);
                row.setCategoryName(labels.get(row.getCategoryCode()));
            }
            // 产品名：按表从批量 Map 取
            if (row.getProductId() != null) {
                if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getProductTable())) {
                    row.setProductName(medNames.get(row.getProductId()));
                } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTable())) {
                    row.setProductName(actNames.get(row.getProductId()));
                } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTable())) {
                    row.setProductName(deviceNames.get(row.getProductId()));
                }
            }
            // 状态展示：枚举 display
            ChargeDiscountConfigStatus status =
                EnumUtil.fromCode(ChargeDiscountConfigStatus.class, row.getStatusEnum());
            if (status != null) {
                row.setStatusEnumText(status.getDisplay());
            }
        }
    }

    /**
     * 按产品表挑选大类术语映射。
     *
     * @param productTable         产品表
     * @param medCategoryLabels    药品分类
     * @param actCategoryLabels    诊疗分类
     * @param deviceCategoryLabels 耗材分类
     * @return 对应映射；未知表返回空 Map
     */
    private Map<String, String> pickCategoryLabels(
        String productTable,
        Map<String, String> medCategoryLabels,
        Map<String, String> actCategoryLabels,
        Map<String, String> deviceCategoryLabels
    ) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return medCategoryLabels;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return actCategoryLabels;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return deviceCategoryLabels;
        }
        return Map.of();
    }

    /**
     * 按产品表加载大类术语码→名映射（Gate 与检索共用）。
     *
     * @param productTable 产品表
     * @return 映射
     */
    private Map<String, String> categoryLabelMapForTable(String productTable) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        }
        return Map.of();
    }

    /**
     * 批量加载药品名称。
     *
     * @param ids 药品主键集合
     * @return id→name
     */
    private Map<Long, String> loadMedicationNames(Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<MedicationDefinition> list = medicationDefinitionQueryService.findAllByIds(ids);
        for (MedicationDefinition item : list) {
            if (item != null && item.getId() != null) {
                result.put(item.getId(), item.getName());
            }
        }
        return result;
    }

    /**
     * 批量加载诊疗项目名称。
     *
     * @param ids 诊疗主键集合
     * @return id→name
     */
    private Map<Long, String> loadActivityNames(Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<ActivityDefinition> list = activityDefinitionQueryService.findAllByIds(ids);
        for (ActivityDefinition item : list) {
            if (item != null && item.getId() != null) {
                result.put(item.getId(), item.getName());
            }
        }
        return result;
    }

    /**
     * 批量加载耗材名称。
     *
     * @param ids 耗材主键集合
     * @return id→name
     */
    private Map<Long, String> loadDeviceNames(Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<DeviceDefinition> list = deviceDefinitionQueryService.getAllDeviceDefinitions(ids);
        for (DeviceDefinition item : list) {
            if (item != null && item.getId() != null) {
                result.put(item.getId(), item.getName());
            }
        }
        return result;
    }

    /**
     * 三大目录表名下拉。
     *
     * @return OptionDto 列表
     */
    private List<OptionDto> buildProductTableOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        options.add(new OptionDto(2L, TableNameConstants.WKF_ACTIVITY_DEFINITION, "诊疗"));
        options.add(new OptionDto(3L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return options;
    }

    /**
     * 产品表 → 中文名。
     *
     * @param productTable 表名
     * @return 展示名；未知时回落表名本身
     */
    private String resolveProductTableName(String productTable) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return "药品";
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return "诊疗";
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return "耗材";
        }
        return productTable;
    }

    /**
     * 大类模式检索：仅返回本院基础目录中确有挂项的大类（术语 ∩ 目录统计）。
     *
     * @param productTable 产品表
     * @param keyword      关键字
     * @param pageNo       1-based 页码
     * @param pageSize     页大小
     * @return 选项分页（含 itemCount）
     */
    private PagedResult<DiscountCatalogOptionDto> searchCategoryOptions(
        String productTable,
        String keyword,
        Integer pageNo,
        Integer pageSize
    ) {
        // Pre：本院目录统计各 category_code 挂项数（空大类不参与）
        Map<String, Integer> usedCategoryCounts = this.loadUsedCategoryItemCounts(productTable);
        List<OptionDto> all = this.loadCategoryOptions(productTable);
        List<DiscountCatalogOptionDto> filtered = new ArrayList<>();
        for (OptionDto option : all) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            String code = option.getValue().trim();
            Integer itemCount = usedCategoryCounts.get(code);
            // 本院目录无挂项的术语大类直接丢弃
            if (itemCount == null || itemCount.intValue() <= 0) {
                continue;
            }
            // 关键字同时匹配码与展示名
            if (keyword != null) {
                boolean codeHit = code.contains(keyword);
                boolean labelHit = option.getLabel() != null && option.getLabel().contains(keyword);
                if (!codeHit && !labelHit) {
                    continue;
                }
            }
            DiscountCatalogOptionDto row = new DiscountCatalogOptionDto();
            row.setValue(code);
            row.setLabel(option.getLabel());
            row.setItemCount(itemCount);
            filtered.add(row);
        }
        // Asm：全量过滤后按页切片（本院在用大类量可控）
        return PagedResultBuilder.buildFromFullList(new PagedResult<>(), filtered, pageNo, pageSize);
    }

    /**
     * 扫描三大目录分页，统计本院已挂项的 {@code category_code → 项目数}。
     *
     * @param productTable 产品表名
     * @return 大类码 → 挂项数；无挂项时为空 Map
     */
    private Map<String, Integer> loadUsedCategoryItemCounts(String productTable) {
        Map<String, Integer> counts = new HashMap<>();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            this.accumulateMedicationCategoryCounts(counts);
            return counts;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            this.accumulateActivityCategoryCounts(counts);
            return counts;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            this.accumulateDeviceCategoryCounts(counts);
            return counts;
        }
        return counts;
    }

    /**
     * 药品目录分页累计大类挂项数。
     *
     * @param counts 累计容器
     */
    private void accumulateMedicationCategoryCounts(Map<String, Integer> counts) {
        int pageNo = 1;
        int pageSize = 200;
        int maxPages = 50;
        long total = Long.MAX_VALUE;
        while (pageNo <= maxPages && (long) (pageNo - 1) * pageSize < total) {
            MedicationDefinitionCatalogPageQuery query = new MedicationDefinitionCatalogPageQuery();
            query.setPageNo(pageNo);
            query.setPageSize(pageSize);
            Page<MedicationDefinitionPageQueryModel> page = medicationDefinitionQueryService.pageMedicationCatalog(
                query, PageRequest.of(query.springPageIndex(), query.getBoundedPageSize()));
            total = page.getTotalElements();
            for (MedicationDefinitionPageQueryModel item : page.getContent()) {
                if (item == null || StringUtils.isEmpty(item.getCategoryCode())) {
                    continue;
                }
                this.bumpCategoryCount(counts, item.getCategoryCode().trim());
            }
            if (page.getContent().isEmpty()) {
                break;
            }
            pageNo++;
        }
    }

    /**
     * 诊疗目录分页累计大类挂项数（与目录维护同口径，含套餐子项）。
     *
     * @param counts 累计容器
     */
    private void accumulateActivityCategoryCounts(Map<String, Integer> counts) {
        int pageNo = 1;
        int pageSize = 200;
        int maxPages = 50;
        long total = Long.MAX_VALUE;
        while (pageNo <= maxPages && (long) (pageNo - 1) * pageSize < total) {
            ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
            query.setPageNo(pageNo);
            query.setPageSize(pageSize);
            query.setIncludeReferencedPackageChildren(Boolean.TRUE);
            Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
                query, PageRequest.of(query.springPageIndex(), query.getBoundedPageSize()));
            total = page.getTotalElements();
            for (ActivityDefinitionPageQueryModel item : page.getContent()) {
                if (item == null || StringUtils.isEmpty(item.getCategoryCode())) {
                    continue;
                }
                this.bumpCategoryCount(counts, item.getCategoryCode().trim());
            }
            if (page.getContent().isEmpty()) {
                break;
            }
            pageNo++;
        }
    }

    /**
     * 耗材目录分页累计大类挂项数。
     *
     * @param counts 累计容器
     */
    private void accumulateDeviceCategoryCounts(Map<String, Integer> counts) {
        int pageNo = 1;
        int pageSize = 200;
        int maxPages = 50;
        long total = Long.MAX_VALUE;
        while (pageNo <= maxPages && (long) (pageNo - 1) * pageSize < total) {
            int springIndex = pageNo - 1;
            Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
                springIndex, pageSize, null, null, null, null);
            total = page.getTotalElements();
            for (DeviceDefinition item : page.getContent()) {
                if (item == null || StringUtils.isEmpty(item.getCategoryCode())) {
                    continue;
                }
                this.bumpCategoryCount(counts, item.getCategoryCode().trim());
            }
            if (page.getContent().isEmpty()) {
                break;
            }
            pageNo++;
        }
    }

    /**
     * 大类挂项计数 +1。
     *
     * @param counts 累计容器
     * @param categoryCode 大类码
     */
    private void bumpCategoryCount(Map<String, Integer> counts, String categoryCode) {
        Integer prev = counts.get(categoryCode);
        if (prev == null) {
            counts.put(categoryCode, Integer.valueOf(1));
            return;
        }
        counts.put(categoryCode, Integer.valueOf(prev.intValue() + 1));
    }

    /**
     * 按产品表加载完整大类术语 Option 列表。
     *
     * @param productTable 产品表
     * @return 术语选项
     */
    private List<OptionDto> loadCategoryOptions(String productTable) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.loadOptions(
                TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.loadOptions(
                TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return terminologyOptionLoader.loadOptions(
                TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        }
        return List.of();
    }

    /**
     * 单项模式检索：委托对应目录分页 Query。
     *
     * @param productTable 产品表
     * @param keyword      关键字
     * @param pageNo       1-based 页码
     * @param pageSize     页大小
     * @return 选项分页
     */
    private PagedResult<DiscountCatalogOptionDto> searchItemOptions(
        String productTable,
        String keyword,
        Integer pageNo,
        Integer pageSize
    ) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return this.searchMedicationItems(keyword, pageNo, pageSize);
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return this.searchActivityItems(keyword, pageNo, pageSize);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return this.searchDeviceItems(keyword, pageNo, pageSize);
        }
        return PagedResultBuilder.buildEmpty(new PagedResult<>());
    }

    /**
     * 药品目录分页投影为选品 Option。
     *
     * @param keyword  关键字
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 选项分页
     */
    private PagedResult<DiscountCatalogOptionDto> searchMedicationItems(
        String keyword,
        Integer pageNo,
        Integer pageSize
    ) {
        MedicationDefinitionCatalogPageQuery query = new MedicationDefinitionCatalogPageQuery();
        query.setKeyword(keyword);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<MedicationDefinitionPageQueryModel> page = medicationDefinitionQueryService.pageMedicationCatalog(
            query, PageRequest.of(query.springPageIndex(), query.getBoundedPageSize()));
        List<DiscountCatalogOptionDto> rows = new ArrayList<>();
        for (MedicationDefinitionPageQueryModel item : page.getContent()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DiscountCatalogOptionDto row = new DiscountCatalogOptionDto();
            row.setId(item.getId());
            row.setValue(String.valueOf(item.getId()));
            row.setBusNo(item.getBusNo());
            row.setLabel(this.buildItemLabel(item.getBusNo(), item.getName()));
            rows.add(row);
        }
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 诊疗目录分页投影为选品 Option。
     *
     * @param keyword  关键字
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 选项分页
     */
    private PagedResult<DiscountCatalogOptionDto> searchActivityItems(
        String keyword,
        Integer pageNo,
        Integer pageSize
    ) {
        ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
        query.setKeyword(keyword);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
            query, PageRequest.of(query.springPageIndex(), query.getBoundedPageSize()));
        List<DiscountCatalogOptionDto> rows = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel item : page.getContent()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DiscountCatalogOptionDto row = new DiscountCatalogOptionDto();
            row.setId(item.getId());
            row.setValue(String.valueOf(item.getId()));
            row.setBusNo(item.getBusNo());
            row.setLabel(this.buildItemLabel(item.getBusNo(), item.getName()));
            rows.add(row);
        }
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 耗材目录分页投影为选品 Option。
     *
     * @param keyword  关键字
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 选项分页
     */
    private PagedResult<DiscountCatalogOptionDto> searchDeviceItems(
        String keyword,
        Integer pageNo,
        Integer pageSize
    ) {
        int springIndex = 0;
        if (pageNo != null && pageNo > 0) {
            springIndex = pageNo - 1;
        }
        int size = 10;
        if (pageSize != null && pageSize > 0) {
            size = pageSize;
        }
        Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
            springIndex, size, keyword, null, null, null);
        List<DiscountCatalogOptionDto> rows = new ArrayList<>();
        for (DeviceDefinition item : page.getContent()) {
            if (item == null || item.getId() == null) {
                continue;
            }
            DiscountCatalogOptionDto row = new DiscountCatalogOptionDto();
            row.setId(item.getId());
            row.setValue(String.valueOf(item.getId()));
            row.setBusNo(item.getBusNo());
            row.setLabel(this.buildItemLabel(item.getBusNo(), item.getName()));
            rows.add(row);
        }
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 组装选品展示文案：有 busNo 时前缀拼接。
     *
     * @param busNo 业务编码
     * @param name  名称
     * @return 展示文案
     */
    private String buildItemLabel(String busNo, String name) {
        if (StringUtils.isEmpty(busNo)) {
            return name;
        }
        if (StringUtils.isEmpty(name)) {
            return busNo;
        }
        return busNo + " " + name;
    }
}
