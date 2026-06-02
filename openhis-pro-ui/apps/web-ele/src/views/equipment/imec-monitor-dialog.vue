<script lang="ts" setup>
import type {
  ImecAlarm,
  ImecAlarmLevel,
  ImecMonitorData,
  ImecVitalSign,
  ImecWsStatus,
} from '#/api';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

/** 测试报文（体征+波形+报警完整数据） */
const TEST_HL7_MESSAGE = `MSH|^~\\&|iMEC|Mindray|HIS|Hospital|20260414103025||ORU^R01|MSG00001|P|2.3.1\rPID||P001234^^^HIS||张三||19850315|M\rPV1||I|ICU-03^^^ICU^^^^bed1||||||||||||adult\rOBR|||OBS001|||20260414103020\rOBX|NM|CE|HR^心率^MMS||75|bpm|60-100|N|||F\rOBX|NM|CE|SpO2^血氧饱和度^MMS||98|%|95-100|N|||F\rOBX|NM|CE|RR^呼吸率^MMS||18|rpm|12-20|N|||F\rOBX|NM|CE|NIBP_Sys^收缩压^MMS||125|mmHg|90-140|N|||F\rOBX|NM|CE|NIBP_Dia^舒张压^MMS||82|mmHg|60-90|N|||F\rOBX|NM|CE|T1^体温1^MMS||36.8|°C|36.0-37.5|N|||F\rOBX|ED|CE|ECG^心电波形^MMS||200^0.05 0.12 0.08 -0.03 -0.15 -0.22 -0.18 -0.05 0.10 0.25 0.35 0.30 0.15 0.02 -0.08 -0.12 -0.10 -0.04 0.03 0.08 0.05 0.01 -0.02 -0.01 0.00 0.02 0.04 0.03 0.01 -0.01\rOBX|ED|CE|SpO2^血氧波形^MMS||100^0.92 0.94 0.95 0.96 0.97 0.97 0.98 0.98 0.97 0.96 0.95 0.94\rOBX|ED|CE|Resp^呼吸波形^MMS||50^0.10 0.20 0.35 0.50 0.60 0.55 0.40 0.25 0.15 0.10\rOBX|ST|CE|AlarmPhy^生理报警^MMS||HR过高^^MMS|||1^高^MMS||N`;

import { ElMessage } from 'element-plus';

import { acknowledgeImecAlarmApi, injectImecTestDataApi, startImecCollectApi, stopImecCollectApi } from '#/api';

import { useImecWebSocket } from './composables/use-imec-websocket';
import WaveformCanvas from './imec-waveform-canvas.vue';

interface Props {
  visible?: boolean;
  deviceId?: string;
  data?: ImecMonitorData | null;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  deviceId: '',
  data: null,
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const show = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
});

// ==================== WebSocket 连接管理 ====================

const collecting = ref(false);

/** WebSocket 连接 composable */
const {
  wsStatus,
  monitorData: wsMonitorData,
  connected: wsConnected,
  isPollingFallback,
  connect: wsConnect,
  disconnect: wsDisconnect,
} = useImecWebSocket({
  deviceId: () => props.deviceId,
  onMessage: (_data) => {
    // WebSocket 推送数据已自动更新 wsMonitorData
  },
  onStatusChange: (_status) => {
    // 状态已自动更新 wsStatus
  },
});

/** 合并 props 传入的初始数据与 WebSocket 实时数据 */
const monitorData = computed(() => wsMonitorData.value || props.data);

/** 弹窗打开时，如果设备已有采集数据则自动连接 WebSocket */
watch(
  () => props.visible,
  (visible) => {
    if (visible && props.deviceId) {
      wsConnect();
    }
  },
  { immediate: true },
);

/** 初始化数据 */
watch(
  () => props.data,
  (val) => {
    if (val && !wsMonitorData.value) {
      wsStatus.value = val.wsStatus || 'connected';
    }
  },
  { immediate: true },
);

/** 启动持续采集 */
async function startCollect() {
  if (!props.deviceId) return;
  collecting.value = true;
  try {
    const res = await startImecCollectApi(props.deviceId);
    if (res.data?.connected) {
      // 启动 WebSocket 连接接收实时数据
      wsConnect();
      ElMessage.success('已启动持续采集');
    } else {
      ElMessage.error('设备连接失败');
    }
  } catch (error) {
    console.error('启动采集失败:', error);
    ElMessage.error('启动采集失败');
  } finally {
    collecting.value = false;
  }
}

