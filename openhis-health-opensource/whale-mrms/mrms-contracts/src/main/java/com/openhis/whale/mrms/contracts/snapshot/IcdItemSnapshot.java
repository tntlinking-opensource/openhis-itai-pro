/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * ICD 字典项快照。
 */
@Getter
@Setter
public class IcdItemSnapshot {

    /** 编码。 */
    private String code;

    /** 名称。 */
    private String name;

    /** 拼音码/检索辅助。 */
    private String searchCode;
}
