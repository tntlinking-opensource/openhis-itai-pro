/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 采购 3503 / 退供 3504 成功后伴生 3502 行组装（无 IO、无 Spring）。
 *
 * <p>从已组装的采购/退供行抄共用字段，按 {@link InvChgType#toSignedCnt} 赋正负：
 * 进货伴生 {@link InvChgType#PURCHASE_IN}（108，正）；退供伴生
 * {@link InvChgType#PURCHASE_RETURN_OUT}（109，负）。</p>
 *
 * <p>退供伴生 3502 的 {@code fixmedins_bchno} 取<strong>退供单号</strong>（与 3504 定位采购用的原进货批次号不同）。
 * 进货伴生批次沿用 3503 进货单号（与 3501+3502 初始化同 bchno、不同 infno 口径一致）。</p>
 */
public final class InventoryPurchaseCompanion3502LineAssembler {

    private InventoryPurchaseCompanion3502LineAssembler() {
    }

    /**
     * 由 3503 采购行组装伴生 3502（108，正数 cnt）；批次号沿用进货单号。
     *
     * @param purchaseLine 已组装的 3503 行
     * @return 3502 上报行；入参空、无数量或数量为零时 {@code null}
     */
    public static InvUploadLineCommand fromPurchaseInbound(InvUploadLineCommand purchaseLine) {
        return buildCompanion(purchaseLine, InvChgType.PURCHASE_IN, null);
    }

    /**
     * 由 3504 退供行组装伴生 3502（109，负数 cnt）；批次号覆写为退供单号。
     *
     * @param returnLine           已组装的 3504 行（其 fixmedins_bchno 仍为原进货批次）
     * @param returnFixmedinsBchno 退供单 busNo 规范后的平台批次号
     * @return 3502 上报行；入参空、无数量或数量为零时 {@code null}
     */
    public static InvUploadLineCommand fromPurchaseReturn(
        InvUploadLineCommand returnLine,
        String returnFixmedinsBchno
    ) {
        return buildCompanion(returnLine, InvChgType.PURCHASE_RETURN_OUT, returnFixmedinsBchno);
    }

    /**
     * 将采购/退供行映射为 3502 变更行。
     *
     * @param purcLine               3503/3504 源行
     * @param invChgType             108 或 109
     * @param overrideFixmedinsBchno 非空时覆盖批次号（退供伴生用退供单号）；空则沿用源行
     * @return 3502 行；源行空、无数量或数量为零时 null
     */
    private static InvUploadLineCommand buildCompanion(
        InvUploadLineCommand purcLine,
        InvChgType invChgType,
        String overrideFixmedinsBchno
    ) {
        if (purcLine == null || purcLine.getPurcRetnCnt() == null) {
            return null;
        }
        // 零数量无法满足 108 正 / 109 负方向校验，跳过伴生
        if (purcLine.getPurcRetnCnt().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        InvUploadLineCommand line3502 = new InvUploadLineCommand();
        line3502.setSourceLineId(purcLine.getSourceLineId());
        line3502.setMedListCodg(purcLine.getMedListCodg());
        line3502.setFixmedinsHilistId(purcLine.getFixmedinsHilistId());
        line3502.setFixmedinsHilistName(purcLine.getFixmedinsHilistName());
        // 进货伴生：沿用 3503 进货批次；退供伴生：覆写为退供单号
        String bchno = trimToNull(overrideFixmedinsBchno);
        if (bchno == null) {
            bchno = purcLine.getFixmedinsBchno();
        }
        line3502.setFixmedinsBchno(bchno);
        line3502.setManuLotnum(purcLine.getManuLotnum());
        line3502.setManuDate(purcLine.getManuDate());
        line3502.setExpyEnd(purcLine.getExpyEnd());
        line3502.setRxFlag(purcLine.getRxFlag());
        line3502.setMemo(purcLine.getMemo());
        line3502.setTracePolicy(purcLine.getTracePolicy());
        // trdn_flag：采购行未填时回落未扫默认（与初始化 3502 同口径）
        if (isEmpty(purcLine.getTrdnFlag())) {
            line3502.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
        } else {
            line3502.setTrdnFlag(purcLine.getTrdnFlag());
        }
        // 追溯码列表拷贝，避免与采购行共享可变 List
        if (purcLine.getDrugTracCodgs() != null && !purcLine.getDrugTracCodgs().isEmpty()) {
            line3502.setDrugTracCodgs(new ArrayList<>(purcLine.getDrugTracCodgs()));
        }
        // cnt：先 abs 再按 108/109 赋正负
        line3502.setCnt(invChgType.toSignedCnt(purcLine.getPurcRetnCnt()));
        line3502.setPric(purcLine.getFinlTrnsPric());
        line3502.setInvChgType(invChgType.getCode());
        // 变更时间：优先采购/退货入库时间，空则当前时刻（平台 inv_chg_time 必填）
        if (isEmpty(purcLine.getPurcRetnStoinTime())) {
            line3502.setInvChgTime(
                GeneralSharedConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        } else {
            line3502.setInvChgTime(purcLine.getPurcRetnStoinTime());
        }
        line3502.setInvChgOpterName(purcLine.getPurcRetnOpterName());
        return line3502;
    }
}