/** 停止持续采集 */
async function stopCollect() {
  if (!props.deviceId) return;
  collecting.value = true;
  try {
    await stopImecCollectApi(props.deviceId);
    wsDisconnect();
    wsMonitorData.value = null;
    wsStatus.value = 'disconnected';
    ElMessage.success('已停止采集');
  } catch (error) {
    console.error('停止采集失败:', error);
    ElMessage.error('停止采集失败');
  } finally {
    collecting.value = false;
  }
}

/** 关闭时断开 WebSocket 并清除数据 */
watch(show, (val) => {
  if (!val) {
    wsDisconnect();
    wsMonitorData.value = null;
  }
});

onBeforeUnmount(() => {
  wsDisconnect();
});

// ==================== 体征数据展示 ====================

/** 体征编码 → 颜色 */
const codeColorMap: Record<string, string> = {
  HR: '#00ff00',
  PR: '#00ff00',
  SpO2: '#00ccff',
  PI: '#00ccff',
  RR: '#ffcc00',
  NIBP_Sys: '#ff3333',
  NIBP_Dia: '#ff3333',
  NIBP_Mean: '#ff3333',
  IBP_Sys: '#ff33ff',
  IBP_Dia: '#ff33ff',
  IBP_Mean: '#ff33ff',
  CVP: '#ff33ff',
  T1: '#ff6600',
  T2: '#ff6600',
  TD: '#ff6600',
  CO2: '#ffff33',
  EtCO2: '#ffff33',
  FiCO2: '#ffff33',
  WEIGHT: '#cccccc',
  HEIGHT: '#cccccc',
};

/** 体征分类 */
const categoryMap: Record<string, string> = {
  HR: 'ecg',
  PR: 'ecg',
  SpO2: 'spo2',
  PI: 'spo2',
  RR: 'resp',
  NIBP_Sys: 'nibp',
  NIBP_Dia: 'nibp',
  NIBP_Mean: 'nibp',
  IBP_Sys: 'ibp',
  IBP_Dia: 'ibp',
  IBP_Mean: 'ibp',
  CVP: 'ibp',
  T1: 'temp',
  T2: 'temp',
  TD: 'temp',
  CO2: 'co2',
  EtCO2: 'co2',
  FiCO2: 'co2',
  WEIGHT: 'other',
  HEIGHT: 'other',
};

/** 分类显示名称 */
const categoryNameMap: Record<string, string> = {
  ecg: '心电',
  spo2: '血氧',
  resp: '呼吸',
  nibp: '无创血压',
  ibp: '有创血压',
  temp: '体温',
  co2: 'CO2',
  other: '其他',
};

/** 分类排序 */
const categoryOrder = ['ecg', 'spo2', 'nibp', 'ibp', 'temp', 'resp', 'co2', 'other'];

/** 连接状态文本映射 */
const wsStatusMap: Record<ImecWsStatus, { color: string; text: string }> = {
  disconnected: { text: '未连接', color: '#888' },
  connecting: { text: '连接中', color: '#ffcc00' },
  connected: { text: '已连接', color: '#52c41a' },
  heartbeat: { text: '心跳中', color: '#00ccff' },
  reconnecting: { text: '重连中', color: '#ffcc00' },
  error: { text: '连接异常', color: '#ff4d4f' },
};

/** 就诊者类别 */
const patientClassMap: Record<string, { bgColor: string; color: string; text: string }> = {
  I: { text: '住院', color: '#52c41a', bgColor: 'rgba(82,196,26,0.15)' },
  O: { text: '门诊', color: '#1890ff', bgColor: 'rgba(24,144,255,0.15)' },
  E: { text: '急救', color: '#ff4d4f', bgColor: 'rgba(255,77,79,0.15)' },
  B: { text: '产科', color: '#52c41a', bgColor: 'rgba(82,196,26,0.15)' },
  U: { text: '未知', color: '#8c8c8c', bgColor: 'rgba(140,140,140,0.15)' },
};

/** 患者类型 */
const visitTypeMap: Record<string, string> = {
  adult: '成人',
  pediatrics: '儿童',
  neonate: '新生儿',
};

/** 性别 */
function formatGender(gender?: string): string {
  if (!gender) return '--';
  const map: Record<string, string> = { M: '男', F: '女', U: '未知', O: '其他' };
  return map[gender] || gender;
}

