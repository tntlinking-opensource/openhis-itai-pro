/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.workflow;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryInboundUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryPurchaseReturnUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryUploadContextLoader;
import com.zxplat.plugin.chs.domain.inventory.access.InventoryUploadAccess;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterInboundCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterPurchaseReturnCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.enums.InvChgType;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运维补传成功后的配对跟传：后置伴生 3502，或前置 3502(107) 成功后跟 3505。
 *
 * <p>通过再次走业务组装+上报路径触发幂等主交易与伴生，避免补传只重放单条导致配对断裂。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRetryCompanionFollowUp {

    private final InventoryUploadAccess uploadAccess;
    private final InventoryUploadContextLoader saleUploadContextLoader;
    private final InventoryInboundUploadContextLoader inboundUploadContextLoader;
    private final InventoryPurchaseReturnUploadContextLoader purchaseReturnUploadContextLoader;
    private final SupplyRequestQueryService supplyRequestQueryService;

    /**
     * 单条补传 SUCCESS 后按交易号触发配对跟传；失败仅打日志，不回滚已成功补传。
     *
     * @param succeededRow 刚补传成功的流水（须已 SUCCESS）
     */
    public void followUpAfterRetrySuccess(YbInvUpload succeededRow) {
        if (succeededRow == null || isEmpty(succeededRow.getInfno())) {
            return;
        }
        try {
            String infno = succeededRow.getInfno().trim();
            // 后置伴生：主交易补传成功后再跑完整上报路径以触发伴生 3502
            if (ChsInfNo.INVENTORY_PURCHASE.equals(infno)) {
                this.followUpPurchase3503(succeededRow);
                return;
            }
            if (ChsInfNo.INVENTORY_PURCHASE_RETURN.equals(infno)) {
                this.followUpPurchaseReturn3504(succeededRow);
                return;
            }
            if (ChsInfNo.INVENTORY_SALE_RETURN.equals(infno)) {
                this.followUpSaleReturn3506(succeededRow);
                return;
            }
            // 前置配对：销售出库 3502(107) 补传成功后再传 3505
            if (ChsInfNo.INVENTORY_CHANGE.equals(infno) && this.isSaleOutboundChgType(succeededRow)) {
                this.followUpSale3505AfterOutbound3502(succeededRow);
            }
        } catch (RuntimeException ex) {
            log.warn(
                "补传成功后配对跟传失败 uploadId={} infno={}: {}",
                succeededRow.getId(),
                succeededRow.getInfno(),
                ex.getMessage());
        }
    }

    /**
     * 3503 补传成功：按进货单号重跑采购上报（幂等 3503 + 伴生 3502/108）。
     *
     * @param row 成功 3503 流水
     */
    private void followUpPurchase3503(YbInvUpload row) {
        String inboundBusNo = trimToNull(row.getFixmedinsBchno());
        if (isEmpty(inboundBusNo) || row.getSourceLineId() == null) {
            log.warn("3503 补传跟传跳过：缺少 inboundBusNo/sourceLineId uploadId={}", row.getId());
            return;
        }
        UploadAfterInboundCommand command = new UploadAfterInboundCommand()
            .setInboundBusNo(inboundBusNo)
            .setSupplyRequestLineIds(List.of(row.getSourceLineId()))
            .setContractBusNo(row.getContractBusNo())
            .setContractId(row.getContractId());
        // Cmd：完整 3503 路径在幂等成功后仍会尝试伴生 3502
        uploadAccess.uploadPurchase3503(inboundUploadContextLoader.build3503Request(command));
    }

    /**
     * 3504 补传成功：按退供单号重跑采购退货上报（幂等 3504 + 伴生 3502/109）。
     *
     * @param row 成功 3504 流水
     */
    private void followUpPurchaseReturn3504(YbInvUpload row) {
        if (row.getSourceLineId() == null) {
            log.warn("3504 补传跟传跳过：缺少 sourceLineId uploadId={}", row.getId());
            return;
        }
        SupplyRequestModel returnLine = supplyRequestQueryService.getById(row.getSourceLineId());
        String returnBusNo = null;
        if (returnLine != null) {
            returnBusNo = trimToNull(returnLine.getBusNo());
        }
        if (isEmpty(returnBusNo)) {
            log.warn("3504 补传跟传跳过：退供单号不可得 uploadId={}", row.getId());
            return;
        }
        UploadAfterPurchaseReturnCommand command = new UploadAfterPurchaseReturnCommand()
            .setReturnBusNo(returnBusNo)
            .setSupplyRequestLineIds(List.of(row.getSourceLineId()))
            .setContractBusNo(row.getContractBusNo())
            .setContractId(row.getContractId());
        // Cmd：完整 3504 路径在幂等成功后仍会尝试伴生 3502（批次=退供单号）
        uploadAccess.uploadPurchaseReturn3504(purchaseReturnUploadContextLoader.build3504Request(command));
    }

    /**
     * 3506 补传成功：按退药行重跑销售退货上报（幂等 3506 + 伴生 3502/106）。
     *
     * @param row 成功 3506 流水
     */
    private void followUpSaleReturn3506(YbInvUpload row) {
        if (row.getSourceId() == null || row.getSourceLineId() == null) {
            log.warn("3506 补传跟传跳过：缺少 encounter/sourceLineId uploadId={}", row.getId());
            return;
        }
        UploadAfterReturnCommand command = new UploadAfterReturnCommand()
            .setEncounterId(row.getSourceId())
            .setDispenseIds(List.of(row.getSourceLineId()))
            .setContractBusNo(row.getContractBusNo())
            .setContractId(row.getContractId());
        // Cmd：完整 3506 路径在幂等成功后仍会尝试伴生 3502（批次=退药单号）
        uploadAccess.uploadSaleReturn3506(saleUploadContextLoader.build3506Request(command));
    }

    /**
     * 销售前置 3502(107) 补传成功：重跑 3505（先幂等 3502 再传 3505）。
     *
     * @param row 成功 3502 流水（inv_chg_type=107）
     */
    private void followUpSale3505AfterOutbound3502(YbInvUpload row) {
        if (row.getSourceId() == null || row.getSourceLineId() == null) {
            log.warn("3502→3505 补传跟传跳过：缺少 encounter/sourceLineId uploadId={}", row.getId());
            return;
        }
        UploadAfterDispenseCommand command = new UploadAfterDispenseCommand()
            .setEncounterId(row.getSourceId())
            .setDispenseIds(List.of(row.getSourceLineId()))
            .setContractBusNo(row.getContractBusNo())
            .setContractId(row.getContractId());
        // Cmd：完整 3505 路径在前置 3502 已成功时继续出站 3505
        uploadAccess.uploadSale3505(saleUploadContextLoader.build3505Request(command));
    }

    /**
     * 判断 3502 是否为发药前置其他出库（107）。
     *
     * @param row 3502 流水
     * @return true 表示销售配对前置出库
     */
    private boolean isSaleOutboundChgType(YbInvUpload row) {
        String chgType = this.readInvChgType(row.getRequestJson());
        return InvChgType.OTHER_OUT.getCode().equals(chgType);
    }

    /**
     * 从 3502 request_json 的 invinfo 读取 {@code inv_chg_type}。
     *
     * @param requestJson 请求报文
     * @return 变更类型码；解析失败 null
     */
    private String readInvChgType(String requestJson) {
        if (isEmpty(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            if (root == null) {
                return null;
            }
            JSONObject invinfo = root.getJSONObject("invinfo");
            if (invinfo == null) {
                return null;
            }
            String code = invinfo.getString("inv_chg_type");
            if (isEmpty(code)) {
                code = invinfo.getString("invChgType");
            }
            return trimToNull(code);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
