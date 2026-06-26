/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import com.openhis.chs.domain.inventory.dto.InitializeInventoryPairCommand;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class InitializeInventoryRequest {

    private String contractBusNo;
    private Long contractId;
    /** 库房/药房位置 ID（必填）。 */
    private Long locationId;
    private String remark;
    /** 单次批处理上限（默认 200）。 */
    private Integer maxLines;
    /** 库存分页页码（0 起，续跑时传上次返回的 nextPageNo）。 */
    private Integer pageNo;
    /** 是否跳过已成功初始化的库存行（默认 true）。 */
    private Boolean skipInitialized;
    private List<InitializeInventoryPairCommand> pairs = new ArrayList<>();
}