/** 判断体征是否在正常范围 */
function isNormal(sign: ImecVitalSign): boolean {
  if (!sign.referenceRange || !sign.value) return true;
  const numVal = Number.parseFloat(sign.value);
  if (Number.isNaN(numVal)) return true;
  const ref = sign.referenceRange;
  if (ref.includes('-')) {
    const parts = ref.split('-');
    const low = Number.parseFloat(parts[0] ?? '');
    const high = Number.parseFloat(parts[1] ?? '');
    if (!Number.isNaN(low) && !Number.isNaN(high)) return numVal >= low && numVal <= high;
  }
  return true;
}

/** 按分类分组体征数据 */
const groupedSigns = computed(() => {
  const signs = monitorData.value?.vitalSigns;
  if (!signs) return {};
  const groups: Record<string, ImecVitalSign[]> = {};
  for (const sign of signs) {
    const cat = categoryMap[sign.code] || 'other';
    if (!groups[cat]) groups[cat] = [];
    groups[cat]!.push(sign);
  }
  const sorted: Record<string, ImecVitalSign[]> = {};
  for (const cat of categoryOrder) {
    if (groups[cat]) sorted[cat] = groups[cat]!;
  }
  return sorted;
});

/** 获取体征颜色 */
function getColor(code: string): string {
  return codeColorMap[code] || '#00ff00';
}

/** 获取分类标签颜色 */
function getCategoryColor(cat: string): string {
  const map: Record<string, string> = {
    ecg: '#00ff00',
    spo2: '#00ccff',
    resp: '#ffcc00',
    nibp: '#ff3333',
    ibp: '#ff33ff',
    temp: '#ff6600',
    co2: '#ffff33',
    other: '#cccccc',
  };
  return map[cat] || '#00ff00';
}

// ==================== 报警管理 ====================

const activeAlarms = computed(
  () => monitorData.value?.alarms?.filter((a) => !a.acknowledged) || [],
);
const physioAlarms = computed(() => activeAlarms.value.filter((a) => a.type === 'physiological'));
const techAlarms = computed(() => activeAlarms.value.filter((a) => a.type === 'technical'));

/** 报警级别样式 */
function alarmLevelStyle(level: ImecAlarmLevel): { bgColor: string; color: string } {
  const map: Record<ImecAlarmLevel, { bgColor: string; color: string }> = {
    low: { color: '#ffcc00', bgColor: 'rgba(255,204,0,0.12)' },
    medium: { color: '#ff8800', bgColor: 'rgba(255,136,0,0.12)' },
    high: { color: '#ff4d4f', bgColor: 'rgba(255,77,79,0.15)' },
  };
  return map[level] || map.low;
}

/** 确认报警 */
async function handleAckAlarm(alarm: ImecAlarm) {
  if (!props.deviceId) return;
  try {
    await acknowledgeImecAlarmApi(props.deviceId, alarm.id);
    if (monitorData.value?.alarms) {
      const target = monitorData.value.alarms.find((a) => a.id === alarm.id);
      if (target) target.acknowledged = true;
    }
  } catch (error) {
    console.error('确认报警失败:', error);
  }
}

// ==================== 测试数据注入 ====================

const testLoading = ref(false);

/** 注入测试数据（开发调试用，无需连接真实监护仪） */
async function handleInjectTest() {
  if (!props.deviceId) {
    ElMessage.warning('缺少设备ID');
    return;
  }
  testLoading.value = true;
  try {
    const res = await injectImecTestDataApi(props.deviceId, TEST_HL7_MESSAGE);
    console.warn('[iMEC] 注入测试数据: vitalSigns=%d, waveforms=%d, alarms=%d',
      res?.data?.vitalSigns?.length, res?.data?.waveforms?.length, res?.data?.alarms?.length);
    if (res.data) {
      wsMonitorData.value = res.data;
      wsStatus.value = 'connected';
      ElMessage.success('测试数据已注入');
    } else {
      console.error('[iMEC] res.data 为空');
      ElMessage.warning('注入返回数据为空');
    }
  } catch (error: any) {
    console.error('[iMEC] 注入测试数据失败:', error);
    const msg = error?.response?.data?.message || error?.message || '注入测试数据失败';
    ElMessage.error(msg);
  } finally {
    testLoading.value = false;
  }
}

// ==================== 波形数据 ====================

const waveforms = computed(() => monitorData.value?.waveforms || []);
const hasWaveforms = computed(() => waveforms.value.length > 0);

// ==================== 连接状态指示 ====================

const connectionModeText = computed(() => {
  if (isPollingFallback.value) return 'HTTP轮询';
  if (wsConnected.value) return 'WebSocket';
  return '--';
});
</script>

