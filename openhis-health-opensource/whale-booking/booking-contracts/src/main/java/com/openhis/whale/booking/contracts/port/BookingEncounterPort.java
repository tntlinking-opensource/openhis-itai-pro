package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.dto.EncounterSearchResultDto;
import com.openhis.whale.booking.contracts.dto.EncounterSummaryDto;
import com.openhis.whale.booking.contracts.query.SearchEncounterQuery;

import java.util.Optional;

/**
 * 就诊读 Port。
 */
public interface BookingEncounterPort {

    /**
     * 加载就诊摘要。
     *
     * @param encounterId 就诊主键
     * @return 摘要；不存在时 empty
     */
    Optional<EncounterSummaryDto> loadEncounterSummary(Long encounterId);

    /**
     * 按就诊号/患者/关键字检索就诊上下文。
     *
     * @param query 检索条件
     * @return 命中时返回摘要；否则 empty
     */
    Optional<EncounterSearchResultDto> searchEncounter(SearchEncounterQuery query);
}
