/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import com.openhis.health.domain.share.enums.TherapyType;

/**
 * 住院医生站中医 Tab 跨层共享常量（AS-IP-DOR-006）。
 */
public interface InpatientDoctorStationTcmConstants {

    /** BR-IP-TCM-005：临嘱立即执行频次码（术语 {@code STAT}）。 */
    String IMMEDIATE_RATE_CODE = "STAT";

    /** IF-IP-TCM-29：长临筛选默认值。 */
    String THERAPY_FILTER_DEFAULT = TherapyType.LONG_TERM.getCode();

    /** EX-IP-TCM-101：就诊快照或检索失败。 */
    String MSG_ENCOUNTER_SNAPSHOT_FAILED = "请先选择有效住院就诊";

    /** EX-IP-TCM-102：病证组被医嘱引用。 */
    String MSG_CONDITION_REFERENCED = "先撤销或调整相关处方后再删";

    /** EX-IP-TCM-103：已执行/已发药/不可回退费用。 */
    String MSG_REVOKE_BLOCKED = "已执行、已发药或费用不可回退，无法签退";

    /** EX-IP-TCM-104：患者与就诊不匹配。 */
    String MSG_PATIENT_ENCOUNTER_MISMATCH = "请先选择患者";

    /** EX-IP-TCM-105：持久化失败。 */
    String MSG_PERSISTENCE_FAILED = "保存失败，请重试";

    /** EX-IP-TCM-201：病证组缺一病一证。 */
    String MSG_DIAGNOSIS_GROUP_INCOMPLETE = "每组须同时选择病名与证候";

    /** EX-IP-TCM-202：主诊组超过 1 个。 */
    String MSG_PRIMARY_GROUP_EXCEEDED = "请只保留一个主诊病证组";

    /** EX-IP-TCM-203：未绑定病证组。 */
    String MSG_SYNDROME_GROUP_REQUIRED = "请先维护并绑定中医病证组";

    /** EX-IP-TCM-205：非中药行。 */
    String MSG_NON_TCM_LINE = "请在对应 Tab 签发";

    /** EX-IP-TCM-207：状态不允许。 */
    String MSG_STATUS_NOT_ALLOWED = "当前状态不允许操作";

    /** EX-IP-TCM-208：临嘱频次非立即。 */
    String MSG_TEMPORARY_RATE_NOT_IMMEDIATE = "临时中药方频次须为立即执行";

    /** EX-IP-TCM-209：同方方头不一致。 */
    String MSG_HEADER_INCONSISTENT = "同一张方内方头字段须一致";

    /** EX-IP-TCM-210：长期方频次为立即。 */
    String MSG_LONG_TERM_RATE_IMMEDIATE = "长期中药方频次不得为立即执行";

    /** EX-IP-TCM-211：不可停嘱。 */
    String MSG_STOP_NOT_ALLOWED = "当前状态或非长期方不允许停嘱";

    /** EX-IP-TCM-212：不可取消停嘱。 */
    String MSG_CANCEL_STOP_NOT_ALLOWED = "仅已停嘱的长期中药方可取消停嘱";

    /** EX-IP-TCM-213：出院/转科门禁。 */
    String MSG_CANCEL_STOP_DISCHARGE_GATE = "存在转科或出院流程，暂不可取消停嘱";

    /** 暂存/签发成功提示。 */
    String RESPONSE_MESSAGE_OK = "操作成功";
}
