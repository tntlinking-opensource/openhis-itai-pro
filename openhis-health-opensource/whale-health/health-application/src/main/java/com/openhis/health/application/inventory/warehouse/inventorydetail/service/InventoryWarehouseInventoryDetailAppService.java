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

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailBackupExportRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailBackupListRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.mapper.InventoryWarehouseInventoryDetailMapper;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ExportInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ListInventoryDetailBackupBusNoRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.LoadInventoryDetailFormInitRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.PageInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ResumeInventoryItemsRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.RetireInventoryItemsRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.SumInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ExportInventoryDetailBackupResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ExportInventoryDetailCurrentResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ListInventoryDetailBackupBusNoResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.LoadInventoryDetailFormInitResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.PageInventoryDetailBackupResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.PageInventoryDetailCurrentResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.ResumeInventoryItemsResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.RetireInventoryItemsResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.SumInventoryDetailBackupResponse;
import com.openhis.health.application.inventory.warehouse.inventorydetail.response.SumInventoryDetailCurrentResponse;
import com.openhis.health.domain.document.inventoryitemsnapshot.command.BatchAppendDocInventoryItemSnapshotCommand;
import com.openhis.health.domain.document.inventoryitemsnapshot.command.DocInventoryItemSnapshotRowCommand;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.service.DocInventoryItemSnapshotDomainService;
import com.openhis.health.domain.document.inventoryitemsnapshot.service.DocInventoryItemSnapshotQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.inventory.InventoryCatalogFilterSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailConstants;
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
import com.openhis.health.module.common.validator.SystemOperatorResolver;
import com.openhis.whale.core.infrastructure.job.SchedulableJob;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * 药库库存明细：现库只读、备份只读、定时快照写、供应状态写。
 *
 * <p>职责边界：现库 IF-02/03/06 经 {@link ChargeItemDefinitionQueryService} 取当前价；
 * 备份 IF-04/05/07 只读静表冻结四价（禁止联结定价）；IF-10 复用现库装配后批量落库。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inventoryWarehouseInventoryDetail/{methodName}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryWarehouseInventoryDetailAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final CodeSystemQueryService codeSystemQueryService;
    private final LocationQueryService locationQueryService;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryWarehouseInventoryDetailMapper inventoryWarehouseInventoryDetailMapper;
    private final InventoryCatalogFilterSupport inventoryCatalogFilterSupport;
    /** 库存明细快照读侧（备份 IF-04/05/07/11）。 */
    private final DocInventoryItemSnapshotQueryService docInventoryItemSnapshotQueryService;
    /** 库存明细快照写侧（IF-10 批量 append）。 */
    private final DocInventoryItemSnapshotDomainService docInventoryItemSnapshotDomainService;
    /** 备份批次单号 {@code ABU} 序号生成。 */
    private final BusinessSequenceService businessSequenceService;
    /** 定时任务无登录态时的系统操作人。 */
    private final SystemOperatorResolver systemOperatorResolver;

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
            // Pre：供应状态与 wkf_inventory_item.inventory_status_enum 同口径（PublicationStatus 枚举，非术语表）
            List<OptionDto> allPub = this.codeSystemQueryService.getOptionListByEnum(PublicationStatus.class);
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
            log.warn("库存明细初始化：供应状态枚举加载失败", ex);
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
        try {
            // Pre：库存范围仅暴露「数量等于0 / 数量大于0」，排除「无限制」。
            List<OptionDto> allRange =
                terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.INVENTORY_RANGE);
            List<OptionDto> filteredRange = new ArrayList<>();
            for (OptionDto option : allRange) {
                if (option == null) {
                    continue;
                }
                if (InventoryWarehouseDetailConstants.INVENTORY_RANGE_ZERO_CODE.equals(option.getValue())
                    || InventoryWarehouseDetailConstants.INVENTORY_RANGE_GT_ZERO_CODE.equals(option.getValue())) {
                    filteredRange.add(option);
                }
            }
            response.setInventoryRangeOptions(filteredRange);
        } catch (RuntimeException ex) {
            log.warn("库存明细初始化：库存范围术语加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_201, "筛选项加载失败");
        }
        // Pre：管辖药库+药房与供应商下拉（与列表筛选 warehouseIds/supplierIds 对齐）
        response.setWarehouseOptions(this.buildWarehouseOptions());
        response.setSupplierOptions(this.buildSupplierOptions());
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
        this.inventoryCatalogFilterSupport.enrichKeywordDefinitionIds(query);
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
        this.inventoryCatalogFilterSupport.enrichKeywordDefinitionIds(query);
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
        this.inventoryCatalogFilterSupport.enrichKeywordDefinitionIds(query);
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
        command.setOperatorId(this.currentPractitionerResolver.requireOperatorId(
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
        command.setOperatorId(this.currentPractitionerResolver.requireOperatorId(
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

    /**
     * 备份库存明细分页：解析批次单号 → 静表分页 → 冻结列映射 DTO（禁止联结定价）。
     *
     * @param request 筛选与分页
     * @return 分页列表
     * @throws BusinessException {@code WHS_DTL_204} 列表加载失败
     */
    public PageInventoryDetailBackupResponse pageInventoryDetailBackup(@Valid PageInventoryDetailBackupRequest request) {
        // Gate：登录上下文与租户
        this.assertGateUserPresent();
        Long tenantId = this.requireTenantId();
        // Pre：解析 backupBusNo 与管辖库房
        String resolvedBusNo = this.resolveBackupBusNo(request.getBackupBusNo(), tenantId);
        if (resolvedBusNo == null) {
            // 当天无快照批次时直接返回空页，避免误查全表
            return this.buildEmptyBackupPageResponse(request);
        }
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        DocInventoryItemSnapshotPageQuery query = this.inventoryWarehouseInventoryDetailMapper.toSnapshotPageQuery(
            request, tenantId, resolvedBusNo, effectiveWarehouseIds);
        Sort sort = Sort.by(Sort.Order.asc(FieldNameConstants.PY_STR));
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize(), sort);
        Page<DocInventoryItemSnapshotRowQueryModel> page;
        try {
            // Cmd：静表分页（四价直读表列，BR-DOC-SNAP-008）
            page = this.docInventoryItemSnapshotQueryService.pageSnapshotRows(query, pageable);
        } catch (RuntimeException ex) {
            log.warn("备份库存明细分页查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_204, "备份列表加载失败", ex);
        }
        List<InventoryDetailBackupListRowDto> rows = new ArrayList<>();
        for (DocInventoryItemSnapshotRowQueryModel model : page.getContent()) {
            rows.add(this.inventoryWarehouseInventoryDetailMapper.toBackupListRowDto(model));
        }
        this.enrichBackupRows(rows);
        // Asm：装配分页响应
        return PagedResultBuilder.build(
            new PageInventoryDetailBackupResponse(),
            rows,
            page.getTotalElements(),
            request.getPageNo(),
            request.getBoundedPageSize()
        );
    }

    /**
     * 备份底部汇总：同 IF-04 筛选，累加静表冻结行总额。
     *
     * @param request 与备份列表同构筛选
     * @return 汇总金额与截断标记
     * @throws BusinessException {@code WHS_DTL_205} 汇总加载失败
     */
    public SumInventoryDetailBackupResponse sumInventoryDetailBackup(@Valid SumInventoryDetailBackupRequest request) {
        // Gate：登录上下文与租户
        this.assertGateUserPresent();
        Long tenantId = this.requireTenantId();
        // Pre：解析 backupBusNo 与管辖库房
        String resolvedBusNo = this.resolveBackupBusNo(request.getBackupBusNo(), tenantId);
        SumInventoryDetailBackupResponse empty = new SumInventoryDetailBackupResponse();
        empty.setSumLineCostTotal(BigDecimal.ZERO);
        empty.setSumLinePriceTotal(BigDecimal.ZERO);
        empty.setTruncated(Boolean.FALSE);
        if (resolvedBusNo == null) {
            // 无可用批次时返回零汇总，避免误查全表
            return empty;
        }
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        DocInventoryItemSnapshotPageQuery query = this.inventoryWarehouseInventoryDetailMapper.toSnapshotPageQuery(
            request, tenantId, resolvedBusNo, effectiveWarehouseIds);
        List<DocInventoryItemSnapshotRowQueryModel> models;
        try {
            // Cmd：拉取汇总候选行（禁止重算定价）
            models = this.docInventoryItemSnapshotQueryService.listSnapshotRowsForSum(
                query, InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS);
        } catch (RuntimeException ex) {
            log.warn("备份库存明细汇总查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_205, "备份汇总加载失败", ex);
        }
        BigDecimal sumCost = BigDecimal.ZERO;
        BigDecimal sumPrice = BigDecimal.ZERO;
        for (DocInventoryItemSnapshotRowQueryModel model : models) {
            if (model == null) {
                continue;
            }
            if (model.getLineCostTotal() != null) {
                sumCost = sumCost.add(model.getLineCostTotal());
            }
            if (model.getLinePriceTotal() != null) {
                sumPrice = sumPrice.add(model.getLinePriceTotal());
            }
        }
        SumInventoryDetailBackupResponse response = new SumInventoryDetailBackupResponse();
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
     * 导出备份库存明细 Excel（静表冻结列，禁止联结定价）。
     *
     * @param request 与备份汇总同构筛选
     * @return 文件名、MIME 与字节流
     * @throws BusinessException {@code WHS_DTL_207} 导出失败
     */
    public ExportInventoryDetailBackupResponse exportInventoryDetailBackup(@Valid ExportInventoryDetailBackupRequest request) {
        // Gate：登录上下文与租户
        this.assertGateUserPresent();
        Long tenantId = this.requireTenantId();
        String resolvedBusNo = this.resolveBackupBusNo(request.getBackupBusNo(), tenantId);
        ExportInventoryDetailBackupResponse response = new ExportInventoryDetailBackupResponse();
        response.setFileName(InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_BACKUP_EXPORT_DEFAULT_FILENAME);
        response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
        response.setExportedRowCount(Integer.valueOf(0));
        response.setTruncated(Boolean.FALSE);
        if (resolvedBusNo == null) {
            try {
                byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                    InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SHEET_NAME,
                    Collections.emptyList(),
                    InventoryDetailBackupExportRowDto.class
                );
                response.setBody(body);
            } catch (RuntimeException ex) {
                log.warn("备份库存空导出写流失败", ex);
                throw new BusinessException(ErrorCodeConstants.WHS_DTL_207, "导出失败，请稍后或联系管理员", ex);
            }
            return response;
        }
        List<Long> effectiveWarehouseIds = this.resolveEffectiveWarehouseIds(request.getWarehouseIds());
        DocInventoryItemSnapshotPageQuery query = this.inventoryWarehouseInventoryDetailMapper.toSnapshotPageQuery(
            request, tenantId, resolvedBusNo, effectiveWarehouseIds);
        List<DocInventoryItemSnapshotRowQueryModel> models;
        try {
            // Cmd：拉取导出行（上限 10 万）
            models = this.docInventoryItemSnapshotQueryService.listSnapshotRowsForExport(
                query, InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_BACKUP_EXPORT_MAX_ROWS);
        } catch (RuntimeException ex) {
            log.warn("备份库存明细导出装载失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_207, "导出失败，请稍后或联系管理员", ex);
        }
        List<InventoryDetailBackupListRowDto> rows = new ArrayList<>();
        for (DocInventoryItemSnapshotRowQueryModel model : models) {
            rows.add(this.inventoryWarehouseInventoryDetailMapper.toBackupListRowDto(model));
        }
        this.enrichBackupRows(rows);
        response.setExportedRowCount(Integer.valueOf(rows.size()));
        boolean exportTruncated = false;
        if (models.size() >= InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_BACKUP_EXPORT_MAX_ROWS) {
            exportTruncated = true;
        }
        response.setTruncated(Boolean.valueOf(exportTruncated));
        List<InventoryDetailBackupExportRowDto> exportRows = new ArrayList<>(rows.size());
        for (InventoryDetailBackupListRowDto row : rows) {
            exportRows.add(this.inventoryWarehouseInventoryDetailMapper.toBackupExportRowDto(row));
        }
        try {
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SHEET_NAME,
                exportRows,
                InventoryDetailBackupExportRowDto.class
            );
            response.setBody(body);
        } catch (RuntimeException ex) {
            log.warn("备份库存明细导出写流失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_207, "导出失败，请稍后或联系管理员", ex);
        }
        // Asm：映射导出响应
        return response;
    }

    /**
     * 备份单号下拉：返回本租户历史 {@code bus_no} 降序去重列表。
     *
     * @param request 空壳请求
     * @return 单号列表
     * @throws BusinessException {@code WHS_DTL_212} 加载失败
     */
    public ListInventoryDetailBackupBusNoResponse listInventoryDetailBackupBusNo(
        @Valid ListInventoryDetailBackupBusNoRequest request
    ) {
        // Gate：登录上下文与租户
        this.assertGateUserPresent();
        Long tenantId = this.requireTenantId();
        List<String> busNoList;
        try {
            // Cmd：distinct bus_no DESC
            busNoList = this.docInventoryItemSnapshotQueryService.findDistinctBusNoByTenant(tenantId);
        } catch (RuntimeException ex) {
            log.warn("备份单号列表查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_212, "备份单号加载失败", ex);
        }
        ListInventoryDetailBackupBusNoResponse response = new ListInventoryDetailBackupBusNoResponse();
        response.setBackupBusNoList(busNoList);
        // Asm：映射响应
        return response;
    }

    /**
     * 定时库存快照（IF-INV-DTL-10）：全量现库装配后批量写入静表。
     *
     * <p>处理器 key {@code inventoryWarehouseInventoryDetail/executeInventoryDetailSnapshot}；
     * cron 与启停由 whale-job 管理界面配置，并发策略建议 {@code DISALLOW}（BR-INV-DTL-021）。</p>
     *
     * @throws BusinessException {@code WHS_DTL_101} 租户缺失；{@code WHS_DTL_211} 落库失败
     */
    @SchedulableJob("库存备份")
    public void executeInventoryDetailSnapshot() {
        // Gate：后台任务须已绑定租户上下文（由 Job 注入 TenantContext）
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "租户上下文缺失");
        }
        LocalDate today = LocalDate.now();
        // Pre：日级幂等，当日已有 ABU 批次则跳过（BR-INV-DTL-020）
        if (this.docInventoryItemSnapshotQueryService.existsSuccessfulBackupForDate(tenantId, today)) {
            log.info("库存明细快照跳过：租户 {} 当日 {} 已有成功批次", tenantId, today);
            return;
        }
        LocalDateTime snapshotTime = LocalDateTime.now();
        Long operatorId = this.systemOperatorResolver.getOperatorPractitionerId();
        WarehouseInventoryDetailPageQuery inventoryQuery = new WarehouseInventoryDetailPageQuery();
        int pageSize = InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SNAPSHOT_PAGE_SIZE;
        Sort sort = Sort.by(Sort.Order.asc(FieldNameConstants.PY_STR));
        Pageable firstPageable = PageRequest.of(0, pageSize, sort);
        Page<WarehouseInventoryDetailItemModel> firstPage =
            this.inventoryWarehouseInventoryDetailMapper.selectCurrentDetailPage(inventoryQuery, firstPageable);
        if (firstPage.getTotalElements() == 0L) {
            // 现库无明细时不落库、不分配 bus_no（R-012）；日级幂等依赖后续有库存时首次成功快照
            log.info("库存明细快照：租户 {} 现库无明细，跳过错落库", tenantId);
            return;
        }
        // Pre：确认现库有明细后再分配本自然日批次单号（ABU+yyyyMMdd+2位）
        String busNo = this.businessSequenceService.getSeqByDay(
            AggregateTypeConstants.INVENTORY_DETAIL_SNAPSHOT_BACKUP,
            BusinessSequenceConstants.INVENTORY_DETAIL_SNAPSHOT_BUS_NO_SEQ_LENGTH);
        int totalPages = firstPage.getTotalPages();
        int pageNo = 1;
        int totalPersisted = 0;
        while (pageNo <= totalPages) {
            Page<WarehouseInventoryDetailItemModel> page;
            if (pageNo == 1) {
                page = firstPage;
            } else {
                Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);
                page = this.inventoryWarehouseInventoryDetailMapper.selectCurrentDetailPage(inventoryQuery, pageable);
            }
            List<WarehouseInventoryDetailItemModel> models = page.getContent();
            List<InventoryDetailListRowDto> rows = new ArrayList<>();
            for (WarehouseInventoryDetailItemModel model : models) {
                rows.add(this.inventoryWarehouseInventoryDetailMapper.toListRowDto(model));
            }
            // Pre：与 IF-02 同等定价装配后映射冻结列
            this.enrichRows(rows);
            List<DocInventoryItemSnapshotRowCommand> snapshotRows = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                InventoryDetailListRowDto row = rows.get(index);
                WarehouseInventoryDetailItemModel model = models.get(index);
                if (row == null || model == null) {
                    continue;
                }
                snapshotRows.add(this.inventoryWarehouseInventoryDetailMapper.toSnapshotRowCommand(row, model));
            }
            if (!snapshotRows.isEmpty()) {
                BatchAppendDocInventoryItemSnapshotCommand cmd = new BatchAppendDocInventoryItemSnapshotCommand();
                cmd.setTenantId(tenantId);
                cmd.setBusNo(busNo);
                cmd.setSnapshotTime(snapshotTime);
                cmd.setCreatedBy(operatorId);
                cmd.setRows(snapshotRows);
                try {
                    // Cmd：批量 append 静表
                    totalPersisted = totalPersisted + this.docInventoryItemSnapshotDomainService.batchAppendSnapshots(cmd);
                } catch (RuntimeException ex) {
                    log.error("库存明细快照落库失败 tenantId={} busNo={} pageNo={}", tenantId, busNo, pageNo, ex);
                    throw new BusinessException(ErrorCodeConstants.WHS_DTL_211, "库存明细快照落库失败", ex);
                }
            }
            pageNo = pageNo + 1;
        }
        log.info("库存明细快照完成 tenantId={} busNo={} rows={}", tenantId, busNo, totalPersisted);
        // Asm：任务成功由 ScheduledJobExecutionLog 记录（框架）
    }

    /** 会话须具备用户主键，否则无法审计写路径与数据范围。 */
    private void assertGateUserPresent() {
        this.currentPractitionerResolver.requireOperatorId(
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
     * 解析当前 HTTP 租户；备份读/写须显式写入快照查询 tenantId。
     *
     * @return 租户主键
     * @throws BusinessException 租户缺失
     */
    private Long requireTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_DTL_101, "租户上下文缺失");
        }
        return tenantId;
    }

    /**
     * 解析备份批次单号：显式传入优先；否则取当自然日最新 {@code ABU} 批次（INV-DTL-SDD-R-009）。
     *
     * @param requestedBackupBusNo 请求单号，可空
     * @param tenantId             租户
     * @return 有效单号；无匹配时 null
     */
    private String resolveBackupBusNo(String requestedBackupBusNo, Long tenantId) {
        String explicit = trimToNull(requestedBackupBusNo);
        if (explicit != null) {
            return explicit;
        }
        String todayPrefix = AggregateTypeConstants.INVENTORY_DETAIL_SNAPSHOT_BACKUP
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<String> allBusNos = this.docInventoryItemSnapshotQueryService.findDistinctBusNoByTenant(tenantId);
        for (String busNo : allBusNos) {
            if (busNo != null && busNo.startsWith(todayPrefix)) {
                return busNo;
            }
        }
        return null;
    }

    /**
     * 无可用备份批次时返回空分页壳，避免静表无 busNo 谓词误查。
     *
     * @param request 原分页请求（保留 pageNo/pageSize）
     * @return 空列表分页响应
     */
    private PageInventoryDetailBackupResponse buildEmptyBackupPageResponse(PageInventoryDetailBackupRequest request) {
        return PagedResultBuilder.build(
            new PageInventoryDetailBackupResponse(),
            Collections.emptyList(),
            0L,
            request.getPageNo(),
            request.getBoundedPageSize()
        );
    }

    /**
     * 备份列表展示 enrich：库房/供应商名、字典 Label、库存展示串；禁止联结定价重算四价。
     *
     * @param rows 备份列表行
     */
    private void enrichBackupRows(List<InventoryDetailBackupListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        List<Long> locationIds = new ArrayList<>();
        for (InventoryDetailBackupListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(row.getItemTable());
            lookup.setProductDefinitionId(row.getItemId());
            lookups.add(lookup);
            if (row.getWarehouseId() != null) {
                locationIds.add(row.getWarehouseId());
            }
        }
        Map<String, ProductDefinitionDisplayDto> products =
            this.productDefinitionBatchLoader.loadAsMap(lookups);
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        Map<Long, String> locationNames = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, String> supplierNames = this.buildSupplierNameMap();
        Map<String, String> medCategoryLabels = Collections.emptyMap();
        Map<String, String> deviceCategoryLabels = Collections.emptyMap();
        Map<String, String> doseFormLabels = Collections.emptyMap();
        Map<String, String> unitLabels = Collections.emptyMap();
        Map<String, String> chrgitmLabels = Collections.emptyMap();
        if (terminologyMaps != null) {
            medCategoryLabels = terminologyMaps.getMedicationCategoryLabelByCode();
            deviceCategoryLabels = terminologyMaps.getDeviceCategoryLabelByCode();
            doseFormLabels = terminologyMaps.getDoseFormLabelByCode();
            unitLabels = terminologyMaps.getUnitLabelByCode();
            chrgitmLabels = terminologyMaps.getChrgitmLvLabelByCode();
        }
        for (InventoryDetailBackupListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getWarehouseId() != null) {
                row.setWarehouseName(locationNames.get(row.getWarehouseId()));
            }
            if (row.getSupplierId() != null) {
                row.setSupplierName(supplierNames.get(row.getSupplierId()));
            }
            PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, row.getInventoryStatusEnum());
            if (status != null) {
                row.setInventoryStatusLabel(status.getDisplay());
            }
            if (row.getChrgitmLv() != null) {
                row.setChrgitmLvLabel(chrgitmLabels.get(String.valueOf(row.getChrgitmLv())));
            }
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                row.setCategoryLabel(this.lookupCodeLabel(medCategoryLabels, row.getMedicationCategoryCode()));
            } else {
                row.setCategoryLabel(this.lookupCodeLabel(deviceCategoryLabels, row.getDeviceCategoryCode()));
            }
            row.setDoseFormLabel(this.lookupCodeLabel(doseFormLabels, row.getDoseFormCode()));
            if (unitLabels != null && StringUtils.isNotBlank(row.getPackageUnitCode())) {
                row.setPackageUnitName(unitLabels.get(row.getPackageUnitCode().trim()));
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(row.getItemTable(), row.getItemId());
            ProductDefinitionDisplayDto product = products.get(key);
            if (product != null) {
                List<ProductDefinitionCatalogUnitBandDto> bands =
                    ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                        product, terminologyMaps);
                row.setUnitBands(bands);
                row.setSplitUnitName(
                    resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT));
            }
            this.applyBackupListDisplayFields(row);
        }
    }

    /**
     * 备份列表库存展示串：倍数取自静表 {@code packageUnitRatio}，禁止重算行总额。
     *
     * @param row 备份列表行
     */
    private void applyBackupListDisplayFields(InventoryDetailBackupListRowDto row) {
        if (row == null) {
            return;
        }
        BigDecimal splitToPackageRatio = this.resolveBackupSplitToPackageRatio(row);
        String packageUnitName = trimToNull(row.getPackageUnitName());
        String splitUnitName = trimToNull(row.getSplitUnitName());
        BigDecimal splitQty = row.getQuantity();
        if (splitQty != null) {
            int packageQty = splitQty.divide(splitToPackageRatio, 0, RoundingMode.DOWN).intValue();
            StringBuilder packageText = new StringBuilder();
            if (packageQty > 0) {
                packageText.append(packageQty);
                if (StringUtils.isNotBlank(packageUnitName)) {
                    packageText.append(packageUnitName);
                }
            } else {
                packageText.append(GeneralSharedConstants.ZERO);
                if (StringUtils.isNotBlank(packageUnitName)) {
                    packageText.append(packageUnitName);
                }
            }
            row.setPackageStockText(packageText.toString());
            StringBuilder splitText = new StringBuilder();
            splitText.append(splitQty.setScale(0, RoundingMode.DOWN).intValue());
            if (StringUtils.isNotBlank(splitUnitName)) {
                splitText.append(splitUnitName);
            }
            row.setSplitStockText(splitText.toString());
        }
    }

    /**
     * 备份行拆零↔包装倍数：优先静表 {@code packageUnitRatio}，再回落单位槽。
     *
     * @param row 备份列表行
     * @return 正倍数
     */
    private BigDecimal resolveBackupSplitToPackageRatio(InventoryDetailBackupListRowDto row) {
        if (row.getPackageUnitRatio() != null && row.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
            return row.getPackageUnitRatio();
        }
        BigDecimal ratio = this.resolveBandRatioToSplit(
            row.getUnitBands(),
            ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
            null);
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return ratio;
    }

    /**
     * 从术语预热映射中取 code 对应展示名。
     *
     * @param labelByCode code→label
     * @param code        字典 code
     * @return 展示名或 null
     */
    private String lookupCodeLabel(Map<String, String> labelByCode, String code) {
        if (labelByCode == null || StringUtils.isBlank(code)) {
            return null;
        }
        return labelByCode.get(code.trim());
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
                row.setInventoryStatusLabel(status.getDisplay());
            }
            ChargeItemDefinitionLotBatchKey lotKey =
                ChargeItemDefinitionLotBatchKey.maybeOf(row.getItemTable(), row.getItemId(), row.getLotNumber());
            if (lotKey != null) {
                pricingKeys.add(lotKey);
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
        row.setProductBusNo(product.getProductBusNo());
        row.setMerchandiseName(product.getMerchandiseName());
        row.setTotalVolume(product.getTotalVolume());
        row.setManufacturer(product.getManufacturer());
        row.setDoseFormCode(product.getDoseFormCode());
        row.setApprovalNumber(product.getApprovalNumber());
        row.setYbNo(product.getYbNo());
        row.setPackageUnitRatioFallback(product.getPackageUnitRatio());
        row.setChrgitmLv(product.getChrgitmLv());
        row.setChrgitmLvLabel(product.getChrgitmLvText());
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
            row.setMedicationCategoryCode(product.getCategoryCode());
            row.setCategoryLabel(product.getCategoryCodeDictText());
            row.setDeviceCategoryCode(null);
        } else {
            row.setDeviceCategoryCode(product.getCategoryCode());
            row.setCategoryLabel(product.getCategoryCodeDictText());
            row.setMedicationCategoryCode(null);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                product, terminologyMaps);
        row.setUnitBands(bands);
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        String packageUnitCode = resolveBandUnitCode(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        if (StringUtils.isBlank(packageUnitCode)) {
            packageUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(product);
        }
        row.setPackageUnitCode(packageUnitCode);
        row.setPackageUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, packageUnitCode, product));
        row.setSplitUnitName(resolveBandUnitName(bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT));
        if (StringUtils.isNotBlank(product.getDoseFormCode()) && terminologyMaps != null) {
            Map<String, String> doseFormLabels = terminologyMaps.getDoseFormLabelByCode();
            if (doseFormLabels != null) {
                row.setDoseFormLabel(doseFormLabels.get(product.getDoseFormCode()));
            }
        }
        if (StringUtils.isBlank(row.getDoseFormLabel())) {
            row.setDoseFormLabel(product.getDoseFormCodeDictText());
        }
        BigDecimal ratio = this.resolveBandRatioToSplit(
            bands,
            ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
            product.getPackageUnitRatio());
        if (ratio != null) {
            row.setPackageUnitRatio(ratio);
        }
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
        if (row.getPackageUnitRatio() == null) {
            BigDecimal bandRatio = this.resolveBandRatioToSplit(
                row.getUnitBands(),
                ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
                row.getPackageUnitRatioFallback());
            if (bandRatio == null) {
                return;
            }
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
        BigDecimal priceAmount = this.resolveFirstPriceValue(priceLines);
        BigDecimal splitToPackageRatio = this.resolveSplitToPackageRatio(row);
        this.assertPositiveRatioOrThrow(splitToPackageRatio);
        BigDecimal lineCost = this.computeLineTotal(row.getQuantity(), splitToPackageRatio, costAmount);
        BigDecimal linePrice = this.computeLineTotal(row.getQuantity(), splitToPackageRatio, priceAmount);
        row.setLotCostPrice(scaleMoney(costAmount));
        row.setLotSalePrice(scaleMoney(priceAmount));
        row.setLineCostTotal(lineCost);
        row.setLinePriceTotal(linePrice);
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
        BigDecimal splitToPackageRatio = this.resolveSplitToPackageRatio(row);
        String packageUnitName = trimToNull(row.getPackageUnitName());
        String splitUnitName = trimToNull(row.getSplitUnitName());
        BigDecimal splitQty = row.getQuantity();
        if (splitQty != null) {
            int packageQty = splitQty.divide(splitToPackageRatio, 0, RoundingMode.DOWN).intValue();
            StringBuilder packageText = new StringBuilder();
            if (packageQty > 0) {
                packageText.append(packageQty);
                if (StringUtils.isNotBlank(packageUnitName)) {
                    packageText.append(packageUnitName);
                }
            } else {
                packageText.append(GeneralSharedConstants.ZERO);
                if (StringUtils.isNotBlank(packageUnitName)) {
                    packageText.append(packageUnitName);
                }
            }
            row.setPackageStockText(packageText.toString());
            StringBuilder splitText = new StringBuilder();
            splitText.append(splitQty.setScale(0, RoundingMode.DOWN).intValue());
            if (StringUtils.isNotBlank(splitUnitName)) {
                splitText.append(splitUnitName);
            }
            row.setSplitStockText(splitText.toString());
        }
    }

    /**
     * 金额按收费域小数位舍入；null 保持 null。
     *
     * @param amount 原金额
     * @return 舍入后金额
     */
    private BigDecimal scaleMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP);
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
     * 在单位槽列表中按槽语义取单位编码。
     *
     * @param bands    单位槽
     * @param bandKind 槽语义
     * @return 单位编码；未命中为 null
     */
    private static String resolveBandUnitCode(
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
            if (StringUtils.isNotBlank(band.getUnitCode())) {
                return band.getUnitCode().trim();
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
     * 拆零↔包装换算比（1 包装 = ratio 拆零；与库存/采购单位无关）。
     *
     * @param row 已装配单位槽的目标行
     * @return 正倍数，无法解析时为 {@code 1}
     */
    private BigDecimal resolveSplitToPackageRatio(InventoryDetailListRowDto row) {
        BigDecimal ratio = this.resolveBandRatioToSplit(
            row.getUnitBands(),
            ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
            row.getPackageUnitRatioFallback());
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            if (row.getPackageUnitRatio() != null && row.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                return row.getPackageUnitRatio();
            }
            return BigDecimal.ONE;
        }
        return ratio;
    }

    /**
     * 从单位槽解析相对拆零倍数，未命中时回落读模型字段。
     *
     * @param bands        单位槽
     * @param bandKind     槽语义
     * @param fallbackRatio 读模型倍数
     * @return 正倍数；无法解析时为 {@code null}
     */
    private BigDecimal resolveBandRatioToSplit(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind,
        BigDecimal fallbackRatio
    ) {
        if (bands != null && StringUtils.isNotBlank(bandKind)) {
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                if (band == null) {
                    continue;
                }
                if (!bandKind.equals(band.getBandKind())) {
                    continue;
                }
                BigDecimal ratio = band.getRatioToSplit();
                if (ratio != null && ratio.compareTo(BigDecimal.ZERO) > 0) {
                    return ratio;
                }
            }
        }
        if (fallbackRatio != null && fallbackRatio.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackRatio;
        }
        return null;
    }

    /**
     * 行总额：整包数×包装整包价 + 拆零余量×（包装整包价÷拆零换算比），舍入与收费域一致。
     *
     * @param quantity      拆零最小单位账面数量
     * @param ratio         拆零↔包装换算比（1 包装 = ratio 拆零）
     * @param packageAmount 包装整包价
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

    /**
     * 构建管辖范围内药库+药房筛选项（与 {@link #resolveEffectiveWarehouseIds} 数据源一致）。
     *
     * @return OptionDto 列表
     */
    private List<OptionDto> buildWarehouseOptions() {
        List<OptionDto> options = new ArrayList<>();
        LinkedHashMap<Long, Location> locationById = new LinkedHashMap<>();
        for (Location warehouse : this.managedLocationIdsResolver.listManagedActiveWarehousesOrEmpty()) {
            if (warehouse == null || warehouse.getId() == null) {
                continue;
            }
            locationById.put(warehouse.getId(), warehouse);
        }
        for (Location pharmacy : this.managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty()) {
            if (pharmacy == null || pharmacy.getId() == null) {
                continue;
            }
            locationById.put(pharmacy.getId(), pharmacy);
        }
        for (Location location : locationById.values()) {
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(location.getId()));
            option.setLabel(location.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 构建供应商筛选项（与行级 supplierName 映射同源）。
     *
     * @return OptionDto 列表
     */
    private List<OptionDto> buildSupplierOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<SupplierListItemQueryModel> suppliers = this.supplierQueryService.listSuppliers();
        for (SupplierListItemQueryModel supplier : suppliers) {
            if (supplier == null || supplier.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(supplier.getId()));
            option.setLabel(supplier.getName());
            options.add(option);
        }
        return options;
    }
}