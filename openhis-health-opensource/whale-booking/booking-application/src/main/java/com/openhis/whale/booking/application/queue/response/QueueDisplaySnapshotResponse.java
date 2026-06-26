package com.openhis.whale.booking.application.queue.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 叫号大屏快照响应。
 */
@Getter
@Setter
public class QueueDisplaySnapshotResponse {

    /** 大屏展示标题。 */
    private String displayTitle;

    /** 自动刷新间隔（秒）。 */
    private Integer refreshSeconds;

    /** 当前叫号信息。 */
    private CallToSeeResponse currentCall;

    /** 候诊列表。 */
    private List<QueueListItemDto> waitingList = new ArrayList<>();
}
