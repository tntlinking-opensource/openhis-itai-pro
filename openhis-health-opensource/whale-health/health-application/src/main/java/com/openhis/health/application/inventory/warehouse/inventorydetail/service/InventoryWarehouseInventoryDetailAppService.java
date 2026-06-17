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

package com.openhis.health.application.inventory.warehouse.inventorydetail.service;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.mapper.InventoryWarehouseInventoryDetailMapper;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.LoadInventoryDetailFormInitRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ResumeInventoryItemsRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.RetireInventoryItemsRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ExportInventoryDetailCurrentResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.LoadInventoryDetailFormInitResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.PageInventoryDetailCurrentResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ResumeInventoryItemsResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.RetireInventoryItemsResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.SumInventoryDetailCurrentResponse;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.inventory.InventoryCatalogFilterSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.ResumeSupplyCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.StopSupplyCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * 药库现库明细：分页查询、汇总、导出；写侧仅支持停供/恢复供应（改 {@code inventory_status_enum}）。
 *
 * <p>金额展示结合批号维进价/售价条件行；管辖库位为空时不拼 location 谓词（可见全租户库房数据需结合门控）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryWarehouseInventoryDetailAppService {

    private final CurrentOperatorResolver currentOperatorResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final LocationQueryService locationQueryService;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryWarehouseInventoryDetailMapper inventoryWarehouseInventoryDetailMapper;
    private final InventoryCatalogFilterSupport inventoryCatalogFilterSupport;

    /**
     * 现库明细筛选项初始化：医保等级、库存发布状态、药品/耗材分类术语下拉。
     *
     * @param request 可空壳请求
     * @return 初始化响应
     * @throws BusinessException {@code WHS_DTL_201} 术语加载失败
     */
    public LoadInventoryDetailFormInitResponse loadInventoryDetailFormInit(@Valid LoadInventoryDetailFormInitRequest request) {
        // Gate：登录上下文
        this.assertGateUserPresent();
        LoadInventoryDetailFormInitResponse response = new LoadInventoryDetailFormInitResponse();
        try {
            // Pre：加载医保等级术语下拉
            List<OptionDto> chrgitm = terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.CHRGITM_LV);
            response.setChrgitmLvOptions(chrgitm);
        } catch (RuntimeException ex) {
            log.warn("库存明细初始化：医保等级术语加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_201, "筛选项加载失败");
        }
        try {
            List<OptionDto> allPub = terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.PUBLICATION_STATUS);
            List<OptionDto> filteredPub = new ArrayList<>();
            for (OptionDto option : allPub) {
                if (option == null) {
                    continue;
                }
                // 库存状态筛选仅暴露 ACTIVE/INACTIVE 两态。
                if (PublicationStatus.ACTIVE.getCode().equals(option.getValue())
                    || PublicationStatus.INACTIVE.getCode().equals(option.getValue())) {
                    filteredPub.add(option);
                }
            }
            response.setInventoryStatusOptions(filteredPub);
        } catch (RuntimeException ex) {
            log.warn("库存明细初始化：发布状态术语加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_201, "筛选项加载失败");
        }
        try {
            response.setMedicationCategoryOptions(
                terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE));
            response.setDeviceCategoryOptions(
                terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE));
        } catch (RuntimeException ex) {
            log.warn("库存明细初始化：药品/耗材分类术语加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_201, "筛选项加载失败");
        }
        // Asm：映射页初始化响应
        return response;
    }

    /**
     * 现库明细分页：管辖库房收窄 → 读侧分页 → 批量装配产品/定价/行总额。
     *
     * @param request 筛选与分页
     * @return 分页列表
     * @throws BusinessException {@code WHS_DTL_202} 列表加载失败
     */
    public PageInventoryDetailCurrentResponse pageInventoryDetailCurrent(@Valid PageInventoryDetailCurrentRequest request) {
        // Gate：登录上下文
        this.assertGateUserPresent();
        // Pre：管辖库房收窄并组装分页查询
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        WarehouseInventoryDetailPageQuery query =
            this.inventoryWarehouseInventoryDetailMapper.toWarehouseDetailPageQuery(request, effectiveWarehouseIds);
        this.inventoryCatalogFilterSupport.enrichResolvedDefinitionIds(query);
        // 默认按 py_str 升序（NULLS LAST）
        Sort sort = Sort.by(Sort.Order.asc(FieldNameConstants.PY_STR));
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize(), sort);
        Page<WarehouseInventoryDetailItemModel> page;
        try {
            // Cmd：分页查询现库明细
            page = this.inventoryWarehouseInventoryDetailMapper.selectCurrentDetailPage(query, pageable);
        } catch (RuntimeException ex) {
            log.warn("现库明细分页查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_202, "列表加载失败", ex);
        }
        List<InventoryDetailListRowDto> rows = new ArrayList<>();
        for (WarehouseInventoryDetailItemModel model : page.getContent()) {
            InventoryDetailListRowDto dto = this.inventoryWarehouseInventoryDetailMapper.toListRowDto(model);
            rows.add(dto);
        }
        this.enrichRows(rows);
        // Asm：装配分页响应
        return PagedResultBuilder.build(
            new PageInventoryDetailCurrentResponse(),
            rows,
            page.getTotalElements(),
            request.getPageNo(),
            request.getBoundedPageSize()
        );
    }

    /**
     * 底部汇总：在相同筛选下累加行成本/售价总额；超上限时 {@code truncated=true}。
     *
     * @param request 与列表同构筛选
     * @return 汇总金额与截断标记
     * @throws BusinessException {@code WHS_DTL_203} 汇总加载失败
     */
    public SumInventoryDetailCurrentResponse sumInventoryDetailCurrent(@Valid SumInventoryDetailCurrentRequest request) {
        // Gate：登录上下文
        this.assertGateUserPresent();
        // Pre：管辖库房收窄并组装汇总查询
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        WarehouseInventoryDetailPageQuery query =
            this.inventoryWarehouseInventoryDetailMapper.toWarehouseDetailPageQuery(request, effectiveWarehouseIds);
        this.inventoryCatalogFilterSupport.enrichResolvedDefinitionIds(query);
        List<WarehouseInventoryDetailItemModel> models;
        try {
            // Cmd：装载汇总候选行（带上限行）
            models = this.inventoryWarehouseInventoryDetailMapper.selectCurrentDetailForSum(
                query, InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS);
        } catch (RuntimeException ex) {
            log.warn("现库明细汇总查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_203, "汇总加载失败");
        }
        List<InventoryDetailListRowDto> rows = new ArrayList<>();
        for (WarehouseInventoryDetailItemModel model : models) {
            rows.add(this.inventoryWarehouseInventoryDetailMapper.toListRowDto(model));
        }
        this.enrichRows(rows);
        BigDecimal sumCost = BigDecimal.ZERO;
        BigDecimal sumPrice = BigDecimal.ZERO;
        for (InventoryDetailListRowDto row : rows) {
            if (row.getLineCostTotal() != null) {
                sumCost = sumCost.add(row.getLineCostTotal());
            }
            if (row.getLinePriceTotal() != null) {
                sumPrice = sumPrice.add(row.getLinePriceTotal());
            }
        }
        SumInventoryDetailCurrentResponse response = new SumInventoryDetailCurrentResponse();
        response.setSumLineCostTotal(sumCost);
        response.setSumLinePriceTotal(sumPrice);
        boolean truncated = false;
        if (models.size() >= InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS) {
            truncated = true;
        }
        response.setTruncated(truncated);
        // Asm：映射汇总响应
        return response;
    }

    /**
     * 导出当前筛选结果为 xlsx（{@link AnnotatedExcelExporter} + 导出行 DTO）。
     *
     * @param request 与列表同构筛选
     * @return 文件名、MIME 与字节流
     * @throws BusinessException {@code WHS_DTL_206} 导出失败
     */
    public ExportInventoryDetailCurrentResponse exportInventoryDetailCurrent(@Valid ExportInventoryDetailCurrentRequest request) {
        // Gate：登录上下文
        this.assertGateUserPresent();
        // Pre：管辖库房收窄并组装导出查询
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        WarehouseInventoryDetailPageQuery query =
            this.inventoryWarehouseInventoryDetailMapper.toWarehouseDetailPageQuery(request, effectiveWarehouseIds);
        this.inventoryCatalogFilterSupport.enrichResolvedDefinitionIds(query);
        List<WarehouseInventoryDetailItemModel> models;
        try {
            // Cmd：装载导出行（带上限行）
            models = this.inventoryWarehouseInventoryDetailMapper.selectCurrentDetailForExport(query);
        } catch (RuntimeException ex) {
            log.warn("现库明细导出装载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_206, "导出失败，请稍后或联系管理员");
        }
        List<InventoryDetailListRowDto> rows = new ArrayList<>();
        for (WarehouseInventoryDetailItemModel model : models) {
            rows.add(this.inventoryWarehouseInventoryDetailMapper.toListRowDto(model));
        }
        this.enrichRows(rows);
        ExportInventoryDetailCurrentResponse response = new ExportInventoryDetailCurrentResponse();
        response.setFileName(InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_EXPORT_DEFAULT_FILENAME);
        response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
        response.setExportedRowCount(Integer.valueOf(rows.size()));
        boolean exportTruncated = false;
        if (models.size() >= InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS) {
            exportTruncated = true;
        }
        response.setTruncated(Boolean.valueOf(exportTruncated));
        List<InventoryDetailCurrentExportRowDto> exportRows = new ArrayList<>(rows.size());
        for (InventoryDetailListRowDto row : rows) {
            exportRows.add(this.inventoryWarehouseInventoryDetailMapper.toCurrentExportRowDto(row));
        }
        try {
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SHEET_NAME,
                exportRows,
                InventoryDetailCurrentExportRowDto.class
            );
            response.setBody(body);
        } catch (RuntimeException ex) {
            log.warn("现库明细导出写流失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_206, "导出失败，请稍后或联系管理员");
        }
        // Asm：映射导出响应
        return response;
    }

    /**
     * 批量停供：将选中库存项发布状态置为 INACTIVE（领域 {@code stopSupply}）。
     *
     * @param request 库存项主键列表
     * @return 影响行数
     * @throws BusinessException 未更新任何行或并发冲突
     */
    public RetireInventoryItemsResponse retireInventoryItems(@Valid RetireInventoryItemsRequest request) {
        // Gate：登录上下文与库存项主键列表
        this.assertGateUserPresent();
        GatewayIdParseUtil.requireNonEmptyLongIds(request.getInventoryItemIds(), "停供：库存项主键列表不能为空");
        List<Long> merged = this.dedupeIds(request.getInventoryItemIds());
        StopSupplyCommand command = new StopSupplyCommand();
        command.setOperatorId(this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.WHS_DTL_101,
            "登录上下文缺失"));
        List<StopSupplyCommand.StopSupplyItemCommand> items = new ArrayList<>();
        for (Long inventoryItemId : merged) {
            StopSupplyCommand.StopSupplyItemCommand line = new StopSupplyCommand.StopSupplyItemCommand();
            line.setId(inventoryItemId);
            items.add(line);
        }
        command.setItems(items);
        try {
            // Cmd：领域批量停供
            int affected = this.inventoryItemDomainService.stopSupply(command).size();
            if (affected == 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_DTL_208, "未更新任何库存行");
            }
            RetireInventoryItemsResponse response = new RetireInventoryItemsResponse();
            response.setAffectedRows(affected);
            // Asm：映射停供响应
            return response;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_105, "并发冲突，请稍后重试");
        }
    }

    /**
     * 批量恢复供应：将选中库存项发布状态恢复为 ACTIVE（领域 {@code resumeSupply}）。
     *
     * @param request 库存项主键列表
     * @return 影响行数
     * @throws BusinessException 未更新任何行或并发冲突
     */
    public ResumeInventoryItemsResponse resumeInventoryItems(@Valid ResumeInventoryItemsRequest request) {
        // Gate：登录上下文与库存项主键列表
        this.assertGateUserPresent();
        GatewayIdParseUtil.requireNonEmptyLongIds(request.getInventoryItemIds(), "恢复供应：库存项主键列表不能为空");
        List<Long> merged = this.dedupeIds(request.getInventoryItemIds());
        ResumeSupplyCommand command = new ResumeSupplyCommand();
        command.setOperatorId(this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.WHS_DTL_101,
            "登录上下文缺失"));
        List<ResumeSupplyCommand.ResumeSupplyItemCommand> items = new ArrayList<>();
        for (Long inventoryItemId : merged) {
            ResumeSupplyCommand.ResumeSupplyItemCommand line = new ResumeSupplyCommand.ResumeSupplyItemCommand();
            line.setId(inventoryItemId);
            items.add(line);
        }
        command.setItems(items);
        try {
            // Cmd：领域批量恢复供应
            int affected = this.inventoryItemDomainService.resumeSupply(command).size();
            if (affected == 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_DTL_209, "未更新任何库存行");
            }
            ResumeInventoryItemsResponse response = new ResumeInventoryItemsResponse();
            response.setAffectedRows(affected);
            // Asm：映射恢复响应
            return response;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_105, "并发冲突，请稍后重试");
        }
    }

    /** 会话须具备用户主键，否则无法审计写路径与数据范围。 */
    private void assertGateUserPresent() {
        this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.WHS_DTL_101,
            "登录上下文缺失");
    }

    /**
     * 将请求中的库房多选与用户管辖库位求交，得到最终库房主键集合。
     *
     * <p>管辖集合来自 {@link CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()}（{@code adm_pr_assign}）；
     * empty 时不拼 {@code location_id} 谓词；请求显式多选仍生效。</p>
     *
     * @param requestedWarehouseIds 请求库房多选，可空
     * @return empty 表示不按库房收窄
     */
    private List<Long> resolveEffectiveWarehouseIds(List<Long> requestedWarehouseIds) {
        return this.managedLocationIdsResolver.resolveEffectiveLocationScope(
            requestedWarehouseIds,
            ErrorCodeConstants.WHS_DTL_101,
            "所选库房不在当前用户管辖范围"
        );
    }

    /**
     * 入参主键去重并保持稳定顺序，满足批量写路径幂等期望。
     *
     * @param ids 原始主键列表
     * @return 去重后列表
     */
    private List<Long> dedupeIds(List<Long> ids) {
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 批量装配：产品定义、库房/供应商名称、批号双链定价与行总额（AS §1.3）。
     * 批号进价/售价经 {@link ChargeItemDefinitionQueryService} 批量接口一次（或分段）下推，禁止在本方法内对每行单独发起仓储查询。
     *
     * @param rows 列表行
     */
    private void enrichRows(List<InventoryDetailListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (InventoryDetailListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(row.getItemTable());
            lookup.setProductDefinitionId(row.getItemId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products =
            this.productDefinitionBatchLoader.loadAsMap(lookups);
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        List<Long> locationIds = new ArrayList<>();
        for (InventoryDetailListRowDto row : rows) {
            if (row != null && row.getWarehouseId() != null) {
                locationIds.add(row.getWarehouseId());
            }
        }
        Map<Long, String> locationNames = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, String> supplierNames = this.buildSupplierNameMap();
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> pricingKeys = new LinkedHashSet<>();
        for (InventoryDetailListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getWarehouseId() != null) {
                row.setWarehouseName(locationNames.get(row.getWarehouseId()));
            }
            if (row.getSupplierId() != null) {
                row.setSupplierName(supplierNames.get(row.getSupplierId()));
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(row.getItemTable(), row.getItemId());
            ProductDefinitionDisplayDto product = products.get(key);
            if (product != null) {
                this.applyProductColumns(row, product, terminologyMaps);
            }
            PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, row.getInventoryStatusEnum());
            if (status != null) {
                row.setInventoryStatusText(status.getDisplay());
            }
            if (row.getPurchaseUnitRatio() != null && row.getPackageUnitRatio() != null) {
                ChargeItemDefinitionLotBatchKey lotKey =
                    ChargeItemDefinitionLotBatchKey.maybeOf(row.getItemTable(), row.getItemId(), row.getLotNumber());
                if (lotKey != null) {
                    pricingKeys.add(lotKey);
                }
            }
        }
        Map<String, List<ChargeItemDefinition>> costByKey;
        Map<String, List<ChargeItemDefinition>> priceByKey;
        if (pricingKeys.isEmpty()) {
            costByKey = Collections.emptyMap();
            priceByKey = Collections.emptyMap();
        } else {
            costByKey = this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(pricingKeys);
            priceByKey = this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(pricingKeys);
        }
        for (InventoryDetailListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            this.applyLotPricingAndTotals(row, costByKey, priceByKey);
            this.applyListDisplayFields(row);
        }
    }

    /**
     * 将产品定义读模型列写入列表行（倍数、收费项定义、分类与医保展示等）。
     *
     * @param row     目标行
     * @param product 读模型
     */
    private void applyProductColumns(
        InventoryDetailListRowDto row,
        ProductDefinitionDisplayDto product,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        row.setChargeItemDefinitionId(product.getChargeItemDefinitionId());
        row.setPurchaseUnitRatio(product.getPurchaseUnitRatio());
        row.setPackageUnitRatio(product.getPackageUnitRatio());
        row.setChrgitmLv(product.getChrgitmLv());
        row.setChrgitmLvText(product.getChrgitmLvText());
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
            row.setMedicationCategoryCode(product.getCategoryCode());
            row.setMedicationCategoryText(product.getCategoryCodeDictText());
            row.setDeviceCategoryCode(null);
            row.setDeviceCategoryText(null);
        } else {
            row.setDeviceCategoryCode(product.getCategoryCode());
            row.setDeviceCategoryText(product.getCategoryCodeDictText());
            row.setMedicationCategoryCode(null);
            row.setMedicationCategoryText(null);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                product, terminologyMaps);
        row.setUnitBands(bands);
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        String primaryUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(product);
        row.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, primaryUnitCode, product));
    }

    /**
     * 装配批号进价/售价条件行与行总额（双链独立）；定价行来自批量映射，禁止在本方法内发起逐行仓储查询。
     *
     * @param row         目标行
     * @param costByKey   复合键 → 批号进价定价行列表（可能为空映射）
     * @param priceByKey  复合键 → 批号售价定价行列表
     */
    private void applyLotPricingAndTotals(
        InventoryDetailListRowDto row,
        Map<String, List<ChargeItemDefinition>> costByKey,
        Map<String, List<ChargeItemDefinition>> priceByKey
    ) {
        if (row.getPurchaseUnitRatio() == null || row.getPackageUnitRatio() == null) {
            return;
        }
        if (StringUtils.isBlank(row.getItemTable()) || row.getItemId() == null) {
            return;
        }
        if (StringUtils.isBlank(row.getLotNumber())) {
            return;
        }
        ChargeItemDefinitionLotBatchKey lotKey =
            ChargeItemDefinitionLotBatchKey.maybeOf(row.getItemTable(), row.getItemId(), row.getLotNumber());
        if (lotKey == null) {
            return;
        }
        String composite = lotKey.compositeKey();
        List<ChargeItemDefinition> costLines = costByKey.get(composite);
        if (costLines == null) {
            costLines = Collections.emptyList();
        }
        List<ChargeItemDefinition> priceLines = priceByKey.get(composite);
        if (priceLines == null) {
            priceLines = Collections.emptyList();
        }
        BigDecimal costAmount = this.resolveFirstPriceValue(costLines);
        row.setLotNumberCostAmount(costAmount);
        BigDecimal priceAmount = this.resolveFirstPriceValue(priceLines);
        row.setLotNumberPriceAmount(priceAmount);
        this.assertPositiveRatioOrThrow(row.getPurchaseUnitRatio());
        this.assertPositiveRatioOrThrow(row.getPackageUnitRatio());
        BigDecimal lineCost = this.computeLineTotal(row.getQuantity(), row.getPurchaseUnitRatio(), costAmount);
        BigDecimal linePrice = this.computeLineTotal(row.getQuantity(), row.getPackageUnitRatio(), priceAmount);
        row.setLineCostTotal(lineCost);
        row.setLinePriceTotal(linePrice);
        // 展示单价以包装整包价为锚（与批号维金额一致），禁止 amount÷倍数 作列表展示
        if (costAmount != null) {
            row.setDisplayCostPerSplit(costAmount.setScale(
                ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                RoundingMode.HALF_UP
            ));
        }
        if (priceAmount != null) {
            row.setDisplayPricePerSplit(priceAmount.setScale(
                ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                RoundingMode.HALF_UP
            ));
        }
    }

    /**
     * 列表/导出对客展示：满包整数 + 拆零余量、进售价单价均由服务端换算，不下发倍数与 unitBands。
     *
     * @param row 已装配产品与批号定价的目标行
     */
    private void applyListDisplayFields(InventoryDetailListRowDto row) {
        if (row == null) {
            return;
        }
        List<ProductDefinitionCatalogUnitBandDto> bands = row.getUnitBands();
        String packageUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        if (StringUtils.isBlank(packageUnitName)) {
            packageUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_STOCK);
        }
        String splitUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT);
        if (StringUtils.isBlank(splitUnitName)) {
            splitUnitName = StringUtils.trimToNull(row.getUnitName());
        }
        packageUnitName = StringUtils.trimToNull(packageUnitName);
        splitUnitName = StringUtils.trimToNull(splitUnitName);
        BigDecimal splitQty = row.getQuantity();
        StringBuilder quantityText = new StringBuilder();
        if (splitQty != null) {
            BigDecimal packageRatio = row.getPackageUnitRatio();
            if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
                packageRatio = BigDecimal.ONE;
            }
            // 持久化量为拆零单位：向下取整得满包整数，余量亦为整数片数，禁止小数盒展示
            int packageQty = splitQty.divide(packageRatio, 0, RoundingMode.DOWN).intValue();
            BigDecimal usedInPackages = packageRatio.multiply(BigDecimal.valueOf(packageQty));
            BigDecimal remainder = splitQty.subtract(usedInPackages);
            if (packageQty > 0) {
                quantityText.append(packageQty);
                if (StringUtils.isNotBlank(packageUnitName)) {
                    quantityText.append(packageUnitName);
                }
            }
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                quantityText.append(remainder.setScale(0, RoundingMode.DOWN).intValue());
                if (StringUtils.isNotBlank(splitUnitName)) {
                    quantityText.append(splitUnitName);
                }
            }
        }
        // 零库存或无满包/余量时，回落为「0+包装单位」避免列表空白
        if (quantityText.isEmpty()) {
            StringBuilder zeroText = new StringBuilder(GeneralSharedConstants.ZERO);
            if (StringUtils.isNotBlank(packageUnitName)) {
                zeroText.append(packageUnitName);
            } else if (StringUtils.isNotBlank(row.getUnitName())) {
                zeroText.append(row.getUnitName().trim());
            }
            row.setDisplayQuantityText(zeroText.toString());
        } else {
            row.setDisplayQuantityText(quantityText.toString());
        }
        String costUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE);
        if (StringUtils.isBlank(costUnitName)) {
            costUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        }
        String priceUnitName = resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        row.setDisplayCostUnitPrice(row.getLotNumberCostAmount());
        row.setDisplayCostUnitName(costUnitName);
        row.setDisplayPriceUnitPrice(row.getLotNumberPriceAmount());
        row.setDisplayPriceUnitName(priceUnitName);
    }

    /**
     * 在单位槽列表中按槽语义取单位展示名（术语已预热写入槽）。
     *
     * @param bands    单位槽
     * @param bandKind 槽语义
     * @return 展示名；未命中为 null
     */
    private static String resolveBandUnitName(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind
    ) {
        if (bands == null || bands.isEmpty() || StringUtils.isBlank(bandKind)) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!bandKind.equals(band.getBandKind())) {
                continue;
            }
            if (StringUtils.isNotBlank(band.getUnitName())) {
                return band.getUnitName().trim();
            }
        }
        return null;
    }

    /**
     * 包装→拆零倍数须为正，否则阻断列表与汇总（AS EX-210）。
     *
     * @param ratio 倍数
     */
    private void assertPositiveRatioOrThrow(BigDecimal ratio) {
        if (ratio == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_210, "包装→拆零倍数非法");
        }
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_210, "包装→拆零倍数非法");
        }
    }

    /**
     * 行总额：整包数×包价 + 零头×（包价÷倍数），舍入与收费域一致。
     *
     * @param quantity      拆零数量
     * @param ratio         包装→拆零倍数
     * @param packageAmount 整包价
     * @return 行总额；单价缺失时 null
     */
    private BigDecimal computeLineTotal(BigDecimal quantity, BigDecimal ratio, BigDecimal packageAmount) {
        if (quantity == null || packageAmount == null) {
            return null;
        }
        if (ratio == null) {
            return null;
        }
        BigDecimal[] parts = quantity.divideAndRemainder(ratio);
        BigDecimal wholePackages = parts[0];
        BigDecimal remainderSplits = parts[1];
        BigDecimal perSplit = packageAmount.divide(
            ratio,
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
            RoundingMode.HALF_UP
        );
        BigDecimal total = wholePackages.multiply(packageAmount).add(remainderSplits.multiply(perSplit));
        return total.setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 稳定选取首个非空定价行金额（同键多行保留首次映射，R-002）。
     *
     * @param lines 定价行
     * @return 金额或 null
     */
    private BigDecimal resolveFirstPriceValue(List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        List<ChargeItemDefinition> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparing(ChargeItemDefinition::getId, Comparator.nullsLast(Long::compareTo)));
        for (ChargeItemDefinition line : ordered) {
            if (line == null) {
                continue;
            }
            if (line.getPriceValue() != null) {
                return line.getPriceValue();
            }
        }
        return null;
    }

    /**
     * 构建供应商主键到名称映射（单次列表查询，避免行级 N+1）。
     *
     * @return 映射
     */
    private Map<Long, String> buildSupplierNameMap() {
        Map<Long, String> map = new HashMap<>();
        List<SupplierListItemQueryModel> list = this.supplierQueryService.listSuppliers();
        for (SupplierListItemQueryModel item : list) {
            if (item == null) {
                continue;
            }
            if (item.getId() != null) {
                map.put(item.getId(), item.getName());
            }
        }
        return map;
    }
}
