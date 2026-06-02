import type { IMessage } from '@stomp/stompjs';

import type { ImecMonitorData, ImecWsStatus } from '#/api';

import { computed, onBeforeUnmount, ref, toValue, watch } from 'vue';

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import { getImecLatestDataApi } from '#/api';

interface UseImecWebSocketOptions {
  /** 设备ID，支持 ref 或 getter 函数以保持响应式 */
  deviceId: string | (() => string);
  onMessage?: (data: ImecMonitorData) => void;
  onStatusChange?: (status: ImecWsStatus) => void;
}

/** iMEC WebSocket STOMP 连接管理 composable */
export function useImecWebSocket(options: UseImecWebSocketOptions) {
  const { onMessage, onStatusChange } = options;

  const wsStatus = ref<ImecWsStatus>('disconnected');
  const monitorData = ref<ImecMonitorData | null>(null);
  const connected = computed(
    () => wsStatus.value === 'connected' || wsStatus.value === 'heartbeat',
  );
  const isPollingFallback = ref(false);

  let stompClient: Client | null = null;
  let reconnectAttempts = 0;
  const MAX_RECONNECT_ATTEMPTS = 10;
  const RECONNECT_DELAY_BASE = 2000;
  let reconnectTimer: null | ReturnType<typeof setTimeout> = null;

  // HTTP 轮询回退
  let pollTimer: null | ReturnType<typeof setInterval> = null;

  /** 获取当前 deviceId 值 */
  function getDeviceId(): string {
    return toValue(options.deviceId);
  }

  /** 更新连接状态 */
  function setStatus(status: ImecWsStatus) {
    wsStatus.value = status;
    onStatusChange?.(status);
  }

  /** 连接 WebSocket */
  function connect() {
    const deviceId = getDeviceId();
    if (!deviceId) return;
    if (stompClient?.active) return;

    setStatus('connecting');
    reconnectAttempts = 0;

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 0, // 手动控制重连
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setStatus('connected');
        reconnectAttempts = 0;
        isPollingFallback.value = false;
        stopPolling();

        // 订阅设备数据（使用当前 deviceId）
        stompClient?.subscribe(`/topic/imec/${deviceId}`, (message: IMessage) => {
          try {
            const data: ImecMonitorData = JSON.parse(message.body);
            monitorData.value = data;
            setStatus('heartbeat');
            onMessage?.(data);
          } catch (error) {
            console.error('iMEC WebSocket 消息解析失败:', error);
          }
        });
      },
      onStompError: (frame) => {
        console.error('iMEC STOMP 错误:', frame.headers.message);
        setStatus('error');
        scheduleReconnect();
      },
      onWebSocketClose: () => {
        if (wsStatus.value !== 'disconnected') {
          setStatus('error');
          scheduleReconnect();
        }
      },
      onWebSocketError: () => {
        setStatus('error');
      },
    });

    try {
      stompClient.activate();
    } catch {
      setStatus('error');
      scheduleReconnect();
    }
  }

  /** 计划重连 */
  function scheduleReconnect() {
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      // 超过最大重连次数，降级为 HTTP 轮询
      console.warn('iMEC WebSocket 重连失败，降级为 HTTP 轮询');
      isPollingFallback.value = true;
      setStatus('error');
      startPolling();
      return;
    }

    reconnectAttempts++;
    setStatus('reconnecting');
    const delay = RECONNECT_DELAY_BASE * Math.min(reconnectAttempts, 5);

    reconnectTimer = setTimeout(() => {
      if (wsStatus.value === 'reconnecting' || wsStatus.value === 'error') {
        disconnectClient();
        connect();
      }
    }, delay);
  }

  /** 仅断开 STOMP 客户端（不改变状态） */
  function disconnectClient() {
    if (stompClient?.active) {
      stompClient.deactivate();
    }
    stompClient = null;
  }

  /** 断开连接 */
  function disconnect() {
    stopPolling();
    clearReconnectTimer();
    disconnectClient();
    setStatus('disconnected');
  }

  /** 清除重连定时器 */
  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  }

  /** 启动 HTTP 轮询回退 */
  function startPolling() {
    stopPolling();
    pollTimer = setInterval(async () => {
      const deviceId = getDeviceId();
      if (!deviceId) return;
      try {
        const res = await getImecLatestDataApi(deviceId);
        if (res.data) {
          monitorData.value = res.data;
          if (wsStatus.value !== 'connected' && wsStatus.value !== 'heartbeat') {
            setStatus('connected');
          }
          onMessage?.(res.data);
        }
      } catch {
        if (!isPollingFallback.value) {
          setStatus('error');
        }
      }
    }, 1000);
  }

  /** 停止轮询 */
  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  // 监听 deviceId 变化时重连（使用 getter 函数保持响应式）
  watch(
    () => toValue(options.deviceId),
    (newId, oldId) => {
      if (newId && newId !== oldId) {
        disconnect();
        monitorData.value = null;
      }
    },
  );

  onBeforeUnmount(() => {
    disconnect();
  });

  return {
    wsStatus,
    monitorData,
    connected,
    isPollingFallback,
    connect,
    disconnect,
  };
}
