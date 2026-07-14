/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.mapper;

import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.AgreePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.CreatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RejectPurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RemovePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.SubmitPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.WithdrawPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.AgreePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.CreatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetPurchaseOrderDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.LoadPurchaseOrderFormInitResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseOrdersResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RejectPurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RemovePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.SubmitPurchaseOrderApprovalResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.UpdatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.WithdrawPurchaseOrderApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PageProductsForPurchaseRequest;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 药库订货单装配实现：{@link InventoryWarehousePurchaseOrderMapper}。
 *
 * <p>列表/详情/命令/响应装配与选品分页映射；B.10.8.1 / B.10.11 口径以接口 JavaDoc 为准。</p>
 *
 * <p>各 {@code @Override} 方法须在 JavaDoc 中与接口保持<strong>同义完整说明</strong>（可补充实现细节与体内 {@code //} 呼应），
 * 禁止仅用单独一行 {@code @inheritDoc} 作为方法说明占位（B.1）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehousePurchaseOrderMapperImpl implements InventoryWarehousePurchaseOrderMapper {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param locations 启用库位列表
     * @return 下拉选项；{@code null} 输入返回空列表
     */
    @Override
    public List<OptionDto> toOptionDtoListFromLocations(List<Location> locations) {
        List<OptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            Long id = location.getId();
            String label = location.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 将供应商列表映射为 {@link OptionDto} 下拉。
     *
     * @param suppliers 供应商列表项
     * @return 下拉选项；标签格式为「名称（类型展示名）」
     */
    @Override
    public List<OptionDto> toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel> suppliers) {
        List<OptionDto> list = new ArrayList<>();
        if (suppliers == null) {
            return list;
        }
        for (SupplierListItemQueryModel row : suppliers) {
            if (row == null || row.getId() == null) {
                continue;
            }
            Long id = row.getId();
            SupplierType st = EnumUtil.fromCode(SupplierType.class, row.getTypeEnum());
            String typeLabel;
            if (st != null) {
                typeLabel = st.getDisplay();
            } else {
                typeLabel = row.getTypeEnum();
            }
            String baseName = row.getName();
            if (StringUtils.isEmpty(baseName)) {
                baseName = String.valueOf(id);
            }
            String label = baseName;
            if (StringUtils.isNotEmpty(typeLabel)) {
                label = baseName + "（" + typeLabel + "）";
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 装配订货页初始化响应。
     *
     * @param warehouseLocationOptions     药库选项（含「全部」由 AppService 前置）
     * @param supplierOptions              供应商选项
     * @param warehouseKeeperOptions       库管员选项
     * @param statusOptions                单据状态选项
     * @param purchaseItemTableTypeOptions 物品来源表选项
     * @return 页初始化响应
     */
    @Override
    public LoadPurchaseOrderFormInitResponse toLoadPurchaseOrderFormInitResponse(
        List<OptionDto> warehouseLocationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> warehouseKeeperOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> purchaseItemTableTypeOptions) {
        LoadPurchaseOrderFormInitResponse res = new LoadPurchaseOrderFormInitResponse();
        res.setWarehouseLocationOptions(warehouseLocationOptions);
        res.setSupplierOptions(supplierOptions);
        res.setWarehouseKeeperOptions(warehouseKeeperOptions);
        res.setStatusOptions(statusOptions);
        res.setPurchaseItemTableTypeOptions(purchaseItemTableTypeOptions);
        return res;
    }

    /**
     * 将医务人员列表映射为下拉选项。
     *
     * @param practitioners 医务人员列表
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners) {
        List<OptionDto> list = new ArrayList<>();
        if (practitioners == null) {
            return list;
        }
        for (Practitioner p : practitioners) {
            if (p == null || p.getId() == null) {
                continue;
            }
            Long id = p.getId();
            String label = p.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 装配供应请求状态枚举选项（持久化 code）。
     *
     * @return 按 {@link SupplyStatus#getSortOrder()} 排序的状态下拉
     */
    @Override
    public List<OptionDto> toOptionDtoListSupplyStatuses() {
        List<SupplyStatus> ordered = new ArrayList<>();
        for (SupplyStatus st : SupplyStatus.values()) {
            ordered.add(st);
        }
        ordered.sort(Comparator.comparing(SupplyStatus::getSortOrder));
        List<OptionDto> list = new ArrayList<>();
        for (SupplyStatus st : ordered) {
            list.add(new OptionDto(st.getSortOrder(), st.getCode(), st.getDisplay()));
        }
        return list;
    }

    /**
     * 装配物品来源表（药品/耗材）选项。
     *
     * @return 物品表下拉（药品、耗材固定两项）
     */
    @Override
    public List<OptionDto> toOptionDtoListPurchaseItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 将选品分页请求转为产品定义读模型查询。
     *
     * @param request 选品分页请求
     * @return 读模型查询对象
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForPurchaseRequest request) {
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(request.getProductTableName());
        query.setCategoryCode(request.getCategoryCode());
        query.setActiveFlag(request.getActiveFlag());
        query.setSupplierId(request.getSupplierId());
        query.setManufacturer(request.getManufacturer());
        query.setIncludeInventory(request.getIncludeInventory());
        PageQueryBinder.bind(request, query);
        query.setSortField(request.getSortField());
        query.setSortOrder(request.getSortOrder());
        return query;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为订货列表行（同号一行，头字段不一致时抛 WHS_PO_102）。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页明细行
     * @return 聚合列表行
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_102} 同号头字段不一致
     */
    @Override
    public List<PurchaseOrderListRowDto> toPurchaseOrderListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<PurchaseOrderListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // —— 先按单号分组，便于按分页给出的单号顺序输出且每单只出一行聚合 DTO
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isEmpty(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            List<SupplyRequestModel> lines = byBusNo.get(busNo);
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
            sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
            SupplyRequestModel head = sortedByApply.get(0);
            // —— 同号多行应共享单据头：供应商/状态/目的药库不一致时直接失败，避免列表展示「假一致」
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getSupplierId(), line.getSupplierId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_PO_102, "同号订货单供应商头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_PO_102, "同号订货单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_PO_102, "同号订货单目的药库头字段不一致: " + busNo);
                }
            }
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDateTime maxApply = null;
            LocalDateTime maxOccurrence = null;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getTotalPrice() != null) {
                    totalAmount = totalAmount.add(line.getTotalPrice());
                }
                if (line.getApplyTime() != null) {
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
                }
                if (line.getOccurrenceTime() != null) {
                    if (maxOccurrence == null || line.getOccurrenceTime().isAfter(maxOccurrence)) {
                        maxOccurrence = line.getOccurrenceTime();
                    }
                }
            }
            LocalDateTime approvalTime = null;
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            // —— 审批时刻取主键升序下第一条非空：稳定且不依赖数据库返回行序
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            PurchaseOrderListRowDto dto = new PurchaseOrderListRowDto();
            dto.setBusNo(busNo);
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setStatusEnum(head.getStatusEnum());
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            dto.setApproverId(head.getApproverId());
            dto.setTotalAmount(totalAmount);
            dto.setApprovalTime(approvalTime);
            dto.setApplyTime(maxApply);
            dto.setOccurrenceTime(maxOccurrence);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用聚合列表行构建分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PagePurchaseOrdersResponse toPagePurchaseOrdersAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<PurchaseOrderListRowDto> records) {
        List<PurchaseOrderListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PagePurchaseOrdersResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将同号供应请求明细与产品读模型装配为订货单详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应（含 {@code rowVersion} 供修订乐观锁）
     */
    @Override
    public GetPurchaseOrderDetailResponse toGetPurchaseOrderDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetPurchaseOrderDetailResponse res = new GetPurchaseOrderDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        // —— 头字段取自第一行：同号多行共享头信息，与列表聚合口径一致。——
        SupplyRequestModel head = lines.get(0);
        res.setBusNo(head.getBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setSupplierId(head.getSupplierId());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(head.getSourceTypeEnum());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setPractitionerId(head.getPractitionerId());
        // —— rowVersion 供修订乐观锁带回；优先首行 version，与进货详情同口径
        res.setRowVersion(this.resolvePurchaseOrderDocumentRowVersion(lines, head));
        List<PurchaseOrderDetailLineDto> detailLines = new ArrayList<>();
        // —— 逐行映射写模型明细；产品名等读模型字段在 toDetailLine 内按需补齐
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 将供应请求明细行与产品读模型装配为订货单详情行 DTO。
     *
     * @param line          供应请求写模型行
     * @param productByKey  产品定义读模型 compositeKey 映射，可为 null
     * @return 详情行 DTO；读模型未命中时产品展示字段保持 null
     */
    private PurchaseOrderDetailLineDto toDetailLine(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        PurchaseOrderDetailLineDto dto = new PurchaseOrderDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setRemake(line.getRemake());
        // —— 有读模型命中时补齐产品名称；无映射则保持 null，由前端降级展示。——
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
            }
        }
        return dto;
    }

    /**
     * 将新建订货请求装配为领域 CreatePurchaseOrderCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public CreatePurchaseOrderCommand toCreatePurchaseOrderCommand(
        CreatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId) {
        CreatePurchaseOrderCommand command = new CreatePurchaseOrderCommand();
        command.setOperatorId(operatorId);
        List<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> items = new ArrayList<>();
        // —— 明细缺失：返回空项命令，由领域门控拒绝空单（装配层不抛业务异常）。——
        if (request.getLines() == null) {
            command.setItems(items);
            return command;
        }
        // —— 头级枚举：源位置类型为 null（订货单无来源库位）；目的类型由库位推导（B.10.8.1）。——
        String sourceTypeCode = null;
        String purposeTypeCode = this.requirePurposeSupplyRequestTypeCodeFromLocation(request.getPurposeLocationId());
        String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
        String typeCode = SupplyType.CABINET_PURCHASE_ORDER.getCode();
        for (PurchaseOrderLineDto line : request.getLines()) {
            if (line == null) {
                continue;
            }
            CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item = new CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand();
            // —— 新建首包：业务单号由领域批量取号写入，装配阶段 item 级 busNo 先保持 null。——
            item.setBusNo(null);
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setSourceTypeEnum(sourceTypeCode);
            item.setSourceLocationId(null);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(request.getPurposeLocationId());
            item.setSupplierId(request.getSupplierId());
            item.setOccurrenceTime(line.getOccurrenceTime());
            item.setPractitionerId(request.getPractitionerId());
            item.setReason(null);
            item.setRemake(line.getRemake());
            item.setPrice(line.getPrice());
            item.setTotalPrice(line.getTotalPrice());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将修订订货请求装配为领域 CreatePurchaseOrderCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     * @throws IllegalArgumentException {@code rowVersion} 超出整型范围
     */
    @Override
    public CreatePurchaseOrderCommand toCreatePurchaseOrderCommandForUpdate(
        UpdatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId) {
        CreatePurchaseOrderCommand command = new CreatePurchaseOrderCommand();
        command.setOperatorId(operatorId);
        Long rowVer = request.getRowVersion();
        if (rowVer != null) {
            if (rowVer > Integer.MAX_VALUE || rowVer < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("rowVersion 超出整型范围");
            }
            command.setExpectedRowVersionForRevision(rowVer.intValue());
        }
        List<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> items = new ArrayList<>();
        // —— 明细缺失：返回空项命令，由领域门控拒绝空单。——
        if (request.getLines() == null) {
            command.setItems(items);
            return command;
        }
        // —— 修订路径头字段解析：源位置类型为 null；目的类型由库位推导。——
        String sourceTypeCode = null;
        String purposeTypeCode = this.requirePurposeSupplyRequestTypeCodeFromLocation(request.getPurposeLocationId());
        String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
        String typeCode = SupplyType.CABINET_PURCHASE_ORDER.getCode();
        for (PurchaseOrderLineDto line : request.getLines()) {
            if (line == null) {
                continue;
            }
            CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand item = new CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand();
            // —— 修订仍走「同号全量替换」写路径：每行须携带业务单号，领域据此覆盖旧明细。——
            item.setBusNo(request.getBusNo());
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setSourceTypeEnum(sourceTypeCode);
            item.setSourceLocationId(null);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(request.getPurposeLocationId());
            item.setSupplierId(request.getSupplierId());
            item.setOccurrenceTime(line.getOccurrenceTime());
            item.setPractitionerId(request.getPractitionerId());
            item.setReason(null);
            item.setRemake(line.getRemake());
            item.setPrice(line.getPrice());
            item.setTotalPrice(line.getTotalPrice());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配新建订货成功响应。
     *
     * @param busNo 领域返回的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    @Override
    public CreatePurchaseOrderResponse toCreatePurchaseOrderResponse(String busNo) {
        CreatePurchaseOrderResponse res = new CreatePurchaseOrderResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    /**
     * 装配修订订货成功响应。
     *
     * @param busNo 同号修订后的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    @Override
    public UpdatePurchaseOrderResponse toUpdatePurchaseOrderResponse(String busNo) {
        UpdatePurchaseOrderResponse res = new UpdatePurchaseOrderResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    /**
     * 将提交审批请求装配为领域 {@link SubmitApprovalCommand}。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员（申请人）
     * @return 领域写命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitPurchaseOrderApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配提交审批成功响应。
     *
     * @param status 提交后头行状态，可为 null
     * @return 提交响应
     */
    @Override
    public SubmitPurchaseOrderApprovalResponse toSubmitPurchaseOrderApprovalResponse(SupplyStatus status) {
        SubmitPurchaseOrderApprovalResponse res = new SubmitPurchaseOrderApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (status != null) {
            res.setStatusEnum(status.getCode());
        }
        return res;
    }

    /**
     * 将撤回审批请求装配为领域 {@link WithdrawApprovalCommand}。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批成功响应。
     *
     * @param status 撤回后头行状态，可为 null
     * @return 撤回响应
     */
    @Override
    public WithdrawPurchaseOrderApprovalResponse toWithdrawPurchaseOrderApprovalResponse(SupplyStatus status) {
        WithdrawPurchaseOrderApprovalResponse res = new WithdrawPurchaseOrderApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (status != null) {
            res.setStatusEnum(status.getCode());
        }
        return res;
    }

    /**
     * 将同意请求装配为领域 {@link AgreeRequestCommand}（订货同意不含发放链）。
     *
     * @param request    同意请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreePurchaseOrderRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配同意订货成功响应。
     *
     * @return 同意响应（{@code success=true}）
     */
    @Override
    public AgreePurchaseOrderResponse toAgreePurchaseOrderResponse() {
        AgreePurchaseOrderResponse res = new AgreePurchaseOrderResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将驳回请求装配为领域 {@link RejectRequestCommand}。
     *
     * @param request    驳回请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectPurchaseOrderRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setChangeReasonCode(request.getRejectReason());
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回订货成功响应。
     *
     * @return 驳回响应（{@code success=true}）
     */
    @Override
    public RejectPurchaseOrderResponse toRejectPurchaseOrderResponse() {
        RejectPurchaseOrderResponse res = new RejectPurchaseOrderResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将删除请求装配为领域 {@link RemoveByBusNoCommand}。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePurchaseOrderRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除订货成功响应。
     *
     * @return 删除响应（{@code success=true}）
     */
    @Override
    public RemovePurchaseOrderResponse toRemovePurchaseOrderResponse() {
        RemovePurchaseOrderResponse res = new RemovePurchaseOrderResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     *
     * <p>使用 {@link PageProductsForPurchaseResponse}（继承 {@link PagedResult}）构建分页响应，
     * {@code content} 为选品行数据。</p>
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForPurchaseResponse toPageProductsForPurchaseResponse(Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            if (pr == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(new PageProductsForPurchaseResponse(), content, page);
    }

    /**
     * 将产品定义读模型装配为订货选品行 DTO（含 PURCHASE 进价解析）。
     *
     * @param pr 产品定义读模型
     * @return 选品行；{@code availableQuantity} 固定 null（订货不汇总库存）
     */
    private ProductDefinitionItemDto toProductDefinitionItemDto(ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setProductDefinitionId(pr.getProductDefinitionId());
        dto.setProductTableName(pr.getProductTableName());
        dto.setProductBusNo(pr.getProductBusNo());
        dto.setProductName(pr.getProductName());
        dto.setMerchandiseName(pr.getMerchandiseName());
        dto.setUnitCode(pr.getPurchaseUnitCode());
        dto.setMinUnitCode(pr.getSplitUnitCode());
        dto.setCategoryCode(pr.getCategoryCode());
        dto.setCategoryCodeDictText(pr.getCategoryCodeDictText());
        dto.setPrice(pr.getPrice());
        if (StringUtils.isNotEmpty(pr.getProductTableName()) && pr.getProductDefinitionId() != null) {
            dto.setPurchasePrice(chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                pr.getProductTableName().trim(), pr.getProductDefinitionId()));
        }
        dto.setManufacturer(pr.getManufacturer());
        dto.setTotalVolume(pr.getTotalVolume());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        dto.setAvailableQuantity(null);
        return dto;
    }

    /**
     * 订货单头「目的位置类型」：仅由 {@code purposeLocationId} 推导（前端不传类型枚举）。
     *
     * @param purposeLocationId 入库目的位置主键
     * @return {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}
     * @throws IllegalArgumentException 当 purposeLocationId 为 null 时
     */
    private String requirePurposeSupplyRequestTypeCodeFromLocation(Long purposeLocationId) {
        if (purposeLocationId == null) {
            throw new IllegalArgumentException("purposeLocationId 不能为空");
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
    }

    /**
     * 供应类别：空或空白时默认库存供应；否则按 {@link SupplyCategory#getCode()} 解析（B.10.8）。
     *
     * @param categoryCodeRaw 请求 {@code categoryEnum}，可 blank
     * @return 持久化 code
     * @throws IllegalArgumentException 非空白但码不在枚举白名单
     */
    private String resolveSupplyCategoryCode(String categoryCodeRaw) {
        if (StringUtils.isEmpty(categoryCodeRaw)) {
            // —— 与历史订货默认类别对齐，减少前端必传负担。——
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }

    /**
     * 供应请求头行排序：按 {@code apply_time} 降序，null 排最后（列表/详情取头行代表）。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return Comparator 约定比较结果
     */
    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        LocalDateTime ta = left.getApplyTime();
        LocalDateTime tb = right.getApplyTime();
        if (ta == null && tb == null) {
            return 0;
        }
        if (ta == null) {
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        return tb.compareTo(ta);
    }

    /**
     * 供应请求行 tie-break：按行主键升序，null 排最后（同 apply_time 时稳定排序）。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return Comparator 约定比较结果
     */
    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        Long ida = left.getId();
        Long idb = right.getId();
        if (ida == null && idb == null) {
            return 0;
        }
        if (ida == null) {
            return 1;
        }
        if (idb == null) {
            return -1;
        }
        return ida.compareTo(idb);
    }

    /**
     * 解析订货单文档级 rowVersion：优先头行 {@code version}，否则取任一行非空版本，仍空则 0。
     *
     * @param lines 同号全部明细
     * @param head  头行（可为 null）
     * @return 供详情/修订使用的乐观锁版本
     */
    private Integer resolvePurchaseOrderDocumentRowVersion(List<SupplyRequestModel> lines, SupplyRequestModel head) {
        if (head != null && head.getVersion() != null) {
            return head.getVersion();
        }
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line != null && line.getVersion() != null) {
                    return line.getVersion();
                }
            }
        }
        return Integer.valueOf(0);
    }
}