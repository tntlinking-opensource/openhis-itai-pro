/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.mapper;

import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningDetailLineDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningListRowDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningProductRowDto;
import com.openhis.health.application.basic.inventoryopening.dto.LocationOpeningOptionDto;
import com.openhis.health.application.basic.inventoryopening.request.AgreeInventoryOpeningRequest;
import com.openhis.health.application.basic.inventoryopening.request.CreateInventoryOpeningRequest;
import com.openhis.health.application.basic.inventoryopening.request.RejectInventoryOpeningRequest;
import com.openhis.health.application.basic.inventoryopening.request.RemoveInventoryOpeningRequest;
import com.openhis.health.application.basic.inventoryopening.request.SubmitInventoryOpeningApprovalRequest;
import com.openhis.health.application.basic.inventoryopening.request.UpdateInventoryOpeningRequest;
import com.openhis.health.application.basic.inventoryopening.request.WithdrawInventoryOpeningApprovalRequest;
import com.openhis.health.application.basic.inventoryopening.response.CloseInventoryOpeningResponse;
import com.openhis.health.application.basic.inventoryopening.response.CreateInventoryOpeningResponse;
import com.openhis.health.application.basic.inventoryopening.response.GetInventoryOpeningDetailResponse;
import com.openhis.health.application.basic.inventoryopening.response.LoadInventoryOpeningFormInitResponse;
import com.openhis.health.application.basic.inventoryopening.response.PageInventoryOpeningsResponse;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateOpeningCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存期初装配实现：{@link BasicInventoryOpeningMapper}。
 *
 * <p>各 {@code @Override} 写全 JavaDoc（禁止 {@code {@inheritDoc}}）。</p>
 */
@Component
public class BasicInventoryOpeningMapperImpl implements BasicInventoryOpeningMapper {

    /**
     * 将位置与关闭标志映射为下拉项。
     *
     * @param locations     位置列表
     * @param closedByLocId 关闭态映射
     * @return 下拉项
     */
    @Override
    public List<LocationOpeningOptionDto> toLocationOpeningOptions(
        List<Location> locations, Map<Long, Boolean> closedByLocId) {
        List<LocationOpeningOptionDto> out = new ArrayList<>();
        if (locations == null) {
            return out;
        }
        for (Location loc : locations) {
            if (loc == null || loc.getId() == null) {
                continue;
            }
            LocationOpeningOptionDto dto = new LocationOpeningOptionDto();
            dto.setValue(loc.getId());
            dto.setLabel(loc.getName());
            dto.setFormEnum(loc.getFormEnum());
            Boolean closed = Boolean.FALSE;
            if (closedByLocId != null && closedByLocId.containsKey(loc.getId())) {
                closed = closedByLocId.get(loc.getId());
            }
            if (closed == null) {
                closed = Boolean.FALSE;
            }
            dto.setOpeningClosed(closed);
            out.add(dto);
        }
        return out;
    }

