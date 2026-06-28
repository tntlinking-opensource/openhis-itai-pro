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

package com.openhis.health.application.basic.devicecatalog.service;

import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.domain.share.constants.DeviceCatalogUiConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.module.common.assembler.pricing.ChargePricingCommandAssembler;
import com.openhis.health.module.common.assembler.option.OptionDtoAssembler;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogDto;
import com.openhis.health.application.basic.devicecatalog.dto.DeviceCatalogInitDto;
import com.openhis.health.application.basic.devicecatalog.dto.PageDeviceCatalogResponse;
import com.openhis.health.application.basic.devicecatalog.mapper.DeviceCatalogMapper;
import com.openhis.health.application.basic.devicecatalog.request.CreateDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.PageDeviceCatalogRequest;
import com.openhis.health.application.basic.devicecatalog.request.UpdateDeviceCatalogRequest;
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
import com.openhis.health.domain.share.utils.OptionDtoLegacyRowUtil;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 耗材（器材）目录管理应用服务：分页列表、详情、增删改、批量启停与收费定价行维护。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>网关字符串 ID → long、批量 ID 合法性：委托 {@link GatewayIdParseUtil}；</li>
 *   <li>定价金额按元写入 {@code adm_charge_item_definition}；批量写入定价行：{@link ChargePricingCommandAssembler}；</li>
 *   <li>领域持久化与状态迁移：{@link DeviceDefinitionDomainService}；定价明细：{@link ChargeItemDefinitionDomainService}。</li>
 * </ul>
 *
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

    /**
     * 耗材维护页初始化：单位类下拉、供应商列表、耗材分类（{@code device_category_code}）、耗材种类（{@code device_type_code}）、财务类型（{@code fin_type_code}）等。
     *
     * <p>返回结构中部分列表为 {@link OptionDtoLegacyRowUtil} 映射的 label/value 行，兼容旧前端表格数据源。</p>
     *
     * @return 各计量维度下拉数据聚合（若术语缺失则对应列为空列表）
     */
    public DeviceCatalogInitDto init() {
        DeviceCatalogInitDto dto = DeviceCatalogInitDto.empty();

        // 五类计量单位与药品目录一致：统一术语 unit_code，避免采购/拆零/剂量各用不同码表导致 ug、片 等错码。
        List<Map<String, Object>> unitOptions =
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.UNIT_CODE);
        dto.setUnitCodeOptions(unitOptions);
        dto.setPackUnits(unitOptions);
        dto.setSaleUnits(unitOptions);
        dto.setMinUnits(unitOptions);
        dto.setStockUnits(unitOptions);
        dto.setDoseUnits(unitOptions);

        dto.setSupplierListOptions(loadSupplierLegacyRows());
        dto.setDeviceCategories(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        dto.setEquipmentTypes(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE));
        dto.setFinancialTypes(
            terminologyOptionLoader.loadLegacyRows(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE));

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
     * 分页查询耗材目录。
     *
     * <p>页码与 Spring Data 约定对齐：请求体使用 1-based 页号，领域查询接口使用 0-based page index。</p>
     *
     * @param request 分页参数（可为 {@code null}，此时使用默认页码与页大小）
     * @return 当前页内容与总条数、总页数
     */
    public PageDeviceCatalogResponse pageDeviceCatalog(PageDeviceCatalogRequest request) {
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
        // 读侧分页：领域返回 Spring Page，应用层再补齐列表行的定价与字典文本。
        String keyword = null;
        String categoryCode = null;
        String typeCode = null;
        if (request != null) {
            keyword = request.getKeyword();
            categoryCode = request.getCategoryCode();
            typeCode = request.getTypeCode();
        }
        Page<DeviceDefinition> page = deviceDefinitionQueryService.pageDeviceDefinition(
            pageNo - 1, pageSize, keyword, categoryCode, typeCode);
        List<DeviceCatalogDto> content = page.getContent().stream()
            .filter(Objects::nonNull)
            .map(deviceCatalogMapper::toDto)
            .collect(Collectors.toList());
        this.enrichPageFields(content);

        // —— B.10.11：使用 PagedResultBuilder 构建分页响应（传入处理后的 content）。——
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
     * @return 设备目录 DTO（含采购价、零售价、限价等回填字段）
     * @throws ValidationException {@code id} 为空或非法
     * @throws EntityNotFoundException 设备不存在或已删除
     */
    public DeviceCatalogDto getDeviceCatalog(String id) {
        long deviceId = GatewayIdParseUtil.parseRequiredLong(id, "查询耗材详情失败：id 不能为空");
        DeviceDefinition entity = deviceDefinitionQueryService.getDeviceDefinition(Long.valueOf(deviceId));
        if (entity == null) {
            throw new EntityNotFoundException("耗材不存在或已删除");
        }
        DeviceCatalogDto dto = deviceCatalogMapper.toDto(entity);
        this.enrichPageFields(List.of(dto));
        dto.setEditabilityCode(DeviceCatalogUiConstants.EDITABILITY_OK);
        return dto;
    }

    /**
     * 新增耗材目录。
     *
     * @param request 创建请求（名称、单位等必填项见体内校验）
     * @return 新建设备的详情（含定价行）
     * @throws ValidationException 请求或必填字段不合法
     */
    
    public DeviceCatalogDto createDeviceCatalog(CreateDeviceCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("创建耗材失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：耗材名称不能为空");
        }
        if (request.getCategoryCode() == null || request.getCategoryCode().trim().isEmpty()) {
            throw new ValidationException("创建耗材失败：耗材分类不能为空");
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
        if (request.getPurchasePrice() == null) {
            throw new ValidationException("创建耗材失败：购入价不能为空");
        }
        if (request.getRetailPrice() == null) {
            throw new ValidationException("创建耗材失败：零售价不能为空");
        }
        if (StringUtils.isBlank(request.getItemTypeCode())) {
            throw new ValidationException("创建耗材失败：财务类型不能为空");
        }

        this.normalizeManufacturerSupplyForWrite(request);
        // 先落主数据，再原子替换实例维度下的收费定价行（与药品目录模式一致）。
        CreateDeviceDefinitionCommand cmd = deviceCatalogMapper.toCreateCommand(request);
        Long createdId = deviceDefinitionDomainService.createDeviceDefinition(cmd);
        this.replacePriceLines(
            createdId,
            request.getItemTypeCode(),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        return this.getDeviceCatalog(String.valueOf(createdId));
    }

    /**
     * 编辑耗材目录。
     *
     * @param request 更新请求（须含字符串 {@code id} 及名称等）
     * @return 最新详情（含定价行）
     * @throws ValidationException 参数不合法
     */
    
    public DeviceCatalogDto editDeviceCatalog(UpdateDeviceCatalogRequest request) {
        if (request == null || request.getId() == null || request.getId().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：耗材名称不能为空");
        }
        if (request.getCategoryCode() == null || request.getCategoryCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：耗材分类不能为空");
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
        if (request.getPurchasePrice() == null) {
            throw new ValidationException("更新耗材失败：购入价不能为空");
        }
        if (request.getRetailPrice() == null) {
            throw new ValidationException("更新耗材失败：零售价不能为空");
        }
        if (request.getEditabilityCode() == null || request.getEditabilityCode().trim().isEmpty()) {
            throw new ValidationException("更新耗材失败：editabilityCode 不能为空");
        }
        if (StringUtils.isBlank(request.getItemTypeCode())) {
            throw new ValidationException("更新耗材失败：财务类型不能为空");
        }
        long id = GatewayIdParseUtil.parseRequiredLong(request.getId(), "更新耗材失败：id 不能为空");

        this.normalizeManufacturerSupplyForWrite(request);
        UpdateDeviceDefinitionCommand cmd = deviceCatalogMapper.toUpdateCommand(request);
        cmd.getItems().get(0).setEditabilityCode(request.getEditabilityCode().trim());
        // 主数据更新与定价行替换分属两个聚合职责，顺序为先领域更新设备再刷新定价。
        deviceDefinitionDomainService.updateDeviceDefinition(cmd);
        this.replacePriceLines(
            id,
            request.getItemTypeCode(),
            request.getYbType(),
            request.getPurchasePrice(),
            request.getRetailPrice(),
            request.getMaximumRetailPrice());
        return this.getDeviceCatalog(String.valueOf(id));
    }

    /**
     * 批量启用耗材目录。
     *
     * @param ids 设备主键字符串列表（非空且均可解析为 long）
     * @throws ValidationException {@code ids} 为空或存在非法项
     */
    public void startDeviceCatalog(List<String> ids) {
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "启用耗材目录失败：ids 不能为空",
            "启用耗材目录失败：ids 存在非法值");
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
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "停用耗材目录失败：ids 不能为空",
            "停用耗材目录失败：ids 存在非法值");
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
        GatewayIdParseUtil.requireNonEmptyParsableLongStrings(ids,
            "删除耗材目录失败：ids 不能为空",
            "删除耗材目录失败：ids 存在非法值");
        DeleteDeviceDefinitionsCommand cmd = new DeleteDeviceDefinitionsCommand();
        cmd.setItems(deviceCatalogMapper.toDeleteItems(ids));
        deviceDefinitionDomainService.deleteDeviceDefinitions(cmd);
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
                    String mt = dto.getManufacturerText();
                    if (mt == null || mt.isBlank()
                        || mt.chars().allMatch(Character::isDigit)
                        || mt.equals(String.valueOf(dto.getSupplyId()))) {
                        dto.setManufacturerText(supName);
                    }
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
     * 写入前收敛生产厂家与供应商：避免 manufacturer_id 空而 supply_id 有值、或 manufacturer_text 误存为 id 串。
     *
     * @param request 创建或更新请求（就地修改）
     */
    private void normalizeManufacturerSupplyForWrite(CreateDeviceCatalogRequest request) {
        if (request == null) {
            return;
        }
        Long supplyId = request.getSupplyId();
        Long manufacturerId = request.getManufacturerId();
        if (supplyId != null && manufacturerId == null) {
            request.setManufacturerId(supplyId);
            manufacturerId = supplyId;
        }
        String mt = request.getManufacturerText();
        if (mt != null && !mt.isBlank()) {
            String trimmed = mt.trim();
            if (trimmed.chars().allMatch(Character::isDigit)) {
                boolean matchesSupply = supplyId != null && trimmed.equals(String.valueOf(supplyId));
                boolean matchesMfg = manufacturerId != null && trimmed.equals(String.valueOf(manufacturerId));
                if (matchesSupply || matchesMfg) {
                    Long resolveId;
                    if (manufacturerId != null) {
                        resolveId = manufacturerId;
                    } else {
                        resolveId = supplyId;
                    }
                    request.setManufacturerText(
                        supplierQueryService.findSupplierNameIfPresent(resolveId).orElse(null));
                }
            }
        }
        if ((request.getManufacturerText() == null || request.getManufacturerText().isBlank())
            && request.getManufacturerId() != null) {
            request.setManufacturerText(
                supplierQueryService.findSupplierNameIfPresent(request.getManufacturerId()).orElse(null));
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
        // 按价格类型码分支回填：采购、限价、零售（单价）互不覆盖错误字段。
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
     * @param purchasePrice     采购价（必填，每包装单位）
     * @param retailPrice       零售价（必填，每包装单位）
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
