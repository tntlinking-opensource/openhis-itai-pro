/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.support;

import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 库存期初业务封锁闸门（AS-BAS-INV-001 · BR-BAS-INV-OPN-003/011/012）。
 *
 * <p>封锁 = 关闭标志 OR 非 OPENING 供应业务 OR 窄口径发放类。命中时必须同写关闭行（U3）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryOpeningBusinessLockSupport {

    private final LocationQueryService locationQueryService;
    private final InventoryOpeningAutoCloseTxSupport autoCloseTxSupport;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 写路径闸门：未关闭且无业务封锁才允许继续；命中封锁时先写关闭再抛 001。
     *
     * @param locationId 目标库房（purpose）
     * @param operatorId 操作人（自动关闭用）
     * @throws BusinessException 已关闭或已封锁（{@link ErrorCodeConstants#BAS_INV_OPN_001}）
     */
    public void assertWritableOrCloseAndReject(Long locationId, Long operatorId) {
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        // 已显式关闭：直接拒绝（与业务封锁文案区分，便于排障）
        if (locationQueryService.isInventoryOpeningClosed(locationId)) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_001,
                "该库房期初已关闭，不可再做期初");
        }
        Optional<SupplyRequest> blockingSupply =
            supplyRequestQueryService.findFirstNonOpeningBusinessForLocation(locationId);
        boolean dispenseBlocked = medicationDispenseQueryService.existsDispenseBusinessForLocation(locationId);
        // 业务封锁命中：关前若有审核中期初则拒绝自动关闭（与显式关闭 U8 同口径），否则写关闭后拒绝写操作
        if (blockingSupply.isPresent() || dispenseBlocked) {
            if (supplyRequestQueryService.existsOpeningInApprovalForLocation(locationId)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_018,
                    "请先撤回或驳回审核中的期初单再关闭");
            }
            this.closeOpeningQuietly(locationId, operatorId);
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_001,
                this.buildBusinessBlockMessage(locationId, blockingSupply, dispenseBlocked));
        }
    }

    /**
     * 仅探测是否封锁（含已关闭），不抛错、不写关闭。
     *
     * @param locationId 库房
     * @return 已关闭或命中业务封锁时 true
     */
    public boolean isLocked(Long locationId) {
        if (locationId == null) {
            return true;
        }
        if (locationQueryService.isInventoryOpeningClosed(locationId)) {
            return true;
        }
        if (supplyRequestQueryService.existsNonOpeningBusinessForLocation(locationId)) {
            return true;
        }
        if (medicationDispenseQueryService.existsDispenseBusinessForLocation(locationId)) {
            return true;
        }
        return false;
    }

    /**
     * 幂等写关闭行（业务封锁自动关闭 / IF-14 显式关闭共用入口；自动关闭走新事务）。
     *
     * @param locationId 库房
     * @param operatorId 操作人
     */
    public void closeOpeningQuietly(Long locationId, Long operatorId) {
        autoCloseTxSupport.closeOpeningInNewTransaction(locationId, operatorId);
    }

    /**
     * 组装业务封锁可读原因（点名单号/类型，避免「不可能有业务」时无法对库）。
     *
     * @param locationId     库房
     * @param blockingSupply 供应侧命中行
     * @param dispenseBlocked 发药侧是否命中
     * @return 提示文案
     */
    private String buildBusinessBlockMessage(
        Long locationId,
        Optional<SupplyRequest> blockingSupply,
        boolean dispenseBlocked) {
        if (blockingSupply.isPresent()) {
            SupplyRequest row = blockingSupply.get();
            String busNo = row.getBusNo();
            if (busNo == null || busNo.isBlank()) {
                busNo = "-";
            } else {
                busNo = busNo.trim();
            }
            String typeCode = row.getTypeEnum();
            if (typeCode == null || typeCode.isBlank()) {
                typeCode = "-";
            } else {
                typeCode = typeCode.trim();
            }
            String typeLabel = typeCode;
            for (SupplyType t : SupplyType.values()) {
                if (t.getCode().equals(typeCode)) {
                    typeLabel = t.getDisplay();
                    break;
                }
            }
            String status = row.getStatusEnum();
            if (status == null || status.isBlank()) {
                status = "-";
            } else {
                status = status.trim();
            }
            return "该库房已发生供应业务，不可再做期初（locationId="
                + locationId
                + "，单号="
                + busNo
                + "，类型="
                + typeLabel
                + "/"
                + typeCode
                + "，状态="
                + status
                + "）";
        }
        if (dispenseBlocked) {
            return "该库房已发生发药/退药业务，不可再做期初（locationId=" + locationId + "）";
        }
        return "该库房已关闭期初或已发生业务，不可再做期初";
    }
}
