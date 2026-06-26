/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * 转科路径行快照。
 */
@Getter
@Setter
public class TransferLineSnapshot {

    /** 源科室名称。 */
    private String fromDeptName;

    /** 目标科室名称。 */
    private String toDeptName;
}
