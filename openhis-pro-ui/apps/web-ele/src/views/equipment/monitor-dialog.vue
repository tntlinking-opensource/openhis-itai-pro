<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

interface VitalSign {
  code: string;
  name: string;
  value: string;
  unit: string;
  referenceRange?: string;
}

interface CollectResult {
  id?: number;
  deviceName?: string;
  brand?: string;
  dataXml?: string;
  success?: boolean;
  patientId?: string;
  patientName?: string;
  patientGender?: string;
  birthDate?: string;
  bedNo?: string;
  patientClass?: string;
  visitType?: string;
  admitDate?: string;
  observeTime?: string;
  vitalSigns?: VitalSign[];
}

interface Props {
  visible?: boolean;
  data?: CollectResult | null;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  data: null,
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const show = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
});

/** 体征编码 → 颜色（模拟真实监护仪配色） */
const codeColorMap: Record<string, string> = {
  HR: '#00ff00', // 心率-绿色
  PR: '#00ff00', // 脉率-绿色
  SpO2: '#00ccff', // 血氧-青色
  RR: '#ffcc00', // 呼吸率-黄色
  T1: '#ff6600', // 体温-橙色
  T2: '#ff6600',
  TD: '#ff6600',
  NIBP_Sys: '#ff3333', // 血压-红色
  NIBP_Dia: '#ff3333',
  NIBP_Mean: '#ff3333',
  IBP_Sys: '#ff33ff', // 有创压-紫红
  IBP_Dia: '#ff33ff',
  IBP_Mean: '#ff33ff',
  CO2: '#ffff33', // CO2-亮黄
  EtCO2: '#ffff33',
  WEIGHT: '#cccccc',
  HEIGHT: '#cccccc',
  PI: '#00ccff',
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

/** 就诊者类别友好显示 */
const patientClassMap: Record<string, string> = {
  I: '住院',
  O: '门诊',
  E: '急救',
  R: '复诊',
  B: '产科',
  C: '往来账户',
  U: '未知',
};

/** 患者类型友好显示 */
const visitTypeMap: Record<string, string> = {
  adult: '成人',
  pediatrics: '儿童',
  neonate: '新生儿',
  None: '未知',
};

/** 性别友好显示 */
function formatGender(gender?: string): string {
  if (!gender) return '--';
  const map: Record<string, string> = { M: '男', F: '女', U: '未知', O: '其他' };
  return map[gender] || gender;
}

/** 获取就诊类别标签样式 */
function getPatientClassStyle(patientClass?: string): {
  text: string;
  color: string;
  bgColor: string;
} {
  const styleMap: Record<string, { text: string; color: string; bgColor: string }> = {
    I: { text: '住院', color: '#52c41a', bgColor: 'rgba(82, 196, 26, 0.15)' },
    O: { text: '门诊', color: '#1890ff', bgColor: 'rgba(24, 144, 255, 0.15)' },
    E: { text: '急救', color: '#ff4d4f', bgColor: 'rgba(255, 77, 79, 0.15)' },
    C: { text: '往来账户', color: '#faad14', bgColor: 'rgba(250, 173, 20, 0.15)' },
    B: { text: '住院', color: '#52c41a', bgColor: 'rgba(82, 196, 26, 0.15)' },
    U: { text: '未知', color: '#8c8c8c', bgColor: 'rgba(140, 140, 140, 0.15)' },
  };
  return (
    styleMap[patientClass || ''] || {
      text: patientClass || '--',
      color: '#8c8c8c',
      bgColor: 'rgba(140, 140, 140, 0.15)',
    }
  );
}

/** 判断体征是否在正常范围 */
function isNormal(sign: VitalSign): boolean {
  if (!sign.referenceRange || !sign.value) return true;
  const numVal = parseFloat(sign.value);
  if (isNaN(numVal)) return true;
  const ref = sign.referenceRange;
  if (ref.includes('-')) {
    const parts = ref.split('-');
    const low = parseFloat(parts[0]);
    const high = parseFloat(parts[1]);
    if (!isNaN(low) && !isNaN(high)) {
      return numVal >= low && numVal <= high;
    }
  }
  return true;
}

/** 按分类分组体征数据 */
const groupedSigns = computed(() => {
  if (!props.data?.vitalSigns) return {};
  const groups: Record<string, VitalSign[]> = {};
  for (const sign of props.data.vitalSigns) {
    const cat = categoryMap[sign.code] || 'other';
    if (!groups[cat]) groups[cat] = [];
    groups[cat].push(sign);
  }
  // 按 categoryOrder 排序
  const sorted: Record<string, VitalSign[]> = {};
  for (const cat of categoryOrder) {
    if (groups[cat]) sorted[cat] = groups[cat];
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
</script>

<template>
  <el-dialog
    v-model="show"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    width="860px"
    class="monitor-dialog"
    append-to-body
    destroy-on-close
  >
    <template #header>
      <div class="monitor-header-bar">
        <span class="monitor-title">实时监护数据</span>
        <div style="display: flex; align-items: center; gap: 8px">
          <span v-if="data?.brand" class="monitor-brand">{{ data.brand }}</span>
          <span v-if="data?.deviceName" class="monitor-device">{{ data.deviceName }}</span>
        </div>
      </div>
    </template>

    <div v-if="data" class="monitor-body">
      <!-- 顶部信息栏 - 分两行：关键信息 + 其他信息 -->
      <div class="monitor-info-bar">
        <!-- 关键信息行 -->
        <div class="info-row primary">
          <div class="info-item highlight">
            <span class="info-label">床位</span>
            <span class="info-value">{{ data.bedNo || '--' }}</span>
          </div>
          <div class="info-item highlight" v-if="data.patientName">
            <span class="info-label">姓名</span>
            <span class="info-value">{{ data.patientName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">患者ID</span>
            <span class="info-value">{{ data.patientId || '--' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">性别</span>
            <span class="info-value">{{ formatGender(data.patientGender) }}</span>
          </div>
          <div class="info-item" v-if="data.birthDate">
            <span class="info-label">出生日期</span>
            <span class="info-value">{{ data.birthDate }}</span>
          </div>
        </div>
        <!-- 次要信息行 -->
        <div class="info-row secondary">
          <div class="info-item">
            <span class="info-label">就诊类别</span>
            <span
              class="info-tag"
              :style="{
                color: getPatientClassStyle(data.patientClass).color,
                backgroundColor: getPatientClassStyle(data.patientClass).bgColor,
                borderColor: getPatientClassStyle(data.patientClass).color,
              }"
            >
              {{ getPatientClassStyle(data.patientClass).text }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">患者类型</span>
            <span class="info-value">{{
              visitTypeMap[data.visitType || ''] || data.visitType || '--'
            }}</span>
          </div>
          <div class="info-item" v-if="data.admitDate">
            <span class="info-label">入院日期</span>
            <span class="info-value">{{ data.admitDate }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">采集时间</span>
            <span class="info-value">{{ data.observeTime || '--' }}</span>
          </div>
        </div>
      </div>

      <!-- 体征数据区域 -->
      <div class="monitor-signs">
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

      <!-- 报警提示 -->
      <div v-if="data.vitalSigns?.some((s) => !isNormal(s))" class="alarm-bar">
        <span class="alarm-icon">&#9888;</span>
        <span
          >异常指标：{{
            data.vitalSigns
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
.monitor-dialog.el-dialog {
  background: #1a1a2e !important;
  border: 1px solid #333355;
  border-radius: 12px;
  overflow: hidden;
}

.monitor-dialog .el-dialog__header {
  background: #0f0f23;
  border-bottom: 1px solid #333355;
  padding: 0;
  margin: 0;
}

.monitor-dialog .el-dialog__body {
  background: #1a1a2e;
  padding: 0;
}

.monitor-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #888;
  font-size: 18px;
}

.monitor-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #fff;
}

.monitor-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
}

.monitor-title {
  font-size: 16px;
  font-weight: 600;
  color: #e0e0e0;
  letter-spacing: 2px;
}

.monitor-device {
  font-size: 13px;
  color: #00ccff;
  background: rgba(0, 204, 255, 0.1);
  padding: 2px 12px;
  border-radius: 12px;
  border: 1px solid rgba(0, 204, 255, 0.3);
}

.monitor-brand {
  font-size: 12px;
  font-weight: 600;
  color: #ffcc00;
  background: rgba(255, 204, 0, 0.12);
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(255, 204, 0, 0.3);
}

.monitor-body {
  padding: 0;
}

.monitor-info-bar {
  background: #0f0f23;
  border-bottom: 1px solid #2a2a44;
  padding: 0;
}

.info-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 0;
  padding: 0 20px;
}

.info-row.primary {
  background: rgba(0, 204, 255, 0.03);
  padding-top: 12px;
  padding-bottom: 12px;
  grid-template-columns: repeat(5, 1fr);
}

.info-row.secondary {
  padding-top: 10px;
  padding-bottom: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  grid-template-columns: repeat(4, 1fr);
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
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
  background: rgba(255, 255, 255, 0.08);
}

.info-item.highlight .info-value {
  font-size: 16px;
  color: #00ccff;
  font-weight: 600;
}

.info-item.highlight .info-label {
  color: #888;
}

.info-label {
  font-size: 11px;
  color: #666;
  letter-spacing: 1px;
  white-space: nowrap;
}

.info-value {
  font-size: 14px;
  color: #ccc;
  font-weight: 500;
  white-space: nowrap;
}

.info-tag {
  display: inline-block;
  font-size: 13px;
  font-weight: 600;
  padding: 3px 12px;
  border-radius: 12px;
  border: 1px solid;
  white-space: nowrap;
  transition: all 0.3s;
}

.monitor-signs {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 450px;
  overflow-y: auto;
}

/* 自定义滚动条 - 美化版 */
.monitor-signs::-webkit-scrollbar {
  width: 8px;
}

.monitor-signs::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 4px;
}

.monitor-signs::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #3a3a5a 0%, #2a2a4a 100%);
  border-radius: 4px;
  border: 2px solid rgba(255, 255, 255, 0.05);
  transition: all 0.3s;
}

.monitor-signs::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, #4a4a6a 0%, #3a3a5a 100%);
}

.monitor-signs::-webkit-scrollbar-corner {
  background: transparent;
}

.sign-category {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  padding: 12px 16px;
  border-left: 3px solid;
}

.category-label {
  font-size: 12px;
  font-weight: 600;
  min-width: 56px;
  padding-top: 4px;
  letter-spacing: 1px;
  opacity: 0.85;
}

.sign-items {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  flex: 1;
}

.sign-item {
  min-width: 120px;
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  border: 1px solid transparent;
  transition: all 0.3s;
}

.sign-item.sign-alarm {
  border-color: rgba(255, 0, 0, 0.4);
  background: rgba(255, 0, 0, 0.08);
  animation: alarm-pulse 1.5s ease-in-out infinite;
}

@keyframes alarm-pulse {
  0%,
  100% {
    border-color: rgba(255, 0, 0, 0.4);
  }
  50% {
    border-color: rgba(255, 0, 0, 0.8);
  }
}

.sign-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  opacity: 0.75;
  margin-bottom: 2px;
}

.sign-value-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.sign-value {
  font-size: 28px;
  font-weight: 700;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  line-height: 1.1;
  text-shadow: 0 0 8px currentColor;
}

.sign-unit {
  font-size: 12px;
  color: #888;
}

.sign-ref {
  font-size: 10px;
  color: #555;
  margin-top: 2px;
}

.alarm-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(255, 0, 0, 0.1);
  border-top: 1px solid rgba(255, 0, 0, 0.3);
  color: #ff4444;
  font-size: 13px;
  font-weight: 500;
}

.alarm-icon {
  font-size: 16px;
}
</style>
