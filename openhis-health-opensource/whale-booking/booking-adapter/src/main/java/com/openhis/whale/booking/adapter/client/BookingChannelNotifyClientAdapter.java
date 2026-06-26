package com.openhis.whale.booking.adapter.client;

import com.openhis.whale.booking.contracts.client.BookingChannelNotifyClient;
import org.springframework.stereotype.Component;

/**
 * {@link BookingChannelNotifyClient} 默认空实现（P2 渠道通知扩展点）。
 *
 * <p>院点可替换为短信/微信适配器；当前 no-op 不影响预约主流程。</p>
 */
@Component
public class BookingChannelNotifyClientAdapter implements BookingChannelNotifyClient {

    /** 预约创建通知（P2 占位，待接入消息中心）。 */
    @Override
    public void notifyBookingCreated(Long appointmentId) {
        // P2：渠道通知占位
    }

    /** 预约取消通知（P2 占位）。 */
    @Override
    public void notifyBookingCancelled(Long appointmentId) {
        // P2：渠道通知占位
    }
}
