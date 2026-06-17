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

package com.openhis.health.application.inventory.warehouse.purchaseorder.mapper;

import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseDocumentDetailRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto;
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
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 药库订货单装配实现：{@link InventoryWarehousePurchaseOrderMapper}。
 *
 * <p>列表/详情/命令/响应装配与选品分页映射；B.10.8.1 / B.10.11 口径以接口 JavaDoc 为准。</p>
 *
 * <p>各 {@code @Override} 方法须在 JavaDoc 中与接口保持<strong>同义完整说明</strong>（可补充实现细节与体内 {@code //} 呼应），
 * 禁止仅用单独一行 {@code @inheritDoc} 作为方法说明占位（B.1）。</p>
 */
@Component
public class InventoryWarehousePurchaseOrderMapperImpl implements InventoryWarehousePurchaseOrderMapper {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    private final LocationQueryService locationQueryService;

    public InventoryWarehousePurchaseOrderMapperImpl(
        ChargeItemDefinitionQueryService chargeItemDefinitionQueryService,
        LocationQueryService locationQueryService) {
        this.chargeItemDefinitionQueryService = chargeItemDefinitionQueryService;
        this.locationQueryService = locationQueryService;
    }

    /** 将启用药库位置列表映射为下拉选项 DTO。 */
    @Override
    public List<WarehouseOptionDto> toWarehouseOptionDtoList(List<Location> locations) {
        List<WarehouseOptionDto> list = new ArrayList<>();
        // —— 空输入返回空列表，避免前端判 null。——
        if (locations == null) {
            return list;
        }
        for (Location location : locations) {
            if (location == null) {
                continue;
            }
            WarehouseOptionDto dto = new WarehouseOptionDto();
            dto.setLocationId(location.getId());
            dto.setDisplayName(location.getName());
            dto.setActive(Boolean.TRUE);
            list.add(dto);
        }
        return list;
    }

    /** 将启用供应商列表映射为下拉选项 DTO。 */
    @Override
    public List<SupplierOptionDto> toSupplierOptionDtoList(List<SupplierListItemQueryModel> suppliers) {
        List<SupplierOptionDto> list = new ArrayList<>();
        // —— 空输入返回空列表，与库房下拉策略一致。——
        if (suppliers == null) {
            return list;
        }
        for (SupplierListItemQueryModel row : suppliers) {
            if (row == null) {
                continue;
            }
            SupplierOptionDto dto = new SupplierOptionDto();
            dto.setSupplierId(row.getId());
            dto.setSupplierName(row.getName());
            // —— 供应商类型枚举转展示文本；无法识别时回落原码，避免信息丢失。——
            SupplierType st = EnumUtil.fromCode(SupplierType.class, row.getTypeEnum());
            if (st != null) {
                dto.setSupplierType(st.getDisplay());
            } else {
                dto.setSupplierType(row.getTypeEnum());
            }
            list.add(dto);
        }
        return list;
    }

    /** 装配订货页初始化响应（药库/供应商选项挂接）。 */
    @Override
    public LoadPurchaseOrderFormInitResponse toLoadPurchaseOrderFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadPurchaseOrderFormInitResponse res = new LoadPurchaseOrderFormInitResponse();
        // —— 初始化响应仅做字段挂接，不含业务分支；各集合由 AppService 预收窄。——
        res.setWarehouseOptions(warehouseOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /**
     *
     * <p>使用 {@link PagePurchaseOrdersResponse}（继承 {@link PagedResult}）构建分页响应，
     * {@code content} 为列表行数据，{@code totalCount} 为总条数，{@code pageNo} 为 1-based。</p>
     *
     * @param page 库房单据读模型分页
     * @return 订货单分页响应；行内枚举语义为持久化 code（B.10.8.1）
     */
    @Override
    public PagePurchaseOrdersResponse toPagePurchaseOrdersResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<WarehouseDocumentDetailRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toWarehouseDocumentDetailRowDto(row));
        }
        return PagedResultBuilder.build(new PagePurchaseOrdersResponse(), content, page);
    }

    /** 将库房单据读模型行装配为列表行 DTO（枚举转 sortOrder 供前端排序）。 */
    private WarehouseDocumentDetailRowDto toWarehouseDocumentDetailRowDto(WarehouseDocumentDetailQueryModel row) {
        WarehouseDocumentDetailRowDto dto = new WarehouseDocumentDetailRowDto();
        dto.setId(row.getId());
        dto.setBusNo(row.getBusNo());
        // —— 枚举对外转 sortOrder 整型，与前端表格排序口径一致（B.10.8.1 扩展）。——
        dto.setTypeEnum(this.toApiSortSupplyType(row.getTypeEnum()));
        dto.setStatusEnum(this.toApiSortSupplyStatus(row.getStatusEnum()));
        dto.setSourceLocationId(row.getSourceLocationId());
        dto.setPurposeLocationId(row.getPurposeLocationId());
        dto.setSupplierId(row.getSupplierId());
        dto.setItemId(row.getItemId());
        dto.setItemTable(row.getItemTable());
        dto.setItemQuantity(row.getItemQuantity());
        dto.setUnitCode(row.getUnitCode());
        dto.setOccurrenceTime(row.getOccurrenceTime());
        dto.setApplyTime(row.getApplyTime());
        dto.setApprovalTime(row.getApprovalTime());
        dto.setTotalPrice(row.getTotalPrice());
        return dto;
    }

    /** 将同号供应请求明细与产品读模型装配为订货单详情响应。 */
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
        res.setTypeEnum(this.toApiSortSupplyType(head.getTypeEnum()));
        res.setStatusEnum(this.toApiSortSupplyStatus(head.getStatusEnum()));
        res.setSupplierId(head.getSupplierId());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(head.getSourceTypeEnum());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        List<PurchaseOrderDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    /** 将供应请求明细行与产品读模型装配为订货单详情行 DTO。 */
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
            }
        }
        return dto;
    }

    /** 将新建订货请求装配为领域 CreatePurchaseOrderCommand。 */
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

    /** 将修订订货请求装配为领域 CreatePurchaseOrderCommand（同号全量对齐）。 */
    @Override
    public CreatePurchaseOrderCommand toCreatePurchaseOrderCommandForUpdate(
        UpdatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId) {
        CreatePurchaseOrderCommand command = new CreatePurchaseOrderCommand();
        command.setOperatorId(operatorId);
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

    /** 装配新建订货成功响应（含 busNo 与待审批状态）。 */
    @Override
    public CreatePurchaseOrderResponse toCreatePurchaseOrderResponse(String busNo) {
        CreatePurchaseOrderResponse res = new CreatePurchaseOrderResponse();
        res.setBusNo(busNo);
        // —— 新建成功默认定为待审批状态，返回 sortOrder 整型与前端表格排序口径一致。——
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 装配修订订货成功响应（含 busNo 与待审批状态）。 */
    @Override
    public UpdatePurchaseOrderResponse toUpdatePurchaseOrderResponse(String busNo) {
        UpdatePurchaseOrderResponse res = new UpdatePurchaseOrderResponse();
        res.setBusNo(busNo);
        // —— 修订成功回退至待审批状态，返回 sortOrder 整型。——
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 将提交审批请求装配为领域 SubmitApprovalCommand。 */
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

    /** 装配提交审批成功响应。 */
    @Override
    public SubmitPurchaseOrderApprovalResponse toSubmitPurchaseOrderApprovalResponse() {
        SubmitPurchaseOrderApprovalResponse res = new SubmitPurchaseOrderApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 提交成功后状态为已提交审批，返回 sortOrder 整型。——
        res.setStatusEnum(SupplyStatus.APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 将撤回审批请求装配为领域 WithdrawApprovalCommand。 */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配撤回审批成功响应。 */
    @Override
    public WithdrawPurchaseOrderApprovalResponse toWithdrawPurchaseOrderApprovalResponse() {
        WithdrawPurchaseOrderApprovalResponse res = new WithdrawPurchaseOrderApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 撤回成功后状态为已撤回，返回 sortOrder 整型。——
        res.setStatusEnum(SupplyStatus.WITHDRAW.getSortOrder().intValue());
        return res;
    }

    /** 将同意请求装配为领域 AgreeRequestCommand。 */
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

    /** 装配同意订货成功响应。 */
    @Override
    public AgreePurchaseOrderResponse toAgreePurchaseOrderResponse() {
        AgreePurchaseOrderResponse res = new AgreePurchaseOrderResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /** 将驳回请求装配为领域 RejectRequestCommand。 */
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

    /** 装配驳回订货成功响应。 */
    @Override
    public RejectPurchaseOrderResponse toRejectPurchaseOrderResponse() {
        RejectPurchaseOrderResponse res = new RejectPurchaseOrderResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /** 将删除请求装配为领域 RemoveByBusNoCommand。 */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePurchaseOrderRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配删除订货成功响应。 */
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

    /** 将产品定义读模型装配为订货选品行 DTO（含采购价解析）。 */
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
        if (StringUtils.isNotBlank(pr.getProductTableName()) && pr.getProductDefinitionId() != null) {
            dto.setPurchasePrice(chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                pr.getProductTableName().trim(), pr.getProductDefinitionId()));
        }
        dto.setManufacturer(pr.getManufacturer());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        return dto;
    }

    /**
     * 供应类型枚举 code 转前端排序用 sortOrder 整型（B.10.8.1 扩展）。
     *
     * @param code 持久化 code
     * @return sortOrder 整型；无法识别时返回 null
     */
    private Integer toApiSortSupplyType(String code) {
        SupplyType t = EnumUtil.fromCode(SupplyType.class, code);
        if (t == null) {
            return null;
        }
        return t.getSortOrder().intValue();
    }

    /**
     * 供应状态枚举 code 转前端排序用 sortOrder 整型（B.10.8.1 扩展）。
     *
     * @param code 持久化 code
     * @return sortOrder 整型；无法识别时返回 null
     */
    private Integer toApiSortSupplyStatus(String code) {
        SupplyStatus s = EnumUtil.fromCode(SupplyStatus.class, code);
        if (s == null) {
            return null;
        }
        return s.getSortOrder().intValue();
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
        if (StringUtils.isBlank(categoryCodeRaw)) {
            // —— 与历史订货默认类别对齐，减少前端必传负担。——
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }
}