<template>
  <el-dialog
    v-model="show"
    :show-close="true"
    :close-on-click-modal="false"
    :close-on-press-escape="true"
    width="960px"
    class="imec-monitor-dialog"
    append-to-body
    destroy-on-close
  >
    <template #header>
      <div class="imec-header-bar">
        <div class="imec-header-left">
          <span class="imec-title">实时监护数据</span>
          <span class="imec-brand-tag">Mindray</span>
        </div>
        <div class="imec-header-right">
          <span class="imec-ws-status" :style="{ color: wsStatusMap[wsStatus]?.color }">
            ● {{ wsStatusMap[wsStatus]?.text }}
          </span>
          <span v-if="wsConnected || isPollingFallback" class="imec-mode-tag">
            {{ connectionModeText }}
          </span>
          <span v-if="monitorData?.deviceName" class="imec-device-tag">{{
            monitorData.deviceName
          }}</span>
          <el-button
            v-if="wsStatus === 'disconnected' || wsStatus === 'error'"
            type="success"
            size="small"
            :loading="collecting"
            @click="startCollect"
          >
            启动采集
          </el-button>
          <el-button v-else type="warning" size="small" :loading="collecting" @click="stopCollect">
            停止采集
          </el-button>
          <el-button type="info" size="small" @click="handleInjectTest">
            注入测试数据
          </el-button>
        </div>
      </div>
    </template>

    <div v-if="monitorData" class="imec-body">
      <!-- 患者信息栏 -->
      <div class="imec-info-bar">
        <div class="info-row primary">
          <div class="info-item highlight">
            <span class="info-label">床位</span>
            <span class="info-value">{{ monitorData.bedNo || '--' }}</span>
          </div>
          <div class="info-item highlight" v-if="monitorData.patientName">
            <span class="info-label">姓名</span>
            <span class="info-value">{{ monitorData.patientName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">患者ID</span>
            <span class="info-value">{{ monitorData.patientId || '--' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">性别</span>
            <span class="info-value">{{ formatGender(monitorData.patientGender) }}</span>
          </div>
          <div class="info-item" v-if="monitorData.birthDate">
            <span class="info-label">出生日期</span>
            <span class="info-value">{{ monitorData.birthDate }}</span>
          </div>
        </div>
        <div class="info-row secondary">
          <div class="info-item">
            <span class="info-label">就诊类别</span>
            <span
              class="info-tag"
              :style="
                patientClassMap[
                  (monitorData.patientClass || 'U') as keyof typeof patientClassMap
                ] || patientClassMap.U
              "
            >
              {{
                patientClassMap[(monitorData.patientClass || 'U') as keyof typeof patientClassMap]
                  ?.text || '--'
              }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">患者类型</span>
            <span class="info-value">{{
              visitTypeMap[monitorData.visitType || ''] || monitorData.visitType || '--'
            }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">采集时间</span>
            <span class="info-value">{{ monitorData.observeTime || '--' }}</span>
          </div>
        </div>
      </div>

      <!-- 报警区域（有报警时显示） -->
      <div v-if="activeAlarms.length > 0" class="imec-alarm-section">
        <div v-if="physioAlarms.length > 0" class="alarm-group physio">
          <div class="alarm-group-title">生理报警</div>
          <div class="alarm-list">
            <div
              v-for="alarm in physioAlarms"
              :key="alarm.id"
              class="alarm-item"
              :style="{
                color: alarmLevelStyle(alarm.level).color,
                backgroundColor: alarmLevelStyle(alarm.level).bgColor,
              }"
            >
              <span class="alarm-level">{{
                alarm.level === 'high' ? '!!!' : alarm.level === 'medium' ? '!!' : '!'
              }}</span>
              <span class="alarm-msg">{{ alarm.message }}</span>
              <span v-if="alarm.value" class="alarm-value"
                >{{ alarm.value }}{{ alarm.unit || '' }}</span
              >
              <el-button type="primary" link size="small" @click="handleAckAlarm(alarm)">
                确认
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="techAlarms.length > 0" class="alarm-group tech">
          <div class="alarm-group-title">技术报警</div>
          <div class="alarm-list">
            <div
              v-for="alarm in techAlarms"
              :key="alarm.id"
              class="alarm-item"
              :style="{
                color: alarmLevelStyle(alarm.level).color,
                backgroundColor: alarmLevelStyle(alarm.level).bgColor,
              }"
            >
              <span class="alarm-level">{{
                alarm.level === 'high' ? '!!!' : alarm.level === 'medium' ? '!!' : '!'
              }}</span>
              <span class="alarm-msg">{{ alarm.message }}</span>
              <el-button type="primary" link size="small" @click="handleAckAlarm(alarm)">
                确认
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 波形区域 -->
      <div v-if="hasWaveforms" class="imec-waveform-section">
        <div class="waveform-title">实时波形</div>
        <div class="waveform-container">
          <WaveformCanvas
            v-for="wf in waveforms"
            :key="wf.code"
            :waveform="wf"
            :color="
              wf.color ||
              (wf.code === 'ECG' ? '#00ff00' : wf.code === 'SpO2' ? '#00ccff' : '#ffcc00')
            "
          />
        </div>
      </div>

      <!-- 体征数据区域 -->
      <div class="imec-signs">
        <div
          v-for="(signs, cat) in groupedSigns"
          :key="cat"
          class="sign-category"
          :style="{ borderLeftColor: getCategoryColor(cat) }"
        >
          <div class="category-label" :style="{ color: getCategoryColor(cat) }">
            {{ categoryNameMap[cat] || cat }}
          </div>
          <div class="sign-items">
            <div
              v-for="sign in signs"
              :key="sign.code"
              class="sign-item"
              :class="{ 'sign-alarm': !isNormal(sign) }"
            >
              <div class="sign-label" :style="{ color: getColor(sign.code) }">
                {{ sign.name }}
              </div>
              <div class="sign-value-row">
                <span
                  class="sign-value"
                  :style="{ color: isNormal(sign) ? getColor(sign.code) : '#ff0000' }"
                >
                  {{ sign.value || '--' }}
                </span>
                <span class="sign-unit">{{ sign.unit || '' }}</span>
              </div>
              <div v-if="sign.referenceRange" class="sign-ref">
                {{ sign.referenceRange }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 异常指标汇总 -->
      <div v-if="monitorData.vitalSigns?.some((s) => !isNormal(s))" class="imec-alarm-bar">
        <span class="alarm-icon">&#9888;</span>
        <span
          >异常指标：{{
            monitorData.vitalSigns
              .filter((s) => !isNormal(s))
              .map((s) => s.name)
              .join('、')
          }}</span
        >
      </div>
    </div>
  </el-dialog>
</template>

<style>
/* 非 scoped：el-dialog append-to-body 后 DOM 在 body 下 */
.imec-monitor-dialog.el-dialog {
  background: #0d1b2a !important;
  border: 1px solid #1b3a5c;
  border-radius: 12px;
  overflow: hidden;
}

.imec-monitor-dialog .el-dialog__header {
  background: #071220;
  border-bottom: 1px solid #1b3a5c;
  padding: 0;
  margin: 0;
}

.imec-monitor-dialog .el-dialog__body {
  background: #0d1b2a;
  padding: 0;
  max-height: 70vh;
  overflow-y: auto;
}

.imec-monitor-dialog .el-dialog__body::-webkit-scrollbar {
  width: 4px;
}

.imec-monitor-dialog .el-dialog__body::-webkit-scrollbar-track {
  background: transparent;
}

.imec-monitor-dialog .el-dialog__body::-webkit-scrollbar-thumb {
  background: rgba(0, 204, 255, 0.2);
  border-radius: 2px;
}

.imec-monitor-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #5a7a9a;
  font-size: 18px;
}

.imec-monitor-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #9ac4e8;
}

.imec-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
}

.imec-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.imec-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.imec-title {
  font-size: 15px;
  font-weight: 600;
  color: #b0d0e8;
  letter-spacing: 2px;
}

.imec-brand-tag {
  font-size: 11px;
  font-weight: 700;
  color: #00ccff;
  background: rgba(0, 204, 255, 0.12);
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(0, 204, 255, 0.35);
  letter-spacing: 1px;
}

.imec-device-tag {
  font-size: 12px;
  color: #7ab8d8;
  background: rgba(122, 184, 216, 0.1);
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(122, 184, 216, 0.25);
}

.imec-ws-status {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
}

.imec-mode-tag {
  font-size: 10px;
  font-weight: 600;
  color: #52c41a;
  background: rgba(82, 196, 26, 0.12);
  padding: 1px 8px;
  border-radius: 8px;
  border: 1px solid rgba(82, 196, 26, 0.3);
  letter-spacing: 0.5px;
}

.imec-body {
  padding: 0;
}

/* 患者信息栏 */
.imec-info-bar {
  background: #071220;
  border-bottom: 1px solid #1b3a5c;
}

.info-row {
  display: grid;
  gap: 0;
  padding: 0 20px;
}

.info-row.primary {
  background: rgba(0, 204, 255, 0.02);
  padding-top: 10px;
  padding-bottom: 10px;
  grid-template-columns: repeat(5, 1fr);
}

.info-row.secondary {
  padding-top: 8px;
  padding-bottom: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
  grid-template-columns: repeat(3, 1fr);
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 0 12px;
  position: relative;
}

.info-item:not(:last-child)::after {
  content: '';
  position: absolute;
  right: 0;
  top: 15%;
  bottom: 15%;
  width: 1px;
  background: rgba(255, 255, 255, 0.06);
}

.info-item.highlight .info-value {
  font-size: 15px;
  color: #00ccff;
  font-weight: 600;
}

.info-item.highlight .info-label {
  color: #5a7a9a;
}

.info-label {
  font-size: 10px;
  color: #4a6a8a;
  letter-spacing: 1px;
  white-space: nowrap;
}

.info-value {
  font-size: 13px;
  color: #9ab8d0;
  font-weight: 500;
  white-space: nowrap;
}

.info-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid;
  white-space: nowrap;
}

/* 报警区域 */
.imec-alarm-section {
  border-bottom: 1px solid #1b3a5c;
  padding: 8px 16px;
  display: flex;
  gap: 12px;
  background: rgba(255, 77, 79, 0.03);
}

.alarm-group {
  flex: 1;
}

.alarm-group-title {
  font-size: 11px;
  font-weight: 600;
  color: #ff8800;
  margin-bottom: 4px;
  letter-spacing: 1px;
}

.alarm-group.tech .alarm-group-title {
  color: #ffcc00;
}

.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.alarm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  animation: imec-alarm-pulse 2s ease-in-out infinite;
}

