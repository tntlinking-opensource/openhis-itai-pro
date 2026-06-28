/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.coding.request;

import lombok.Getter;
import lombok.Setter;

/**
 * ICD 编码检索请求。
 */
@Getter
@Setter
public class SearchIcdRequest {

    /** 检索关键字。 */
    private String keyword;

    /** 最大返回条数。 */
    private Integer limit;
}
