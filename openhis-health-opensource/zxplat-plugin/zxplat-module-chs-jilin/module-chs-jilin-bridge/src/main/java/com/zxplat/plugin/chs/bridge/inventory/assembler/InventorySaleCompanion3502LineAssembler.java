/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.zxplat.plugin.chs.domain.share.enums.InvChgType;
import com.zxplat.domain.share.constants.GeneralSharedConstants;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 销售 3505 / 退药 3506 配对 3502 行组装（无 IO、无 Spring）。
 *
 * <p>发药 / 住院清床合并：先 {@link InvChgType#OTHER_OUT}（107，负）再 3505；批次沿用销售行
 * {@code fixmedins_bchno}。</p>
 * <p>门诊退药 / 住院清床冲销：先 3506 成功，再 {@link InvChgType#OTHER_IN}（106，正）；批次取
 * {@code originalFixmedinsBchno}（门诊=退药发放 busNo；住院冲销=`CLRREV`{原3505上报id}），
 * 与 3506 平台定位用的原销售批次号不同。</p>
 */
public final class InventorySaleCompanion3502LineAssembler {

    private InventorySaleCompanion3502LineAssembler() {
    }

    /**
     * 由 3505 销售行组装前置出库 3502（107，负数 cnt）；批次沿用发药单号。
     *
     * @param saleLine 已组装的 3505 行
     * @return 3502 上报行；入参空、无数量或数量为零时 {@code null}
     */
    public static InvUploadLineCommand fromSaleOutbound(InvUploadLineCommand saleLine) {
        return buildCompanion(saleLine, InvChgType.OTHER_OUT, null, false);
    }

    /**
     * 由 3506 退药行组装后置入库 3502（106，正数 cnt）；批次覆写为退药单号。
     *
     * @param returnLine 已组装的 3506 行（fixmedins_bchno 为原销售批次）
     * @return 3502 上报行；入参空、无退药单号/数量或数量为零时 {@code null}
     */
    public static InvUploadLineCommand fromSaleReturnInbound(InvUploadLineCommand returnLine) {
        if (returnLine == null) {
            return null;
        }
        // 退药伴生批次：originalFixmedinsBchno（退药单号），禁止用原销售 MDP
        return buildCompanion(
            returnLine, InvChgType.OTHER_IN, returnLine.getOriginalFixmedinsBchno(), true);
    }

    /**
     * 将销售/退药行映射为 3502 变更行。
     *
     * @param saleLine               3505/3506 源行
     * @param invChgType             107 或 106
     * @param overrideFixmedinsBchno 非空时覆盖批次；空则沿用源行 fixmedins_bchno
     * @param requireOverrideBchno   true 时覆写批次必填（退药伴生），空则返回 null
     * @return 3502 行；无法组装时 null
     */
    private static InvUploadLineCommand buildCompanion(
        InvUploadLineCommand saleLine,
        InvChgType invChgType,
        String overrideFixmedinsBchno,
        boolean requireOverrideBchno
    ) {
        if (saleLine == null || saleLine.getSelRetnCnt() == null) {
            return null;
        }
        // 零数量无法满足出库负 / 入库正方向校验，跳过
        if (saleLine.getSelRetnCnt().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        String bchno = trimToNull(overrideFixmedinsBchno);
        // 退药伴生必须有退药单号
        if (requireOverrideBchno && bchno == null) {
            return null;
        }
        if (bchno == null) {
            bchno = saleLine.getFixmedinsBchno();
        }
        if (isEmpty(bchno)) {
            return null;
        }
        InvUploadLineCommand line3502 = new InvUploadLineCommand();
        line3502.setSourceLineId(saleLine.getSourceLineId());
        line3502.setMedListCodg(saleLine.getMedListCodg());
        line3502.setFixmedinsHilistId(saleLine.getFixmedinsHilistId());
        line3502.setFixmedinsHilistName(saleLine.getFixmedinsHilistName());
        line3502.setFixmedinsBchno(bchno);
        line3502.setManuLotnum(saleLine.getManuLotnum());
        line3502.setManuDate(saleLine.getManuDate());
        line3502.setExpyEnd(saleLine.getExpyEnd());
        line3502.setRxFlag(saleLine.getRxFlag());
        line3502.setMemo(saleLine.getMemo());
        line3502.setTracePolicy(saleLine.getTracePolicy());
        // trdn_flag：销售行未填时回落未扫默认
        if (isEmpty(saleLine.getTrdnFlag())) {
            line3502.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
        } else {
            line3502.setTrdnFlag(saleLine.getTrdnFlag());
        }
        // 追溯码拷贝，避免与销售行共享可变 List
        if (saleLine.getDrugTracCodgs() != null && !saleLine.getDrugTracCodgs().isEmpty()) {
            line3502.setDrugTracCodgs(new ArrayList<>(saleLine.getDrugTracCodgs()));
        }
        // cnt：先 abs 再按 107/106 赋正负
        line3502.setCnt(invChgType.toSignedCnt(saleLine.getSelRetnCnt()));
        // 平台 pric 必填：优先成交价，回落行上已解析单价（门诊退药常无退药行收费项）
        BigDecimal pric = saleLine.getFinlTrnsPric();
        if (pric == null) {
            pric = saleLine.getPric();
        }
        line3502.setPric(pric);
        line3502.setInvChgType(invChgType.getCode());
        // 变更时间：优先发退药时间，空则当前时刻
        if (isEmpty(saleLine.getSelRetnTime())) {
            line3502.setInvChgTime(
                GeneralSharedConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        } else {
            line3502.setInvChgTime(saleLine.getSelRetnTime());
        }
        line3502.setInvChgOpterName(saleLine.getSelRetnOpterName());
        return line3502;
    }
}