@keyframes imec-alarm-pulse {
  0%,
  100% {
    opacity: 0.9;
  }
  50% {
    opacity: 1;
  }
}

.alarm-level {
  font-weight: 700;
  font-size: 10px;
}

.alarm-msg {
  flex: 1;
  font-weight: 500;
}

.alarm-value {
  font-family: 'Consolas', monospace;
  font-weight: 600;
}

/* 波形区域 */
.imec-waveform-section {
  border-bottom: 1px solid #1b3a5c;
  padding: 8px 16px;
}

.waveform-title {
  font-size: 11px;
  font-weight: 600;
  color: #5a7a9a;
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.waveform-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 体征数据区域 */
.imec-signs {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.imec-signs::-webkit-scrollbar {
  width: 4px;
}

.imec-signs::-webkit-scrollbar-track {
  background: transparent;
}

.imec-signs::-webkit-scrollbar-thumb {
  background: rgba(0, 204, 255, 0.2);
  border-radius: 2px;
}

.sign-category {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 6px;
  padding: 10px 14px;
  border-left: 3px solid;
}

.category-label {
  font-size: 11px;
  font-weight: 600;
  min-width: 50px;
  padding-top: 3px;
  letter-spacing: 1px;
  opacity: 0.85;
}

.sign-items {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  flex: 1;
}

.sign-item {
  min-width: 110px;
  padding: 5px 10px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 5px;
  border: 1px solid transparent;
  transition: all 0.3s;
}

.sign-item.sign-alarm {
  border-color: rgba(255, 0, 0, 0.4);
  background: rgba(255, 0, 0, 0.06);
  animation: imec-sign-alarm 1.5s ease-in-out infinite;
}

@keyframes imec-sign-alarm {
  0%,
  100% {
    border-color: rgba(255, 0, 0, 0.4);
  }
  50% {
    border-color: rgba(255, 0, 0, 0.8);
  }
}

.sign-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  opacity: 0.7;
  margin-bottom: 1px;
}

.sign-value-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.sign-value {
  font-size: 26px;
  font-weight: 700;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  line-height: 1.1;
  text-shadow: 0 0 6px currentColor;
}

.sign-unit {
  font-size: 11px;
  color: #5a7a9a;
}

.sign-ref {
  font-size: 9px;
  color: #3a5a7a;
  margin-top: 1px;
}

/* 底部报警汇总 */
.imec-alarm-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  background: rgba(255, 0, 0, 0.08);
  border-top: 1px solid rgba(255, 0, 0, 0.25);
  color: #ff6666;
  font-size: 12px;
  font-weight: 500;
}

.alarm-icon {
  font-size: 14px;
}
</style>
