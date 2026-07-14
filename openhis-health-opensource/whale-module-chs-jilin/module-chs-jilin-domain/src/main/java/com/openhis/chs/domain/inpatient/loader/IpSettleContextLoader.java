/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.loader;

import com.openhis.chs.domain.inpatient.command.IpAdmissionCommand;
import com.openhis.chs.domain.inpatient.command.IpEncounterCommand;
import com.openhis.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.command.IpPreSettleCommand;
import com.openhis.chs.domain.inpatient.command.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;

/** 住院医保结算 HIS 上下文加载（bridge 实现） */
public interface IpSettleContextLoader {

    /**
     * 补全住院预结算编排上下文（2301/2303 前置）。
     *
     * @param command 调用方已填写的预结算命令
     * @return 补全后的结算上下文
     */
    IpSettleContext enrichPreSettle(IpPreSettleCommand command);

    /**
     * 补全住院入院登记编排上下文（2401 前置）。
     *
     * @param command 调用方已填写的入院命令
     * @return 补全后的结算上下文
     */
    IpSettleContext enrichAdmission(IpAdmissionCommand command);

    /**
     * 补全住院就诊平台信息（2403 等前置）。
     *
     * @param command 调用方已填写的就诊命令
     * @return 补全后的平台就诊命令
     */
    IpEncounterPlatformCommand enrichEncounterPlatform(IpEncounterCommand command);

    /**
     * 补全住院费用撤销编排上下文（2302 前置）。
     *
     * @param command 调用方已填写的撤销命令
     * @return 补全后的撤销命令
     */
    IpRevokeFeeDetailCommand enrichRevokeFeeDetail(IpRevokeFeeDetailCommand command);
}
