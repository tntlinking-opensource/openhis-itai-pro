/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.contract.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建费用性质命令。
 */
@Getter
@Setter
public class CreateContractCommand {
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** display医嘱。 */
    private Integer displayOrder;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** admvs。 */
    private String admVs;
    /** 机构主键。 */
    private Long orgId;
    /** 终端配置。 */
    private ContractTerminalConfigCommand terminalConfig;
}
