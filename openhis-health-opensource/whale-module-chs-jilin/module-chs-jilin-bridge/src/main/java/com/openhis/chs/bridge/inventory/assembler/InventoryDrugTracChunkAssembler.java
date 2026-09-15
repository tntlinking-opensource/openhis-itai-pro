/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 进销存报文追溯码超限拆批（平台单次 {@code drugtracinfo} ≤
 * {@link ChsInventoryConstants#DRUGTRACINFO_MAX_PER_REQUEST}）。
 *
 * <p>无 IO、无 Spring。仅拆 {@link InvUploadLineCommand}；调用方对每片分别出站。
 * 数量按码数比例分摊，末片吃剩余，避免合计漂移。</p>
 */
public final class InventoryDrugTracChunkAssembler {

    /** 拆批批次号后缀前缀（与 {@code -C01} 形态配套）。 */
    private static final String CHUNK_BCHNO_SUFFIX_PREFIX = "-C";

    /** 工具类禁止实例化。 */
    private InventoryDrugTracChunkAssembler() {
    }

    /**
     * 若追溯码超过平台上限则拆成多行；否则返回仅含原行的单元素列表。
     *
     * @param line           待上报行（3502 用 {@code cnt}；可空）
     * @param maxPerRequest  单次最大码数；≤0 时回落常量上限
     * @return 非空列表；入参空时空列表
     */
    public static List<InvUploadLineCommand> splitForPlatformLimit(
        InvUploadLineCommand line,
        int maxPerRequest
    ) {
        // 空行：无拆批对象
        if (line == null) {
            return List.of();
        }
        int limit = maxPerRequest;
        // 调用方未传上限：回落本系统出站安全上限（DRUGTRACINFO_MAX_PER_REQUEST=100），非书面 HARD_LIMIT=500
        if (limit <= 0) {
            limit = ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST;
        }
        List<String> codes = line.getDrugTracCodgs();
        // 无码或未超限：不拆，保持原行引用（调用方可继续走单次幂等）
        if (codes == null || codes.size() <= limit) {
            return List.of(line);
        }
        String baseBchno = trimToNull(line.getFixmedinsBchno());
        // 无批次无法生成 -C 后缀幂等键，原样返回由平台拒收并落 FAILED
        if (baseBchno == null) {
            return List.of(line);
        }
        BigDecimal totalCnt = line.getCnt();
        // cnt 空按 0 分摊，避免 NPE；业务上超限入库通常 cnt 与码数同阶
        if (totalCnt == null) {
            totalCnt = BigDecimal.ZERO;
        }
        int totalCodes = codes.size();
        int chunkCount = (totalCodes + limit - 1) / limit;
        List<InvUploadLineCommand> chunks = new ArrayList<>(chunkCount);
        BigDecimal assignedAbs = BigDecimal.ZERO;
        BigDecimal absTotal = totalCnt.abs();
        boolean negative = totalCnt.signum() < 0;
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int from = chunkIndex * limit;
            int to = Math.min(from + limit, totalCodes);
            List<String> slice = new ArrayList<>(codes.subList(from, to));
            InvUploadLineCommand chunk = copyLineShell(line);
            chunk.setDrugTracCodgs(slice);
            chunk.setFixmedinsBchno(buildChunkFixmedinsBchno(baseBchno, chunkIndex + 1, chunkCount));
            // 数量：按本片码数占总数比例分摊；末片用差额吃尽
            BigDecimal chunkAbs;
            if (chunkIndex == chunkCount - 1) {
                chunkAbs = absTotal.subtract(assignedAbs);
            } else {
                chunkAbs = absTotal
                    .multiply(BigDecimal.valueOf(slice.size()))
                    .divide(BigDecimal.valueOf(totalCodes), ChsInventoryConstants.UPLOAD_QUANTITY_SCALE, RoundingMode.HALF_UP);
                assignedAbs = assignedAbs.add(chunkAbs);
            }
            if (negative) {
                // 出库类变更：保持负号
                chunk.setCnt(chunkAbs.negate());
            } else {
                // 入库类变更：正数 cnt
                chunk.setCnt(chunkAbs);
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 生成拆批批次号：{@code base-C01}；保证总长 ≤30（平台 fixmedins_bchno 上限）。
     *
     * @param baseBchno  原始批次号
     * @param chunkNo    1-based 片号
     * @param chunkCount 总片数（决定后缀位数）
     * @return 带后缀的批次号
     */
    static String buildChunkFixmedinsBchno(String baseBchno, int chunkNo, int chunkCount) {
        // 后缀宽度至少 2，保证 -C01 形态稳定
        int width = String.valueOf(chunkCount).length();
        if (width < 2) {
            width = 2;
        }
        String suffix = CHUNK_BCHNO_SUFFIX_PREFIX + String.format("%0" + width + "d", chunkNo);
        int maxBase = 30 - suffix.length();
        String base = baseBchno;
        // 平台 fixmedins_bchno ≤30：超长时截断基号保留后缀
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }
        return base + suffix;
    }

    /**
     * 浅拷贝上报行外壳（不含追溯码列表，由调用方写入切片）。
     *
     * @param source 源行
     * @return 新行
     */
    private static InvUploadLineCommand copyLineShell(InvUploadLineCommand source) {
        InvUploadLineCommand copy = new InvUploadLineCommand();
        copy.setSourceLineId(source.getSourceLineId());
        copy.setMedListCodg(source.getMedListCodg());
        copy.setFixmedinsHilistId(source.getFixmedinsHilistId());
        copy.setFixmedinsHilistName(source.getFixmedinsHilistName());
        copy.setManuLotnum(source.getManuLotnum());
        copy.setManuDate(source.getManuDate());
        copy.setExpyEnd(source.getExpyEnd());
        copy.setRxFlag(source.getRxFlag());
        copy.setMemo(source.getMemo());
        copy.setTracePolicy(source.getTracePolicy());
        copy.setTrdnFlag(source.getTrdnFlag());
        copy.setPric(source.getPric());
        copy.setInvChgType(source.getInvChgType());
        copy.setInvChgTime(source.getInvChgTime());
        copy.setInvChgOpterName(source.getInvChgOpterName());
        copy.setPurcRetnCnt(source.getPurcRetnCnt());
        copy.setFinlTrnsPric(source.getFinlTrnsPric());
        copy.setSelRetnCnt(source.getSelRetnCnt());
        copy.setOriginalFixmedinsBchno(source.getOriginalFixmedinsBchno());
        if (!isEmpty(source.getSplerName())) {
            copy.setSplerName(source.getSplerName());
        }
        return copy;
    }
}
