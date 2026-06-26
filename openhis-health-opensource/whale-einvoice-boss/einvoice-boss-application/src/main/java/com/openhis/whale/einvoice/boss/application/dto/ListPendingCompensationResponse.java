package com.openhis.whale.einvoice.boss.application.dto;

import com.openhis.whale.einvoice.boss.contracts.dto.BossCompensationRowDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 待补偿列表响应。
 */
@Data
public class ListPendingCompensationResponse {

    /** 统计摘要。 */
    private BossPendingCompensationStatsDto stats = new BossPendingCompensationStatsDto();

    /** 当前页行。 */
    private List<BossCompensationRowDto> rows = new ArrayList<>();

    /** 过滤后总数。 */
    private long total;
}
