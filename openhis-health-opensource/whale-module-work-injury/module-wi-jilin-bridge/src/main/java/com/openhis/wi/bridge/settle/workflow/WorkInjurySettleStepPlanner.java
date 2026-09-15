/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.settle.workflow;

import com.openhis.wi.gateway.scheme.shared.model.WiInfnoConstants;
import com.openhis.wi.gateway.scheme.shared.model.WiPhase;
import com.openhis.wi.gateway.scheme.shared.model.WiPlatformConstants;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工伤 infno 链编排（Java 常量；禁止 YAML 配置，见 11 §3）。
 */
@Component
public class WorkInjurySettleStepPlanner {

    /** REG_QUERY：工伤 1101 写 psn_no → 9102。 */
    private static final List<String> REG_QUERY_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.PERSON_INFO,
            WiInfnoConstants.QUALIFICATION_QUERY
    ));

    /** 2301 → 9102 登记审批链。 */
    private static final List<String> REG_APPROVAL_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.APPROVAL_UPLOAD,
            WiInfnoConstants.QUALIFICATION_QUERY
    ));

    /** OP_REVERSE 退费链（2208）。 */
    private static final List<String> OP_REVERSE_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.OUTPATIENT_SETTLE_REVOKE
    ));

    /**
     * RECON：总额对账仅 1320（老 HIS「总额对账」）。
     * <p>对账确认 1322 走独立 phase {@link WiPhase#RECON_CONFIRM}，避免当月未结束仍自动确认。</p>
     */
    private static final List<String> RECON_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.RECON_SUMMARY
    ));

    /** RECON_DETAIL：明细对账 1321（按就诊流水）。 */
    private static final List<String> RECON_DETAIL_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.RECON_DETAIL
    ));

    /** RECON_CONFIRM：对账确认仅 1322（老 HIS「对账确认」）。 */
    private static final List<String> RECON_CONFIRM_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.RECON_CONFIRM
    ));

    /**
     * 解析 phase 下指定 stepIndex 的 infno。
     *
     * @param phase           业务阶段
     * @param stepIndex       0-based 步序号
     * @param encounterAnchor 就诊锚点（OP_SETTLE 保留参数，链结构不再依赖其 flag）
     * @return infno；越界时 null
     */
    public String resolveInfno(WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor) {
        return resolveInfno(phase, stepIndex, encounterAnchor, 0);
    }

    /**
     * 解析 phase 下指定 stepIndex 的 infno。
     *
     * @param phase           业务阶段
     * @param stepIndex       0-based 步序号
     * @param encounterAnchor 就诊锚点
     * @param feeLineCount    OP_SETTLE 收费明细条数（决定 2204 批次数）
     * @return infno；越界时 null
     */
    public String resolveInfno(
            WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor, int feeLineCount) {
        List<String> chain = buildChain(phase, feeLineCount);
        if (stepIndex < 0 || stepIndex >= chain.size()) {
            return null;
        }
        return chain.get(stepIndex);
    }

    /**
     * 当前步若为 2204，返回批次下标（0-based）；否则 0。
     */
    public int resolveFeeUploadBatchIndex(
            WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor, int feeLineCount) {
        List<String> chain = buildChain(phase, feeLineCount);
        if (stepIndex < 0 || stepIndex >= chain.size()) {
            return 0;
        }
        if (!WiInfnoConstants.OUTPATIENT_FEE_UPLOAD.equals(chain.get(stepIndex))) {
            return 0;
        }
        int batch = 0;
        for (int i = 0; i < stepIndex; i++) {
            if (WiInfnoConstants.OUTPATIENT_FEE_UPLOAD.equals(chain.get(i))) {
                batch++;
            }
        }
        return batch;
    }

    /**
     * 某 phase 总步数。
     *
     * @param phase           业务阶段
     * @param encounterAnchor 锚点
     * @return 步数
     */
    public int totalSteps(WiPhase phase, YbWiEncounterAnchor encounterAnchor) {
        return totalSteps(phase, encounterAnchor, 0);
    }

    /**
     * 某 phase 总步数（含 2204 分批）。
     */
    public int totalSteps(WiPhase phase, YbWiEncounterAnchor encounterAnchor, int feeLineCount) {
        return buildChain(phase, feeLineCount).size();
    }

    /**
     * 是否还有下一步。
     *
     * @param phase           业务阶段
     * @param stepIndex       当前步（submit 后已 ++）
     * @param encounterAnchor 锚点
     * @return true 时 nextAction=PREPARE_NEXT
     */
    public boolean hasMoreSteps(WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor) {
        return hasMoreSteps(phase, stepIndex, encounterAnchor, 0);
    }

    /**
     * 是否还有下一步（含 2204 分批）。
     */
    public boolean hasMoreSteps(
            WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor, int feeLineCount) {
        return stepIndex < totalSteps(phase, encounterAnchor, feeLineCount);
    }

    /**
     * 按 phase 返回 infno 链。
     */
    private List<String> buildChain(WiPhase phase, int feeLineCount) {
        if (phase == WiPhase.REG_QUERY) {
            return REG_QUERY_STEPS;
        }
        if (phase == WiPhase.REG_APPROVAL) {
            return REG_APPROVAL_STEPS;
        }
        if (phase == WiPhase.OP_SETTLE) {
            return buildOpSettleChain(feeLineCount);
        }
        if (phase == WiPhase.OP_REVERSE) {
            return OP_REVERSE_STEPS;
        }
        if (phase == WiPhase.RECON) {
            return RECON_STEPS;
        }
        if (phase == WiPhase.RECON_DETAIL) {
            return RECON_DETAIL_STEPS;
        }
        if (phase == WiPhase.RECON_CONFIRM) {
            return RECON_CONFIRM_STEPS;
        }
        return Collections.emptyList();
    }

    /**
     * OP_SETTLE：先撤回再正结（链长固定，不因 has_active / platform_registered 中途变长变短）。
     * <p>{@code 2205 → 9102 → 2201 → 2203 → 2204×N → 2206 → 2207}</p>
     * <p>2205：整单撤未结算；空撤由 Orchestrator 幂等成功。2201：已登记时由 Orchestrator 跳过 Win。</p>
     *
     * @param feeLineCount 收费明细条数
     * @return infno 有序列表
     */
    private List<String> buildOpSettleChain(int feeLineCount) {
        List<String> chain = new ArrayList<>();
        chain.add(WiInfnoConstants.OUTPATIENT_FEE_REVOKE);
        chain.add(WiInfnoConstants.QUALIFICATION_QUERY);
        chain.add(WiInfnoConstants.OUTPATIENT_REG);
        chain.add(WiInfnoConstants.OUTPATIENT_VISIT_UPLOAD);
        int batches = feeUploadBatchCount(feeLineCount);
        for (int i = 0; i < batches; i++) {
            chain.add(WiInfnoConstants.OUTPATIENT_FEE_UPLOAD);
        }
        chain.add(WiInfnoConstants.OUTPATIENT_PRE_SETTLE);
        chain.add(WiInfnoConstants.OUTPATIENT_SETTLE);
        return chain;
    }

    /** 至少 1 批（空明细由 assembler 报错）；超出按 50 条拆批。 */
    private static int feeUploadBatchCount(int feeLineCount) {
        if (feeLineCount <= 0) {
            return 1;
        }
        int batchSize = WiPlatformConstants.FEE_UPLOAD_BATCH_SIZE;
        return (feeLineCount + batchSize - 1) / batchSize;
    }
}
