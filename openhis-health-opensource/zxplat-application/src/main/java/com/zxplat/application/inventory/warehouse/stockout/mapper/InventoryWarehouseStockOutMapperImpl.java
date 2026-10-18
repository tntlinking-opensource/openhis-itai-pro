/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.zxplat.application.inventory.warehouse.stockout.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import com.zxplat.application.inventory.warehouse.stockout.dto.StockOutLineDto;
import com.zxplat.application.inventory.warehouse.stockout.dto.StockOutListRowDto;
import com.zxplat.application.inventory.warehouse.stockout.request.AgreeStockOutRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.CreateStockOutRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.UpdateStockOutRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.RejectStockOutRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.RemoveStockOutRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.SubmitStockOutApprovalRequest;
import com.zxplat.application.inventory.warehouse.stockout.request.WithdrawStockOutApprovalRequest;
import com.zxplat.application.inventory.warehouse.stockout.response.AgreeStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.CreateStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.GetLinkablePharmacyRequisitionDetailResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.GetStockOutDetailResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.LoadStockOutFormInitResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.PageLinkableRequisitionsResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.PageProductsForStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.PageStockOutsResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.RejectStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.RemoveStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.SubmitStockOutApprovalResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.UpdateStockOutResponse;
import com.zxplat.application.inventory.warehouse.stockout.response.WithdrawStockOutApprovalResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.SupplyCategory;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 药库出库单装配实现：{@link InventoryWarehouseStockOutMapper}。
 *
 * <p>11 选品、12 可关联请领、08 同意后的发放与壳单命令。枚举持久化口径遵循 B.10.8.1；各 {@code @Override} 方法 JavaDoc 与接口保持同义完整说明（B.1），
 * 禁止仅以单独一行 {@code @inheritDoc} 作为方法说明占位。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehouseStockOutMapperImpl implements InventoryWarehouseStockOutMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为下拉选项（id/编码/展示名）。
     *
     * @param locations 启用库位列表，可为 null
     * @return 位置下拉选项，永不为 null
     */
    @Override
    public List<OptionDto> toOptionDtoListFromLocations(List<Location> locations) {
        List<OptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        // —— 下拉 value/label：缺名称时退化为 id 字符串，避免前端渲染空选项。——
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
     * 将医务人员列表映射为下拉选项。
     *
     * @param practitioners 医务人员列表，可为 null
     * @return 人员下拉选项，永不为 null
     */
    @Override
    public List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners) {
        List<OptionDto> list = new ArrayList<>();
        if (practitioners == null) {
            return list;
        }
        // —— 人员下拉与位置下拉同构：保证 OptionDto 三元组在列表/表单侧可复用同一渲染逻辑。——
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
     * @return 按 {@link SupplyStatus#getSortOrder()} 升序的选项列表
     */
    @Override
    public List<OptionDto> toOptionDtoListSupplyStatuses() {
        List<SupplyStatus> ordered = new ArrayList<>();
        // —— 下拉顺序与枚举 sortOrder 一致：避免前端硬编码状态排列。——
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
     * @return 两档物理表名下拉
     */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        // —— 与库存入账白名单严格一致：仅允许两物理表名作为持久化身份（B.10.8.1）。——
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 装配页初始化响应（选项集合写入各字段）。
     *
     * @param warehouseOpts  药库位置下拉
     * @param pharmacyOpts   药房位置下拉
     * @param inboundTypes   物品来源表类型
     * @param statusOpts     单据状态
     * @param pharmacistOpts 药师下拉
     * @param keeperOpts     库管员下拉
     * @return 初始化响应体
     */
    @Override
    public LoadStockOutFormInitResponse toLoadStockOutFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        // —— 纯 DTO 聚合：各下拉已在 AppService 门控后装配，此处不做任何业务裁剪。——
        LoadStockOutFormInitResponse res = new LoadStockOutFormInitResponse();
        res.setWarehouseLocationOptions(warehouseOpts);
        res.setPharmacyLocationOptions(pharmacyOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     同页明细读模型行
     * @return 聚合列表行
     */
    @Override
    public List<StockOutListRowDto> toStockOutListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<StockOutListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // —— 先按 bus_no 分组：一页内可能含多单多行，后续按单号顺序输出聚合头。——
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
            // —— 头行口径：同号多行取「申请时刻最新」的一条作为展示头，但头字段须与全行一致（否则列表不可信）。——
            List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
            sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
            SupplyRequestModel head = sortedByApply.get(0);
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号出库单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号出库单源库房头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号出库单目的库房头字段不一致: " + busNo);
                }
            }
            // —— 列表「申请时间」展示为同号各行申请时刻的最大值；金额与进货列表同构为各行 total_price 之和。——
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
            // —— 审批时间：取 id 最小且带审批时刻的首行，保证同号多行下展示口径稳定、可复现。——
            LocalDateTime approvalTime = null;
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            StockOutListRowDto dto = new StockOutListRowDto();
            dto.setBusNo(busNo);
            dto.setTypeEnum(SupplyType.CABINET_STOCK_OUT.getCode());
            dto.setStatusEnum(head.getStatusEnum());
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setApproverId(head.getApproverId());
            dto.setApplicantId(head.getApplicantId());
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setApprovalTime(approvalTime);
            dto.setApplyTime(maxApply);
            dto.setOccurrenceTime(maxOccurrence);
            dto.setTotalAmount(totalAmount);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用聚合列表行构建分页响应。
     *
     * @param mergedTotal 合并后总条数
     * @param pageNo      页码（从 1 开始）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PageStockOutsResponse toPageStockOutsAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<StockOutListRowDto> records) {
        // —— records 永不为 null：避免前端列表组件对 null 与空数组分支分叉。——
        List<StockOutListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageStockOutsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为详情响应（含明细增强字段）。
     *
     * @param lines        同号全部明细行
     * @param productHints 行 id → 产品目录提示
     * @return 详情响应
     */
    @Override
    public GetStockOutDetailResponse toGetStockOutDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetStockOutDetailResponse res = new GetStockOutDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        // —— 详情头：与列表聚合一致，按申请时刻降序取首行作为头字段来源。——
        List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
        sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
        SupplyRequestModel head = sortedByApply.get(0);
        res.setBusNo(head.getBusNo());
        res.setOriginalBusNo(head.getOriginalBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(head.getSourceTypeEnum());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        List<StockOutDetailLineDto> detailLines = new ArrayList<>();
        // —— 明细行顺序与查询返回一致（不在此重排）；头字段单独按申请时刻降序稳定选取，与列表聚合口径一致。——
        for (SupplyRequestModel line : lines) {
            StockOutDetailLineDto dto = this.toStockOutDetailLineDto(line);
            if (dto != null && productHints != null && line.getId() != null) {
                ProductDefinitionCatalogEditorHintsDto hint = productHints.get(line.getId());
                if (hint != null) {
                    dto.setProductCatalog(hint);
                }
            }
            if (dto != null) {
                detailLines.add(dto);
            }
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 单条供应请求行映射为详情明细 DTO。
     *
     * @param line 供应请求读模型行
     * @return 详情明细 DTO；{@code line} 为 null 时返回 null
     */
    @Override
    public StockOutDetailLineDto toStockOutDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            // —— 上游若混入 null 行则跳过装配；不抛异常以免掩盖批量查询根因（由 AppService 保证主路径无 null）。——
            return null;
        }
        StockOutDetailLineDto dto = new StockOutDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setStartTime(line.getStartTime());
        dto.setEndTime(line.getEndTime());
        dto.setTraceNo(line.getTraceNo());
        dto.setRemake(line.getRemake());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setProductName(null);
        dto.setSpecification(null);
        dto.setManufacturer(null);
        dto.setUnitCodeDictText(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /**
     * 将新建请求映射为领域 Create 命令（含操作人与申请人）。
     *
     * @param request     新建请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @param busNo       业务单号（修订时非空）
     * @return 领域创建命令
     */
    @Override
    public CreateStockOutCommand toCreateStockOutCommand(
        CreateStockOutRequest request,
        Long operatorId,
        Long applicantId,
        String busNo) {
        CreateStockOutCommand command = new CreateStockOutCommand();
        command.setOperatorId(operatorId);
        List<CreateStockOutCommand.CreateStockOutItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            // —— 头级枚举：源/目的位置类型由库位 id 推导（B.10.8.1），请求体不传类型枚举。——
            String sourceTypeCode = this.resolveSourceTypeEnumForStockOutHeader(request.getSourceLocationId());
            String purposeTypeCode = this.resolvePurposeTypeEnumForStockOutHeader(request.getPurposeLocationId());
            String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
            String typeCode = SupplyType.CABINET_STOCK_OUT.getCode();
            String original = request.getOriginalBusNo();
            if (original != null) {
                original = original.trim();
                if (original.isEmpty()) {
                    original = null;
                }
            }
            for (StockOutLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                // —— 每行一条 CreateStockOutItem：修订时 busNo 非空，新建时由领域取号后再回填行级单号语义由领域统一处理。——
                CreateStockOutCommand.CreateStockOutItemCommand item = new CreateStockOutCommand.CreateStockOutItemCommand();
                item.setBusNo(busNo);
                item.setOriginalBusNo(original);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setSourceTypeEnum(sourceTypeCode);
                item.setSourceLocationId(request.getSourceLocationId());
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setReason(null);
                item.setRemake(line.getRemake());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getTotalPrice());
                item.setApplicantId(applicantId);
                item.setPractitionerId(request.getPractitionerId());
                item.setApplyTime(null);
                items.add(item);
            }
        }
        // —— 无明细时 items 为空列表：是否允许空单由领域门禁；此处仅忠实搬运请求。——
        command.setItems(items);
        return command;
    }

    /**
     * 修订出库单：将 {@link UpdateStockOutRequest} 塌缩为 {@link CreateStockOutRequest} 形态后委托
     *
     * @param request     修订请求，不得为 null
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域创建命令（携带既有 {@code busNo}）
     * @throws IllegalArgumentException {@code request} 为 null，或嵌套装配抛出非法参数
     */
    @Override
    public CreateStockOutCommand toCreateStockOutCommand(
        UpdateStockOutRequest request,
        Long operatorId,
        Long applicantId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        // —— 修订与新建共用装配器：避免两套字段拷贝分叉，降低与 SDD 不一致风险。——
        CreateStockOutRequest synthetic = new CreateStockOutRequest();
        synthetic.setSourceLocationId(request.getSourceLocationId());
        synthetic.setPurposeLocationId(request.getPurposeLocationId());
        synthetic.setCategoryEnum(request.getCategoryEnum());
        synthetic.setOriginalBusNo(request.getOriginalBusNo());
        synthetic.setPractitionerId(request.getPractitionerId());
        synthetic.setLines(request.getLines());
        String busNo = null;
        if (request.getBusNo() != null) {
            busNo = request.getBusNo().trim();
        }
        if (busNo != null && busNo.isEmpty()) {
            busNo = null;
        }
        return this.toCreateStockOutCommand(synthetic, operatorId, applicantId, busNo);
    }

    /**
     * 装配创建成功响应。
     *
     * @param busNo 业务单号
     * @return 创建响应
     */
    @Override
    public CreateStockOutResponse toCreateStockOutResponse(String busNo) {
        CreateStockOutResponse res = new CreateStockOutResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配更新成功响应。
     *
     * @param busNo 业务单号
     * @return 更新响应
     */
    @Override
    public UpdateStockOutResponse toUpdateStockOutResponse(String busNo) {
        UpdateStockOutResponse res = new UpdateStockOutResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 将提交审批请求映射为 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域提交命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitStockOutApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配提交审批响应。
     *
     * @param statusAfter 提交后状态枚举
     * @return 提交审批响应
     */
    @Override
    public SubmitStockOutApprovalResponse toSubmitStockOutApprovalResponse(SupplyStatus statusAfter) {
        SubmitStockOutApprovalResponse res = new SubmitStockOutApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // —— 对外状态一律持久化 getCode() 字符串（B.10.8.1）。——
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将撤回审批请求映射为 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人主键
     * @return 领域撤回命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawStockOutApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        // —— 撤回仅依赖单号 + 操作人；无额外时钟字段，避免与提交路径语义分叉。——
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批响应。
     *
     * @param statusAfter 撤回后状态枚举
     * @return 撤回审批响应
     */
    @Override
    public WithdrawStockOutApprovalResponse toWithdrawStockOutApprovalResponse(SupplyStatus statusAfter) {
        WithdrawStockOutApprovalResponse res = new WithdrawStockOutApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // —— 对外状态一律持久化 getCode() 字符串（B.10.8.1）。——
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将同意请求映射为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人/审批人主键
     * @return 领域同意命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeStockOutRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        // —— 同意项携带审批时刻与审批人主键，供领域 agreeRequest 落状态与审计列。——
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意供应请求行装配为出库发放命令。
     *
     * @param lines      已同意明细行
     * @param operatorId 操作人主键
     * @return 发放命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForStockOut(List<SupplyRequestModel> lines, Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            // —— 发放明细一行对应一条已持久化的供应请求：requestId 为后续库存与追溯编排的外键锚点。——
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.CABINET_STOCK_OUT.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setLotNumber(line.getLotNumber());
                item.setOccurrenceTime(line.getOccurrenceTime());
                items.add(item);
            }
        }
        cmd.setItems(items);
        return cmd;
    }

    /**
     * 将已同意出库行装配为对侧入库壳单 CreateRequisition 命令。
     *
     * @param agreedOutLines      已同意出库明细行
     * @param outboundBusNo       出库业务单号（写入 original_bus_no）
     * @param operatorId          操作人主键
     * @param applicantId         申请人主键
     * @param inboundTypeEnumCode 壳单供应类型持久化码（药房入库或科室入库）
     * @return 壳单创建命令（引导态 AGREE）
     */
    @Override
    public CreateRequisitionCommand toCreateRequisitionCommandForShellInboundStockIn(
        List<SupplyRequestModel> agreedOutLines,
        String outboundBusNo,
        Long operatorId,
        Long applicantId,
        String inboundTypeEnumCode) {
        CreateRequisitionCommand command = new CreateRequisitionCommand();
        command.setOperatorId(operatorId);
        command.setBootstrapFinalStatusCode(SupplyStatus.AGREE.getCode());
        List<CreateRequisitionCommand.CreateRequisitionItemCommand> items = new ArrayList<>();
        String obn = "";
        if (outboundBusNo != null) {
            obn = outboundBusNo.trim();
        }
        String shellTypeCode = inboundTypeEnumCode;
        if (StringUtils.isEmpty(shellTypeCode)) {
            shellTypeCode = SupplyType.PHARMACY_STOCK_IN.getCode();
        } else {
            shellTypeCode = shellTypeCode.trim();
        }
        // 壳单头字段沿用出库：制单人/申请时刻/审批人/审批时刻；缺省时 apply_time 回落 now（列表时刻窗谓词）。
        LocalDateTime shellApplyTime = LocalDateTime.now();
        Long shellApproverId = null;
        LocalDateTime shellApprovalTime = null;
        if (agreedOutLines != null) {
            for (SupplyRequestModel headLine : agreedOutLines) {
                if (headLine == null) {
                    continue;
                }
                if (headLine.getApplyTime() != null) {
                    shellApplyTime = headLine.getApplyTime();
                }
                if (headLine.getApproverId() != null) {
                    shellApproverId = headLine.getApproverId();
                }
                if (headLine.getApprovalTime() != null) {
                    shellApprovalTime = headLine.getApprovalTime();
                }
                break;
            }
        }
        if (agreedOutLines != null) {
            // —— 壳单：行类型由目的库位形态决定；original_bus_no 绑定出库单号；制单/审批人与出库头一致。——
            for (SupplyRequestModel line : agreedOutLines) {
                if (line == null) {
                    continue;
                }
                CreateRequisitionCommand.CreateRequisitionItemCommand item = new CreateRequisitionCommand.CreateRequisitionItemCommand();
                item.setBusNo(null);
                item.setOriginalBusNo(obn);
                item.setTypeEnum(shellTypeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(line.getCategoryEnum());
                item.setSourceTypeEnum(line.getSourceTypeEnum());
                item.setSourceLocationId(line.getSourceLocationId());
                item.setPurposeTypeEnum(line.getPurposeTypeEnum());
                item.setPurposeLocationId(line.getPurposeLocationId());
                item.setSupplierId(line.getSupplierId());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getTotalPrice());
                item.setOccurrenceTime(line.getOccurrenceTime());
                // 制单人：优先出库行申请人，缺省用调用方回落的医务人员主键
                Long lineApplicantId = line.getApplicantId();
                if (lineApplicantId != null) {
                    item.setApplicantId(lineApplicantId);
                } else {
                    item.setApplicantId(applicantId);
                }
                item.setApplyTime(shellApplyTime);
                item.setApproverId(shellApproverId);
                item.setApprovalTime(shellApprovalTime);
                item.setReason(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将已同意出库行装配为药房入库壳单 CreateRequisition 命令。
     *
     * @param agreedOutLines 已同意出库明细行
     * @param outboundBusNo  出库业务单号（写入 original_bus_no）
     * @param operatorId     操作人主键
     * @param applicantId    申请人主键
     * @return 壳单创建命令（引导态 AGREE）
     */
    @Override
    public CreateRequisitionCommand toCreateRequisitionCommandForShellPharmacyStockIn(
        List<SupplyRequestModel> agreedOutLines,
        String outboundBusNo,
        Long operatorId,
        Long applicantId) {
        return this.toCreateRequisitionCommandForShellInboundStockIn(
            agreedOutLines,
            outboundBusNo,
            operatorId,
            applicantId,
            SupplyType.PHARMACY_STOCK_IN.getCode());
    }

    /**
     * 装配同意成功响应。
     *
     * @param outboundBusNo      出库业务单号
     * @param statusEnum         同意后状态持久化码
     * @param generatedInBusNo   系统生成的药房入库壳单号
     * @return 同意响应
     */
    @Override
    public AgreeStockOutResponse toAgreeStockOutResponse(String outboundBusNo, String statusEnum, String generatedInBusNo) {
        AgreeStockOutResponse res = new AgreeStockOutResponse();
        res.setBusNo(outboundBusNo);
        res.setStatusEnum(statusEnum);
        res.setGeneratedPharmacyStockInBusNo(generatedInBusNo);
        return res;
    }

    /**
     *
     * <p>合并原因写入领域项：{@code changeReason} 非空白时取其展示文案，否则回落 {@code changeReasonCode}，保证审计与通知侧优先「人读串」。</p>
     *
     * @param request    驳回请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectStockOutRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        // —— 驳回原因：优先落展示文案，缺省时再回落到原因码，便于审计与通知模板统一取「人读串」。——
        String mergedReason;
        if (StringUtils.isEmpty(request.getChangeReason())) {
            mergedReason = request.getChangeReasonCode();
        } else {
            mergedReason = request.getChangeReason();
        }
        item.setChangeReasonCode(mergedReason);
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回响应。
     *
     * @param ok 是否成功
     * @return 驳回响应
     */
    @Override
    public RejectStockOutResponse toRejectStockOutResponse(boolean ok) {
        RejectStockOutResponse res = new RejectStockOutResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将删除请求映射为 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人主键
     * @return 领域删除命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveStockOutRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除响应。
     *
     * @param ok 是否成功
     * @return 删除响应
     */
    @Override
    public RemoveStockOutResponse toRemoveStockOutResponse(boolean ok) {
        RemoveStockOutResponse res = new RemoveStockOutResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     *
     * <p>使用 {@link PagedResult} 统一构建分页响应，{@code content} 为选品行数据。</p>
     *
     * @param page 库存项读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForStockOutResponse toPageProductsForStockOutResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                // —— 分页 content 偶发 null 占位时跳过，避免 NPE 污染整页响应。——
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForStockOutResponse(), content, page);
    }

    /**
     * 按 busNo 顺序聚合请领明细为可关联选单行（一行一单；金额为同行 totalPrice 之和）。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     同页请领明细读模型行
     * @return 聚合选单行
     */
    @Override
    public List<WarehouseDocumentDetailRowDto> toLinkableRequisitionRows(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<WarehouseDocumentDetailRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // —— 先按 bus_no 分组：一页内可能含多单多行，后续按单号顺序输出聚合头。——
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
            // —— 头行口径：同号多行取「申请时刻最新」者为展示头，头字段须与全行一致。——
            List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
            sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
            SupplyRequestModel head = sortedByApply.get(0);
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号请领单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号请领单源库房头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号请领单目的库房头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getTypeEnum(), line.getTypeEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_102, "同号请领单类型头字段不一致: " + busNo);
                }
            }
            // —— 列表金额与出库/进货选单同构：各行 total_price 之和（行级 6 位刻度加总，展示层再取 2 位）。——
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDateTime maxApply = null;
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
            }
            LocalDateTime approvalTime = null;
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            WarehouseDocumentDetailRowDto dto = new WarehouseDocumentDetailRowDto();
            dto.setBusNo(busNo);
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setTypeEnum(head.getTypeEnum());
            dto.setStatusEnum(head.getStatusEnum());
            dto.setCategoryEnum(head.getCategoryEnum());
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setApplicantId(head.getApplicantId());
            dto.setApplyTime(maxApply);
            dto.setApprovalTime(approvalTime);
            dto.setTotalAmount(totalAmount);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用按 busNo 去重后的聚合行构建可关联请领分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 可关联请领分页响应
     */
    @Override
    public PageLinkableRequisitionsResponse toPageLinkableRequisitionsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<WarehouseDocumentDetailRowDto> records) {
        List<WarehouseDocumentDetailRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(
            new PageLinkableRequisitionsResponse(),
            content,
            mergedTotal,
            pageNo,
            pageSize);
    }

    /**
     * 将已同意药房请领单读模型行装配为出库草稿预填响应。
     *
     * @param requisitionLines 请领单供应请求行，非空
     * @param productHints     供应行 id → 产品目录单位带（可空 map）
     * @return 出库表单预填 DTO
     */
    @Override
    public GetLinkablePharmacyRequisitionDetailResponse toGetLinkablePharmacyRequisitionDetailResponse(
        List<SupplyRequestModel> requisitionLines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetLinkablePharmacyRequisitionDetailResponse res = new GetLinkablePharmacyRequisitionDetailResponse();
        if (requisitionLines == null || requisitionLines.isEmpty()) {
            return res;
        }
        SupplyRequestModel head = requisitionLines.get(0);
        String busNo = head.getBusNo();
        res.setOriginalBusNo(busNo);
        res.setRequisitionBusNo(busNo);
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setPractitionerId(head.getPractitionerId());
        Map<Long, ProductDefinitionCatalogEditorHintsDto> safeHints = productHints;
        if (safeHints == null) {
            safeHints = new LinkedHashMap<>();
        }
        List<StockOutDetailLineDto> lines = new ArrayList<>();
        for (SupplyRequestModel line : requisitionLines) {
            if (line == null) {
                continue;
            }
            StockOutDetailLineDto dto = this.toStockOutDetailLineDto(line);
            if (dto != null && line.getId() != null) {
                ProductDefinitionCatalogEditorHintsDto hint = safeHints.get(line.getId());
                if (hint != null) {
                    dto.setProductCatalog(hint);
                }
            }
            if (dto != null) {
                lines.add(dto);
            }
        }
        res.setLines(lines);
        return res;
    }

    /**
     * 库存读模型与产品展示 DTO 映射为选品行。
     *
     * @param inv 库存项读模型
     * @param pr  产品定义展示 DTO，可为 null
     * @return 选品行 DTO
     */
    @Override
    public ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setInventoryItemId(inv.getId());
        dto.setProductTableName(inv.getItemTable());
        dto.setProductDefinitionId(inv.getItemId());
        dto.setAvailableQuantity(inv.getQuantity());
        dto.setLotNumber(inv.getLotNumber());
        dto.setExpirationDate(inv.getExpirationDate());
        if (pr != null) {
            // —— 读模型在场时补齐编号/名称/规格/厂家，便于选品弹窗一眼识别。——
            dto.setProductBusNo(pr.getProductBusNo());
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
        } else {
            dto.setProductName(null);
            dto.setSpecification(null);
            dto.setManufacturer(null);
        }
        dto.setUnitBands(null);
        dto.setPurchaseUnitPrice(null);
        return dto;
    }

    /**
     * 比较两条供应申请读模型上的申请时刻：空值排后，非空按时间降序，用于同号多行下选取「最新申请」头行。
     *
     * @param left  左操作数，不应为 {@code null}
     * @param right 右操作数，不应为 {@code null}
     * @return 与 {@link Comparator#compare} 约定的比较结果符号
     */
    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        LocalDateTime ta = left.getApplyTime();
        LocalDateTime tb = right.getApplyTime();
        if (ta == null && tb == null) {
            return 0;
        }
        // —— 申请时间为空的行视为更旧，排序沉底，避免抢占「最近申请」头行。——
        if (ta == null) {
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        return tb.compareTo(ta);
    }

    /**
     * 按行主键升序比较两条读模型；空主键排后，用于「首条非空审批时间」等稳定口径。
     *
     * @param left  左操作数，不应为 {@code null}
     * @param right 右操作数，不应为 {@code null}
     * @return 与 {@link Comparator#compare} 约定的比较结果符号
     */
    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        Long ida = left.getId();
        Long idb = right.getId();
        if (ida == null && idb == null) {
            return 0;
        }
        // —— 无主键行沉底，保证「首条带审批时刻」选取稳定、可复现。——
        if (ida == null) {
            return 1;
        }
        if (idb == null) {
            return -1;
        }
        return ida.compareTo(idb);
    }

    /**
     * 出库单头「源位置类型」：由 {@code sourceLocationId} 推导。
     *
     * @param sourceLocationId 请求 {@code sourceLocationId}，必填
     * @return 持久化 code
     */
    private String resolveSourceTypeEnumForStockOutHeader(Long sourceLocationId) {
        if (sourceLocationId == null) {
            throw new IllegalArgumentException("sourceLocationId 不能为空");
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(sourceLocationId).getCode();
    }

    /**
     * 出库单头「目的位置类型」：由 {@code purposeLocationId} 推导。
     *
     * @param purposeLocationId 请求 {@code purposeLocationId}，必填
     * @return 持久化 code
     */
    private String resolvePurposeTypeEnumForStockOutHeader(Long purposeLocationId) {
        if (purposeLocationId == null) {
            throw new IllegalArgumentException("purposeLocationId 不能为空");
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
    }

    /**
     * 供应类别：空或空白时默认库存供应；否则按 {@link SupplyCategory#getCode()} 解析（B.10.8）。
     *
     * @param categoryCodeRaw 请求传入的类别码，可为 null
     * @return 持久化 code
     */
    private String resolveSupplyCategoryCode(String categoryCodeRaw) {
        if (StringUtils.isEmpty(categoryCodeRaw)) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }
}