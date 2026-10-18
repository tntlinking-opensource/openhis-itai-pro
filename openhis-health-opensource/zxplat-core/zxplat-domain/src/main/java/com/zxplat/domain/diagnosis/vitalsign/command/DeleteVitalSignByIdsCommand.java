/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量按主表 id 删除生命体征单据命令（DM-DIA-002 v2.0 §6.1.3）。
 */
@Getter
@Setter
public class DeleteVitalSignByIdsCommand {

    /** 主表 id 列表。 */
    private List<Long> ids;

    /** 操作人 id。 */
    private Long operatorId;
}
