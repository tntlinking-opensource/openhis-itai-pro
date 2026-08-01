/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import cn.hutool.core.date.DateUtil;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.enums.AcctUsedFlag;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.domain.share.enums.PsnSetlway;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.scheme.common.model.ChsSettleData;
import com.openhis.whale.core.application.exception.BusinessException;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** 门诊 2203207 结算报文共用组装（baseSettleData、chrg_bchno 等） */
public final class OpOutpatientSettleInputAssembler {

    private static final DateTimeFormatter FEE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OpOutpatientSettleInputAssembler() {
    }

    /** 生成收费批次号（0 字符） */
    public static String newChrgBchno(ContractConfig config, long seed) {
        String prefix = "OP";
        if (config != null && isNotEmpty(config.getFixmedinsCode())) {
            String code = config.getFixmedinsCode().trim();
            if (code.length() > 8) {
                prefix = code.substring(0, 8);
            } else {
                prefix = code;
            }
        }
        String day = DateUtil.format(DateUtil.date(), "yyyyMMdd");
        String suffix = String.valueOf(Math.abs(seed) % 1_000_000L);
        while (suffix.length() < 6) {
            suffix = "0" + suffix;
        }
        return prefix + day + suffix;
    }

    /** 解析门诊 med_type（默11 普通门诊） */
    public static String resolveMedTypeForContext(OpPreSettleCommand ctx) {
        if (isNotEmpty(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        return MedType.GENERAL_OUTPATIENT.getCode();
    }

    /** 缺省 med_type=11（普通门诊） */
    public static String resolveMedTypeForCommand(String medType) {
        if (isNotEmpty(medType)) {
            return medType.trim();
        }
        return MedType.GENERAL_OUTPATIENT.getCode();
    }

    static ChsSettleData baseSettleData(
            OpPreSettleCommand ctx,
            String mdtrtId,
            String chrgBchno,
            BigDecimal medfeeSumamt) {
        return new ChsSettleData()
            .setPsnNo(ctx.getPsnNo())
            .setMdtrtCertType(ctx.getMdtrtCertType())
            .setMdtrtCertNo(ctx.getMdtrtCertNo())
            .setMedType(resolveMedTypeForContext(ctx))
            .setMedfeeSumamt(medfeeSumamt)
            .setPsnSetlway(resolvePsnSetlway(ctx))
            .setMdtrtId(mdtrtId)
            .setChrgBchno(chrgBchno)
            .setInsutype(ctx.getInsutype())
            .setAcctUsedFlag(resolveAcctUsedFlag(ctx))
            .setInsuplcAdmdvs(ctx.getInsuplcAdmdvs());
    }

    static String formatTime(java.time.LocalDateTime time) {
        if (time == null) {
            return DateUtil.now();
        }
        return time.format(FEE_TIME);
    }

    /**
     * 非诊断字段空白归一为 {@code "-"}（科室医师等平台仍常见占位写法）。
     * <p><b>禁止</b>用于 {@code diag_code}/{@code diag_name}——须用 {@link #requireDiagText}。</p>
     */
    static String blankToDash(String value) {
        if (isEmpty(value)) {
            return "-";
        }
        return value.trim();
    }

    /**
     * 诊断文本闸门：空或字面量 {@code "-"} 拒绝上传，避免平台类型化空值违规。
     *
     * @param value     诊断编码或名称
     * @param fieldHint 报错字段名（如 diag_code）
     * @return trim 后的非空文本
     */
    public static String requireDiagText(String value, String fieldHint) {
        if (isEmpty(value)) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "2203 " + fieldHint + " 不能为空；挂号费须配置默认诊断，诊疗费须先下诊断");
        }
        String trimmed = value.trim();
        if ("-".equals(trimmed)) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "2203 " + fieldHint + " 禁止传 \"-\" 占位");
        }
        return trimmed;
    }

    static int resolveTraceCount(Integer count) {
        if (count != null && count > 0) {
            return count;
        }
        return 0;
    }

    private static String resolvePsnSetlway(OpPreSettleCommand ctx) {
        if (isNotEmpty(ctx.getPsnSetlway())) {
            return ctx.getPsnSetlway().trim();
        }
        return PsnSetlway.BY_ITEM.getCode();
    }

    private static String resolveAcctUsedFlag(OpPreSettleCommand ctx) {
        if (isNotEmpty(ctx.getAcctUsedFlag())) {
            return ctx.getAcctUsedFlag().trim();
        }
        return AcctUsedFlag.YES.getCode();
    }
}
