/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.loader;

import com.zxplat.plugin.chs.domain.outpatient.command.OpPlatformRegistrationCommand;
import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;

import java.util.List;

/** 门诊医保结算 HIS 上下文加载（bridge 实现） */
public interface OpPreSettleContextLoader {

    /**
     * 补全门诊预结算编排上下文（2204/2206 前置）。
     *
     * @param partial 调用方已填写的部分命令
     * @return 补全后的预结算命令
     */
    OpPreSettleCommand enrichPreSettle(OpPreSettleCommand partial);

    /**
     * 补全门诊平台挂号编排上下文（2201 前置）。
     *
     * @param partial 调用方已填写的部分命令
     * @return 补全后的预结算命令
     */
    OpPreSettleCommand enrichRegistration(OpPlatformRegistrationCommand partial);

    /**
     * 补全分组预结算编排上下文（多 medType/诊断分组场景）。
     *
     * @param partial             调用方已填写的部分命令
     * @param groupChargeItemIds  本组收费项 ID 列表
     * @param groupMedType        本组医疗类别
     * @param groupDiseCodg       本组病种编码
     * @param groupDiseName       本组病种名称
     * @return 补全后的预结算命令
     */
    OpPreSettleCommand enrichPreSettleForGroup(
        OpPreSettleCommand partial,
        List<Long> groupChargeItemIds,
        String groupMedType,
        String groupDiseCodg,
        String groupDiseName);
}
