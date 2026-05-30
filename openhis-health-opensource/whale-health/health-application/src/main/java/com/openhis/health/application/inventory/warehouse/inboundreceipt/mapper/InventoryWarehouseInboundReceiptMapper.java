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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.AgreeInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.CreateInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.PageProductsForInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.RejectInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.RemoveInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.SubmitInboundReceiptApprovalRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.UpdateInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.WithdrawInboundReceiptApprovalRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 药库进货（入库）单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p><strong>实现</strong>：{@link InventoryWarehouseInboundReceiptMapperImpl}；接口与实现的方法 JavaDoc 须对齐，
 * 禁止在实现类中用单独的 inheritDoc JavaDoc 标签充当方法完整说明（B.1）。</p>
 *
 * <ul>
 *       {@link #toGetInboundReceiptDetailResponse}；</li>
 *       {@link #toPageLinkablePurchaseOrdersResponse}；</li>
 * </ul>
 *
 * <p><strong>B.10.8.1</strong>：写入领域命令或 HTTP 响应中凡表示状态/类型/类别/字典语义且类型为 {@link String} 的字段，
 * 一律使用各业务枚举或字典的 {@link EnumInterface#getCode()} 持久化字面量，经 {@link EnumUtil} 或显式常量归一；
 * 禁止排序号、整型与数字串冒充后再映射。</p>
 *
 * <p><strong>B.10.11</strong>：凡装配分页响应体时，{@code pageNo} 为 <strong>1-based</strong>，与 {@link AbstractPageQuery} 入参一致。</p>
 *
 * <p>方法级 JavaDoc 须含可审查的 {@code @param}/{@code @return}/{@code @throws} 实质说明，忌空泛复述方法签名（B.1）。</p>
 */
public interface InventoryWarehouseInboundReceiptMapper {

    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    List<OptionDto> toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel> suppliers);

    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    List<OptionDto> toOptionDtoListSupplyStatuses();

    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    LoadInboundReceiptFormInitResponse toLoadInboundReceiptFormInitResponse(
        List<OptionDto> pharmacyLocationOptions,
        List<OptionDto> warehouseLocationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> pharmacistOptions,
        List<OptionDto> warehouseKeeperOptions,
        List<OptionDto> inboundItemTableTypeOptions,
        List<OptionDto> statusOptions);

    /**
     * 供应商/人员展示名由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService}
     * 在返回前批量富化（B.2 分层）。
     *
     * @param busNoPageOrder 当前页单号顺序（与领域 {@code GROUP BY bus_no} 分页一致）
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合行列表
     * @throws com.openhis.whale.core.application.exception.BusinessException 同号多行在供应商或状态等「头字段」上不一致，
     *                                   装配层直接失败（常见错误码 {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#WHS_IN_102}）
     */
    List<InboundReceiptListRowDto> toInboundReceiptListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    PageInboundReceiptsResponse toPageInboundReceiptsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<InboundReceiptListRowDto> records);

    /**
     * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse}）。
     *
     * <p>头字段取自 {@code lines} 的首行（与领域同号多行共享头语义）；{@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse#getRowVersion()}
     * 由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapperImpl}
     * 内 {@code resolveInboundReceiptDocumentRowVersion} 解析。明细行通过 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto}
     * 下发；产品展示字段仅在 {@code productByKey} 命中复合键时填充。</p>
     *
     * @param lines         同号多行写模型查询结果；{@code null} 或空时返回空壳响应（各字段默认 null/空列表）
     * @param productByKey  产品复合键（{@code itemTable + itemId}，与 {@link com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil#buildCompositeKey} 一致）到展示 DTO 的映射；
     *                      可 {@code null}，则不下发 {@code productName}/{@code specification}/{@code manufacturer}
     * @return 详情响应；头/行上 {@code *Enum} 类字段均为持久化 {@code getCode()} 字符串（B.10.8.1）
     */
    GetInboundReceiptDetailResponse toGetInboundReceiptDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     *
     * <p>供应头 {@code source_type_enum}/{@code purpose_type_enum} 由装配层按 {@code sourceLocationId}/{@code purposeLocationId} 与
     * {@code adm_location.form_enum} 推导为 {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}，请求体可不传；
     * {@code categoryEnum} 为 {@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 字符串，做 {@code trim} 与
     * {@link EnumUtil} 校验或默认解析。</p>
     *
     * @param request     创建请求（{@code lines} 由应用层先行非空校验）
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键（进货场景通常与操作人一致）
     * @return 领域创建命令；各 item 的 {@code typeEnum} 固定为药库进货类型 code；草稿阶段各 item 的 {@code applyTime} 固定为 null（申请时间在提交审批时由域层写入）
     * @throws IllegalArgumentException 显式非法枚举、或 {@code categoryEnum} 非空但非法（经 {@link EnumUtil}）；位置类型无法由库位推导时
     */
    CreateInboundReceiptCommand toCreateInboundReceiptCommand(
        CreateInboundReceiptRequest request,
        Long operatorId,
        Long applicantId);

    /**
     *
     * <p>枚举字段解析规则同 {@link #toCreateInboundReceiptCommand}；另将 {@link UpdateInboundReceiptRequest#getRowVersion()}
     * 写入命令期望版本（须在 {@code int} 范围内）。</p>
     *
     * @param request               修订请求（含乐观锁版本）
     * @param operatorId            操作人主键
     * @param applicantId           申请人主键
     * @param originalBusNoSnapshot 修订前首行携带的来源订货单号快照
     * @return 领域创建命令（修订语义与新建共用写路径）；草稿阶段各 item 的 {@code applyTime} 为 null
     * @throws IllegalArgumentException code 非法或 {@code rowVersion} 超出 {@code int} 可表示范围
     */
    CreateInboundReceiptCommand toCreateInboundReceiptCommandForUpdate(
        UpdateInboundReceiptRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    CreateInboundReceiptResponse toCreateInboundReceiptResponse(String busNo);

    UpdateInboundReceiptResponse toUpdateInboundReceiptResponse(String busNo);

    SubmitApprovalCommand toSubmitApprovalCommand(SubmitInboundReceiptApprovalRequest request, Long operatorId, Long applicantId);

    SubmitInboundReceiptApprovalResponse toSubmitInboundReceiptApprovalResponse(SupplyStatus statusAfter);

    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawInboundReceiptApprovalRequest request, Long operatorId);

    WithdrawInboundReceiptApprovalResponse toWithdrawInboundReceiptApprovalResponse(SupplyStatus statusAfter);

    AgreeRequestCommand toAgreeRequestCommand(AgreeInboundReceiptRequest request, Long operatorId);

    AgreeInboundReceiptResponse toAgreeInboundReceiptResponse(boolean ok);

    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForInbound(List<SupplyRequestModel> lines, Long operatorId);

    CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String resolvedProductName,
        String resolvedLotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto productDefinition);

    /**
     * 同单参数 {@link #toCreateInventoryItemCommand(SupplyRequestModel, Long, String, String, BigDecimal, String, Long, ProductDefinitionDisplayDto)}，
     * 显式指定新建库存项 {@code supplier_id}（出库同意链目的库首次建账从源库行承袭时使用；{@code null} 时回落为行上供应商）。
     */
    CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String resolvedProductName,
        String resolvedLotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto productDefinition,
        Long inventoryItemSupplierId);

    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    RejectRequestCommand toRejectRequestCommand(RejectInboundReceiptRequest request, Long operatorId);

    RejectInboundReceiptResponse toRejectInboundReceiptResponse(boolean ok);

    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveInboundReceiptRequest request, Long operatorId);

    RemoveInboundReceiptResponse toRemoveInboundReceiptResponse(boolean ok);

    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForInboundReceiptRequest request);

    PageProductsForInboundReceiptResponse toPageProductsForInboundReceiptResponse(Page<ProductDefinitionDisplayDto> page);

    PageLinkablePurchaseOrdersResponse toPageLinkablePurchaseOrdersResponse(Page<WarehouseDocumentDetailQueryModel> page);
}