    /**
     * 人员 → OptionDto。
     *
     * @param practitioners 人员
     * @return 选项
     */
    @Override
    public List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners) {
        List<OptionDto> out = new ArrayList<>();
        if (practitioners == null) {
            return out;
        }
        for (Practitioner p : practitioners) {
            if (p == null || p.getId() == null) {
                continue;
            }
            OptionDto opt = new OptionDto();
            opt.setValue(String.valueOf(p.getId()));
            opt.setLabel(p.getName());
            out.add(opt);
        }
        return out;
    }

    /**
     * 供应状态下拉。
     *
     * @return 状态选项
     */
    @Override
    public List<OptionDto> toOptionDtoListSupplyStatuses() {
        List<OptionDto> out = new ArrayList<>();
        for (SupplyStatus st : SupplyStatus.values()) {
            if (st == null) {
                continue;
            }
            OptionDto opt = new OptionDto();
            opt.setValue(st.getCode());
            opt.setLabel(st.getDisplay());
            out.add(opt);
        }
        return out;
    }

    /**
     * 装配页初始化响应。
     *
     * @param warehouses      药库
     * @param pharmacies      药房
     * @param deptPharms      耗材房
     * @param statuses        状态
     * @param practitioners   经手人
     * @return 初始化响应
     */
    @Override
    public LoadInventoryOpeningFormInitResponse toFormInitResponse(
        List<LocationOpeningOptionDto> warehouses,
        List<LocationOpeningOptionDto> pharmacies,
        List<LocationOpeningOptionDto> deptPharms,
        List<OptionDto> statuses,
        List<OptionDto> practitioners,
        List<OptionDto> suppliers) {
        LoadInventoryOpeningFormInitResponse res = new LoadInventoryOpeningFormInitResponse();
        res.setWarehouseOptions(warehouses);
        res.setPharmacyOptions(pharmacies);
        res.setDepartmentPharmacyOptions(deptPharms);
        res.setStatusOptions(statuses);
        res.setPractitionerOptions(practitioners);
        res.setSupplierOptions(suppliers);
        return res;
    }

    /**
     * 按单号顺序聚合列表行。
     *
     * @param busNoPageOrder        页内单号序
     * @param detailRows            明细
     * @param currentEffectiveByLoc 库房 → 当前有效 busNo
     * @return 列表行
     */
    @Override
    public List<InventoryOpeningListRowDto> toListRows(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows,
        Map<Long, String> currentEffectiveByLoc) {
        List<InventoryOpeningListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        if (detailRows != null) {
            for (SupplyRequestModel row : detailRows) {
                if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                    continue;
                }
                String bn = row.getBusNo().trim();
                byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
            }
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
            SupplyRequestModel head = lines.get(0);
            InventoryOpeningListRowDto dto = new InventoryOpeningListRowDto();
            dto.setBusNo(busNo);
            dto.setTypeEnum(head.getTypeEnum());
            dto.setLocationId(head.getPurposeLocationId());
            dto.setStatusEnum(head.getStatusEnum());
            // 制单人/审批人主键暂存为 id:{n}，由 AppService#enrichListDisplayNames 解析为姓名
            if (head.getApplicantId() != null) {
                dto.setApplicantName("id:" + head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                dto.setApproverName("id:" + head.getApproverId());
            }
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, head.getStatusEnum());
            if (st != null) {
                dto.setStatusDisplay(st.getDisplay());
            }
            LocalDateTime maxApply = null;
            LocalDateTime approvalTime = null;
            BigDecimal qtySum = BigDecimal.ZERO;
            BigDecimal priceSum = BigDecimal.ZERO;
            int pricedLineCount = 0;
            int lineCount = 0;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                lineCount++;
                if (line.getApplyTime() != null) {
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
                }
                if (line.getApprovalTime() != null && approvalTime == null) {
                    approvalTime = line.getApprovalTime();
                }
                if (line.getItemQuantity() != null) {
                    qtySum = qtySum.add(line.getItemQuantity());
                }
                // 仅当本单全部行均有落库 totalPrice 时才在此汇总；否则交 AppService 补算
                if (line.getTotalPrice() != null) {
                    priceSum = priceSum.add(line.getTotalPrice());
                    pricedLineCount++;
                }
            }
            dto.setApplyTime(maxApply);
            dto.setApprovalTime(approvalTime);
            dto.setLineCount(Integer.valueOf(lineCount));
            dto.setTotalQuantity(qtySum);
            if (lineCount > 0 && pricedLineCount == lineCount) {
                dto.setTotalPrice(priceSum);
            }
            boolean effective = false;
            if (currentEffectiveByLoc != null && head.getPurposeLocationId() != null) {
                String cur = currentEffectiveByLoc.get(head.getPurposeLocationId());
                if (busNo.equals(cur)) {
                    effective = true;
                }
            }
            dto.setCurrentEffective(Boolean.valueOf(effective));
            out.add(dto);
        }
        return out;
    }

    /**
     * 装配分页响应。
     *
     * @param total   总单数
     * @param records 行
     * @return 分页响应
     */
    @Override
    public PageInventoryOpeningsResponse toPageResponse(long total, List<InventoryOpeningListRowDto> records) {
        PageInventoryOpeningsResponse res = new PageInventoryOpeningsResponse();
        res.setTotal(Long.valueOf(total));
        res.setRecords(records);
        return res;
    }

    /**
     * 装配详情响应。
     *
     * @param lines    供应行
     * @param products 产品读模型
     * @return 详情
     */
    @Override
    public GetInventoryOpeningDetailResponse toDetailResponse(
        List<SupplyRequestModel> lines, Map<String, ProductDefinitionDisplayDto> products) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_103, "期初单不存在");
        }
        SupplyRequestModel head = lines.get(0);
        GetInventoryOpeningDetailResponse res = new GetInventoryOpeningDetailResponse();
        res.setBusNo(head.getBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setLocationId(head.getPurposeLocationId());
        res.setPractitionerId(head.getPractitionerId());
        res.setOccurrenceTime(head.getOccurrenceTime());
        res.setApplyTime(head.getApplyTime());
        res.setApprovalTime(head.getApprovalTime());
        List<InventoryOpeningDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            // 空草稿无品规行：跳过无 itemId 的占位
            if (line.getItemId() == null) {
                continue;
            }
            ProductDefinitionDisplayDto product = null;
            if (products != null) {
                product = products.get(ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId()));
            }
            detailLines.add(this.toDetailLineDto(line, product));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 详情行装配。
     *
     * @param line    供应行
     * @param product 产品
     * @return 详情行
     */
    @Override
    public InventoryOpeningDetailLineDto toDetailLineDto(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        InventoryOpeningDetailLineDto dto = new InventoryOpeningDetailLineDto();
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setEndTime(line.getEndTime());
        dto.setStartTime(line.getStartTime());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setPrice(line.getPrice());
        // 期初不再回显独立售价（与进货一致：只录进价）
        dto.setSalePrice(null);
        dto.setTotalPrice(line.getTotalPrice());
        dto.setTraceNo(line.getTraceNo());
        dto.setSupplierId(line.getSupplierId());
        if (product != null) {
            dto.setItemName(product.getProductName());
            dto.setSpecification(product.getTotalVolume());
            // 厂家由产品读模型补齐，避免详情网格厂家列空白
            dto.setManufacturer(product.getManufacturer());
        }
        return dto;
    }

    /**
     * 新建请求 → CreateOpeningCommand。
     *
     * @param request         新建请求
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode OPENING 类型
     * @param purposeTypeCode purpose 类型
     * @param busNo           修订时既有单号；新建传 null
     * @return 领域命令
     */
    @Override
    public CreateOpeningCommand toCreateOpeningCommand(
        CreateInventoryOpeningRequest request,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode,
        String busNo) {
        return this.toCreateOpeningCommandFromLines(
            request.getLocationId(),
            request.getPractitionerId(),
            request.getOccurrenceTime(),
            request.getLines(),
            operatorId,
            applicantId,
            openingTypeCode,
            purposeTypeCode,
            busNo);
    }

    /**
     * 编辑请求 → CreateOpeningCommand（同号修订）。
     *
     * @param request         编辑请求
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode OPENING 类型
     * @param purposeTypeCode purpose 类型
     * @return 领域命令
     */
    @Override
    public CreateOpeningCommand toCreateOpeningCommand(
        UpdateInventoryOpeningRequest request,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode) {
        String busNo = null;
        if (request.getBusNo() != null) {
            busNo = request.getBusNo().trim();
        }
        return this.toCreateOpeningCommandFromLines(
            request.getLocationId(),
            request.getPractitionerId(),
            request.getOccurrenceTime(),
            request.getLines(),
            operatorId,
            applicantId,
            openingTypeCode,
            purposeTypeCode,
            busNo);
    }

    /**
     * 由明细行装配 CreateOpeningCommand（导入/复制）。
     *
     * @param locationId      库房
     * @param practitionerId  经手人
     * @param occurrenceTime  业务时点
     * @param lines           明细
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode 类型
     * @param purposeTypeCode purpose 类型
     * @param busNo           修订单号；新建 null
     * @return 领域命令
     */
    @Override
    public CreateOpeningCommand toCreateOpeningCommandFromLines(
        Long locationId,
        Long practitionerId,
        LocalDateTime occurrenceTime,
        List<InventoryOpeningLineDto> lines,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode,
        String busNo) {
        CreateOpeningCommand cmd = new CreateOpeningCommand();
        cmd.setOperatorId(operatorId);
        cmd.setTypeEnum(openingTypeCode);
        cmd.setPurposeTypeEnum(purposeTypeCode);
        cmd.setPurposeLocationId(locationId);
        cmd.setOccurrenceTime(occurrenceTime);
        cmd.setApplicantId(applicantId);
        cmd.setPractitionerId(practitionerId);
        List<CreateOpeningCommand.CreateOpeningItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (InventoryOpeningLineDto line : lines) {
                if (line == null) {
                    continue;
                }
                CreateOpeningCommand.CreateOpeningItemCommand item =
                    new CreateOpeningCommand.CreateOpeningItemCommand();
                item.setBusNo(busNo);
                item.setTypeEnum(openingTypeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(SupplyCategory.STOCK_SUPPLY.getCode());
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(locationId);
                item.setLotNumber(line.getLotNumber());
                item.setStartTime(line.getStartTime());
                item.setEndTime(line.getEndTime());
                item.setOccurrenceTime(occurrenceTime);
                item.setApplicantId(applicantId);
                item.setPractitionerId(practitionerId);
                item.setPrice(line.getPrice());
                // 期初不落独立售价列；批号售价在同意时与进价同值写入
                item.setSalePrice(null);
                item.setTotalPrice(line.getTotalPrice());
                item.setSupplierId(line.getSupplierId());
                item.setTraceNo(line.getTraceNo());
                items.add(item);
            }
        }
        cmd.setItems(items);
        return cmd;
    }

    /**
     * @param busNo 单号
     * @return 新建响应
     */
    @Override
    public CreateInventoryOpeningResponse toCreateResponse(String busNo) {
        CreateInventoryOpeningResponse res = new CreateInventoryOpeningResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @return 提交命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitInventoryOpeningApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand cmd = new SubmitApprovalCommand();
        cmd.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item =
            new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 撤回命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawInventoryOpeningApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand cmd = new WithdrawApprovalCommand();
        cmd.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item =
            new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 同意命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeInventoryOpeningRequest request, Long operatorId) {
        AgreeRequestCommand cmd = new AgreeRequestCommand();
        cmd.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 驳回命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectInventoryOpeningRequest request, Long operatorId) {
        RejectRequestCommand cmd = new RejectRequestCommand();
        cmd.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setChangeReasonCode(request.getReason());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 删除命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveInventoryOpeningRequest request, Long operatorId) {
        RemoveByBusNoCommand cmd = new RemoveByBusNoCommand();
        cmd.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 同意行 → 发放命令。
     *
     * @param lines      同意行
     * @param operatorId 操作人
     * @return 发放命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(
        List<SupplyRequestModel> lines, Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                if (line.getItemQuantity() == null || line.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(line.getTypeEnum());
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
     * @param inventoryItemId 库存项
     * @param increaseQty     增量
     * @param operatorId      操作人
     * @return 加量命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long inventoryItemId, BigDecimal increaseQty, Long operatorId) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        IncreaseQuantityCommand.IncreaseQuantityItemCommand item =
            new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
        item.setId(inventoryItemId);
        item.setIncreaseQty(increaseQty);
        command.setItem(item);
        return command;
    }

    /**
     * @param line              供应行
     * @param purposeLocationId 库房
     * @param productName       品名
     * @param lotNumber         批号
     * @param quantity          数量
     * @param unitCode          单位
     * @param operatorId        操作人
     * @param product           产品
     * @param supplierId        供应商
     * @return 建库存命令
     */
    @Override
    public CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String productName,
        String lotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto product,
        Long supplierId) {
        CreateInventoryItemCommand command = new CreateInventoryItemCommand();
        command.setOperatorId(operatorId);
        CreateInventoryItemCommand.CreateInventoryItemItemCommand item =
            new CreateInventoryItemCommand.CreateInventoryItemItemCommand();
        item.setItemTable(line.getItemTable());
        item.setItemId(line.getItemId());
        item.setName(productName);
        item.setSupplierId(supplierId);
        item.setUnitCode(unitCode);
        item.setQuantity(quantity);
        item.setLotNumber(lotNumber);
        item.setLocationId(purposeLocationId);
        item.setProductionDate(line.getStartTime());
        item.setExpirationDate(line.getEndTime());
        if (product != null) {
            if (StringUtils.isNotEmpty(product.getCategoryCode())) {
                item.setCategoryCode(product.getCategoryCode().trim());
            }
            if (StringUtils.isNotEmpty(product.getPyStr())) {
                item.setPyStr(product.getPyStr().trim());
            }
        }
        command.setItem(item);
        return command;
    }

    /**
     * 选品分页 → 行 DTO。
     *
     * @param page 产品页
     * @return 选品行
     */
    @Override
    public List<InventoryOpeningProductRowDto> toProductRows(Page<ProductDefinitionDisplayDto> page) {
        List<InventoryOpeningProductRowDto> out = new ArrayList<>();
        if (page == null || page.getContent() == null) {
            return out;
        }
        for (ProductDefinitionDisplayDto p : page.getContent()) {
            if (p == null) {
                continue;
            }
            InventoryOpeningProductRowDto row = new InventoryOpeningProductRowDto();
            row.setItemTable(p.getProductTableName());
            row.setItemId(p.getProductDefinitionId());
            // 目录编号供选品表展示，与进货选品同字段语义
            row.setProductBusNo(p.getProductBusNo());
            row.setItemName(p.getProductName());
            row.setSpecification(p.getTotalVolume());
            row.setCategoryCode(p.getCategoryCode());
            row.setCategoryCodeDictText(p.getCategoryCodeDictText());
            row.setUnitCode(p.getSplitUnitCode());
            if (StringUtils.isEmpty(row.getUnitCode())) {
                row.setUnitCode(p.getPurchaseUnitCode());
            }
            row.setManufacturer(p.getManufacturer());
            // 选品透出药典追溯单位+比，并装配含 TRACE 槽的 unitBands
            row.setTraceUnitCode(p.getTraceUnitCode());
            row.setTraceUnitRatio(p.getTraceUnitRatio());
            row.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(p));
            out.add(row);
        }
        return out;
    }

    /**
     * @param locationId 库房
     * @return 关闭响应
     */
    @Override
    public CloseInventoryOpeningResponse toCloseResponse(Long locationId) {
        CloseInventoryOpeningResponse res = new CloseInventoryOpeningResponse();
        res.setLocationId(locationId);
        res.setOpeningClosed(Boolean.TRUE);
        return res;
    }
}
