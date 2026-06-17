package com.openhis.whale.booking.adapter.port;

import com.openhis.whale.booking.contracts.port.ChannelNotifyPort;
import org.springframework.stereotype.Component;

/**
 * {@link ChannelNotifyPort} 默认空实现（院点可替换为短信/微信适配器）。
 */
@Component
public class ChannelNotifyPortAdapter implements ChannelNotifyPort {

    @Override
    public void notifyBookingCreated(Long appointmentId) {
        // P2：渠道通知占位
    }

    @Override
    public void notifyBookingCancelled(Long appointmentId) {
        // P2：渠道通知占位
    }
}
