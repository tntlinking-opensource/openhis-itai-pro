/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.settle.workflow;

import com.openhis.wi.gateway.scheme.shared.model.WiInfnoConstants;
import com.openhis.wi.gateway.scheme.shared.model.WiPhase;
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

    /** REG_QUERY 单步链（9102）。 */
    private static final List<String> REG_QUERY_STEPS =
            Collections.unmodifiableList(List.of(WiInfnoConstants.QUALIFICATION_QUERY));

    /** 2301 → 9102 登记审批链。 */
    private static final List<String> REG_APPROVAL_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.APPROVAL_UPLOAD,
            WiInfnoConstants.QUALIFICATION_QUERY
    ));

    /** OP_SETTLE 核心步（2205 由 anchor 条件插入）。 */
    private static final List<String> OP_SETTLE_CORE = Collections.unmodifiableList(List.of(
            WiInfnoConstants.OUTPATIENT_REG,
            WiInfnoConstants.OUTPATIENT_VISIT_UPLOAD,
            WiInfnoConstants.OUTPATIENT_FEE_UPLOAD,
            WiInfnoConstants.OUTPATIENT_PRE_SETTLE,
            WiInfnoConstants.OUTPATIENT_SETTLE
    ));

    /** OP_REVERSE 退费链（2208）。 */
    private static final List<String> OP_REVERSE_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.OUTPATIENT_SETTLE_REVOKE
    ));

    /** RECON 对账链（1320→1321→1322）。 */
    private static final List<String> RECON_STEPS = Collections.unmodifiableList(List.of(
            WiInfnoConstants.RECON_SUMMARY,
            WiInfnoConstants.RECON_DETAIL,
            WiInfnoConstants.RECON_CONFIRM
    ));

    /**
     * 解析 phase 下指定 stepIndex 的 infno。
     *
     * @param phase           业务阶段
     * @param stepIndex       0-based 步序号
     * @param encounterAnchor 就诊锚点（OP_SETTLE 时用于 2205 条件插入）
     * @return infno；越界时 null
     */
    public String resolveInfno(WiPhase phase, int stepIndex, YbWiEncounterAnchor encounterAnchor) {
        List<String> chain = buildChain(phase, encounterAnchor);
        if (stepIndex < 0 || stepIndex >= chain.size()) {
            return null;
        }
        return chain.get(stepIndex);
    }

    /**
     * 某 phase 总步数。
     *
     * @param phase           业务阶段
     * @param encounterAnchor 锚点
     * @return 步数
     */
    public int totalSteps(WiPhase phase, YbWiEncounterAnchor encounterAnchor) {
        return buildChain(phase, encounterAnchor).size();
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
        return stepIndex < totalSteps(phase, encounterAnchor);
    }

    /**
     * 按 phase 返回 infno 链；OP_SETTLE 动态插入 2205。
     *
     * @param phase           业务阶段
     * @param encounterAnchor 就诊锚点
     * @return infno 有序列表
     */
    private List<String> buildChain(WiPhase phase, YbWiEncounterAnchor encounterAnchor) {
        if (phase == WiPhase.REG_QUERY) {
            return REG_QUERY_STEPS;
        }
        if (phase == WiPhase.REG_APPROVAL) {
            return REG_APPROVAL_STEPS;
        }
        if (phase == WiPhase.OP_SETTLE) {
            return buildOpSettleChain(encounterAnchor);
        }
        if (phase == WiPhase.OP_REVERSE) {
            return OP_REVERSE_STEPS;
        }
        if (phase == WiPhase.RECON) {
            return RECON_STEPS;
        }
        return Collections.emptyList();
    }

    /**
     * 组装 OP_SETTLE 链：2201→2203→(2205?)→2204→2206→2207（BR-WI-SET-002）。
     *
     * @param encounterAnchor 锚点（has_active_fee_upload 决定是否 2205）
     * @return infno 有序列表
     */
    private List<String> buildOpSettleChain(YbWiEncounterAnchor encounterAnchor) {
        // Pre：2201→2203→(2205?)→2204→2206→2207；无历史 2204 则跳过 2205（BR-WI-SET-002）
        List<String> chain = new ArrayList<>();
        chain.add(WiInfnoConstants.OUTPATIENT_REG);
        chain.add(WiInfnoConstants.OUTPATIENT_VISIT_UPLOAD);
        if (needs2205(encounterAnchor)) {
            chain.add(WiInfnoConstants.OUTPATIENT_FEE_REVOKE);
        }
        chain.add(WiInfnoConstants.OUTPATIENT_FEE_UPLOAD);
        chain.add(WiInfnoConstants.OUTPATIENT_PRE_SETTLE);
        chain.add(WiInfnoConstants.OUTPATIENT_SETTLE);
        return chain;
    }

    /**
     * anchor.has_active_fee_upload=true 时插入 2205（撤销历史 2204）。
     *
     * @param encounterAnchor 就诊锚点
     * @return 是否需要 2205
     */
    private boolean needs2205(YbWiEncounterAnchor encounterAnchor) {
        if (encounterAnchor == null) {
            return false;
        }
        Boolean active = encounterAnchor.getHasActiveFeeUpload();
        if (active == null) {
            return false;
        }
        return active;
    }
}
