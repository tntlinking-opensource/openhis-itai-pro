<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

defineOptions({
  name: 'HealthPortraitDemoPage',
});

type HealthLevel = 'mild' | 'moderate' | 'normal' | 'severe';
type MarkerKey =
  | 'heart'
  | 'kidney'
  | 'leftArm'
  | 'leftLeg'
  | 'liver'
  | 'lung'
  | 'rightArm'
  | 'rightLeg'
  | 'spleen';

interface MarkerTemplateItem {
  key: MarkerKey;
  label: string;
  x: number;
  y: number;
}

interface PatientMarkerState {
  key: MarkerKey;
  level: HealthLevel;
  value: string;
}

interface PatientDemoItem {
  id: string;
  name: string;
  careType: string;
  checkDate: string;
  reviewer: string;
  scaleScore: string;
  dietAdvice: string;
  carePathTip: string;
  markerStates: PatientMarkerState[];
}

interface RenderMarkerItem extends MarkerTemplateItem, PatientMarkerState {
  symbol: string;
  text: string;
}

const markerTemplate: MarkerTemplateItem[] = [
  { key: 'heart', label: '心', x: 50, y: 28 },
  { key: 'liver', label: '肝', x: 57, y: 38 },
  { key: 'spleen', label: '脾', x: 44, y: 38 },
  { key: 'lung', label: '肺', x: 50, y: 35 },
  { key: 'kidney', label: '肾', x: 50, y: 52 },
  { key: 'leftArm', label: '左臂', x: 28, y: 42 },
  { key: 'rightArm', label: '右臂', x: 72, y: 42 },
  { key: 'leftLeg', label: '左腿', x: 43, y: 78 },
  { key: 'rightLeg', label: '右腿', x: 57, y: 78 },
];

const levelMap: Record<HealthLevel, { symbol: string; text: string }> = {
  normal: { symbol: '', text: '正常' },
  mild: { symbol: '△', text: '轻度异常' },
  moderate: { symbol: '▲', text: '中度异常' },
  severe: { symbol: '！', text: '重度异常' },
};

const patientOptions: PatientDemoItem[] = [
  {
    id: 'p-001',
    name: '刘阿姨',
    careType: '全自理',
    checkDate: '2026-04-21',
    reviewer: '李护士',
    scaleScore: 'Barthel 92分',
    dietAdvice: '枸杞菊花茶（适配肝肾功能偏颇）',
    carePathTip: '每日监测血糖1次，每周复查肝肾功能',
    markerStates: [
      { key: 'heart', level: 'normal', value: '心率 72次/分' },
      { key: 'liver', level: 'normal', value: '肝功能平稳' },
      { key: 'spleen', level: 'normal', value: '脾功能状态稳定' },
      { key: 'lung', level: 'normal', value: '呼吸 18次/分' },
      { key: 'kidney', level: 'mild', value: '肌酐 116umol/L' },
      { key: 'leftArm', level: 'normal', value: '肌力 5级，活动稳定' },
      { key: 'rightArm', level: 'normal', value: '肌力 5级，活动稳定' },
      { key: 'leftLeg', level: 'mild', value: '肌力下降，步态轻缓' },
      { key: 'rightLeg', level: 'normal', value: '步态稳定' },
    ],
  },
  {
    id: 'p-002',
    name: '王爷爷',
    careType: '半失能',
    checkDate: '2026-04-21',
    reviewer: '赵护士',
    scaleScore: 'Barthel 66分',
    dietAdvice: '山药薏米粥（兼顾脾肾调理）',
    carePathTip: '每日监测心率与步态，每周评估肝肾指标',
    markerStates: [
      { key: 'heart', level: 'mild', value: '心率 91次/分' },
      { key: 'liver', level: 'moderate', value: '转氨酶 76U/L' },
      { key: 'spleen', level: 'normal', value: '脾功能状态稳定' },
      { key: 'lung', level: 'normal', value: '呼吸 19次/分' },
      { key: 'kidney', level: 'moderate', value: '肌酐 145umol/L' },
      { key: 'leftArm', level: 'mild', value: '上肢轻度无力' },
      { key: 'rightArm', level: 'normal', value: '右臂活动可' },
      { key: 'leftLeg', level: 'moderate', value: '步行需辅助' },
      { key: 'rightLeg', level: 'mild', value: '右腿乏力' },
    ],
  },
  {
    id: 'p-003',
    name: '张奶奶',
    careType: '全失能',
    checkDate: '2026-04-21',
    reviewer: '王护士',
    scaleScore: 'Barthel 38分',
    dietAdvice: '黄芪枣仁饮（重在扶正与体力维护）',
    carePathTip: '每日两次生命体征监测，每周复查肝肾及营养状态',
    markerStates: [
      { key: 'heart', level: 'moderate', value: '心率 108次/分' },
      { key: 'liver', level: 'moderate', value: '转氨酶 83U/L' },
      { key: 'spleen', level: 'mild', value: '脾功能轻度偏颇' },
      { key: 'lung', level: 'mild', value: '呼吸 23次/分' },
      { key: 'kidney', level: 'severe', value: '肌酐 198umol/L' },
      { key: 'leftArm', level: 'moderate', value: '左臂活动受限' },
      { key: 'rightArm', level: 'moderate', value: '右臂活动受限' },
      { key: 'leftLeg', level: 'severe', value: '左腿重度乏力' },
      { key: 'rightLeg', level: 'moderate', value: '右腿中度乏力' },
    ],
  },
];
const route = useRoute();
const router = useRouter();

const markerLabelMap: Record<MarkerKey, string> = {} as Record<MarkerKey, string>;
for (const marker of markerTemplate) {
  markerLabelMap[marker.key] = marker.label;
}

/** 补齐患者的五脏四肢默认状态，保证画像点位始终完整显示。 */
function normalizeMarkerStates(states: PatientMarkerState[]) {
  const stateMap = new Map<MarkerKey, PatientMarkerState>();
  states.forEach((state) => {
    stateMap.set(state.key, state);
  });
  return markerTemplate.map((item) => {
    const current = stateMap.get(item.key);
    return (
      current ?? {
        key: item.key,
        level: 'normal' as HealthLevel,
        value: '待填写',
      }
    );
  });
}

const patientRecords = ref<PatientDemoItem[]>(
  patientOptions.map((item) => ({
    ...item,
    markerStates: normalizeMarkerStates(item.markerStates),
  })),
);

const activePatientId = ref(patientRecords.value[0]?.id ?? '');
const activeMarkerKey = ref<'' | MarkerKey>('');

const activePatient = computed(() => {
  return (
    patientRecords.value.find((item) => item.id === activePatientId.value) ??
    patientRecords.value[0]
  );
});

const markerStateMap = computed(() => {
  const stateMap = new Map<MarkerKey, PatientMarkerState>();
  // 将患者状态转换为映射结构，保证模板层渲染读取稳定。
  activePatient.value?.markerStates.forEach((state) => {
    stateMap.set(state.key, state);
  });
  return stateMap;
});

const renderMarkers = computed<RenderMarkerItem[]>(() => {
  // 按模板顺序拼装位置与状态，避免卡片和图层顺序抖动。
  return markerTemplate.map((templateItem) => {
    const state = markerStateMap.value.get(templateItem.key);
    const level = state?.level ?? 'normal';
    return {
      ...templateItem,
      key: templateItem.key,
      level,
      value: state?.value ?? '-',
      symbol: levelMap[level].symbol,
      text: levelMap[level].text,
    };
  });
});

const torsoMarkers = computed(() => renderMarkers.value);

const editorMarkerKey = ref<MarkerKey>('heart');
const editorMarkerLevel = ref<HealthLevel>('normal');
const editorMarkerValue = ref('');
const markerPopoverVisible = ref(false);

const markerEditorOptions = computed(() => {
  return markerTemplate.map((item) => ({ label: item.label, value: item.key }));
});

const statusStats = computed(() => {
  const stats: Record<HealthLevel, number> = {
    normal: 0,
    mild: 0,
    moderate: 0,
    severe: 0,
  };
  // 汇总当前患者状态数量，底部信息条用于异常分布展示。
  renderMarkers.value.forEach((marker) => {
    stats[marker.level] += 1;
  });
  return stats;
});

const activeMarker = computed(() => {
  return renderMarkers.value.find((item) => item.key === activeMarkerKey.value) ?? null;
});

const abnormalTags = computed(() => {
  return renderMarkers.value
    .filter((item) => item.level !== 'normal')
    .map((item) => `${item.label}${item.text}`)
    .slice(0, 6);
});

const detailSummaryText = computed(() => {
  return renderMarkers.value.map((item) => `${item.label}${item.text}`).join('，');
});

/** 处理患者切换并清空当前焦点。 */
function handlePatientChange() {
  activeMarkerKey.value = '';
  handleSelectEditorMarker(editorMarkerKey.value);
}

/** 处理点位或卡片点击，聚焦当前健康项。 */
function handleFocusMarker(markerKey: MarkerKey) {
  activeMarkerKey.value = markerKey;
  handleSelectEditorMarker(markerKey);
  markerPopoverVisible.value = true;
}

/** 计算统一样式类，复用于侧边卡和中心点位。 */
function getMarkerClass(marker: RenderMarkerItem) {
  return {
    'is-active': activeMarkerKey.value === marker.key,
    [`is-${marker.level}`]: true,
  };
}

/** 在编辑器里切换器官，并回填当前器官已有数据。 */
function handleSelectEditorMarker(markerKey: MarkerKey) {
  editorMarkerKey.value = markerKey;
  const current = markerStateMap.value.get(markerKey);
  editorMarkerLevel.value = current?.level ?? 'normal';
  editorMarkerValue.value = current?.value ?? '';
}

/** 编辑器下拉选择器官时同步回填该器官当前数据。 */
function handleEditorMarkerChange(markerKey: MarkerKey) {
  handleSelectEditorMarker(markerKey);
}

/** 保存当前器官填写数据，并同步更新画像与列表。 */
function handleApplyMarkerData() {
  const patient = activePatient.value;
  if (!patient) {
    return;
  }
  const target = patient.markerStates.find((item) => item.key === editorMarkerKey.value);
  if (!target) {
    return;
  }
  target.level = editorMarkerLevel.value;
  target.value = editorMarkerValue.value.trim() || '待填写';
  activeMarkerKey.value = editorMarkerKey.value;
  markerPopoverVisible.value = false;
}

// 根据档案来源的老人 ID 生成画像 mock 点位，确保跳转后可直接演示。
function getArchiveMockMarkerStates(elderId: string): PatientMarkerState[] {
  const mockByElderId: Record<string, PatientMarkerState[]> = {
    'ELD-0001': [
      { key: 'heart', level: 'mild', value: '心率 92次/分' },
      { key: 'liver', level: 'normal', value: '肝功能稳定' },
      { key: 'spleen', level: 'normal', value: '脾功能正常' },
      { key: 'lung', level: 'normal', value: '呼吸 19次/分' },
      { key: 'kidney', level: 'mild', value: '肌酐 122umol/L' },
      { key: 'leftArm', level: 'normal', value: '左臂活动可' },
      { key: 'rightArm', level: 'normal', value: '右臂活动可' },
      { key: 'leftLeg', level: 'mild', value: '步态稍慢，需助行' },
      { key: 'rightLeg', level: 'normal', value: '右腿力量正常' },
    ],
    'ELD-0002': [
      { key: 'heart', level: 'normal', value: '心率 80次/分' },
      { key: 'liver', level: 'mild', value: '转氨酶轻度升高' },
      { key: 'spleen', level: 'normal', value: '脾功能平稳' },
      { key: 'lung', level: 'normal', value: '呼吸 18次/分' },
      { key: 'kidney', level: 'moderate', value: '肌酐 148umol/L' },
      { key: 'leftArm', level: 'mild', value: '左上肢轻度乏力' },
      { key: 'rightArm', level: 'normal', value: '右上肢活动可' },
      { key: 'leftLeg', level: 'moderate', value: '步行需协助' },
      { key: 'rightLeg', level: 'mild', value: '右腿无力感' },
    ],
    'ELD-0003': [
      { key: 'heart', level: 'moderate', value: '心率 104次/分' },
      { key: 'liver', level: 'moderate', value: '转氨酶 88U/L' },
      { key: 'spleen', level: 'mild', value: '脾功能轻度异常' },
      { key: 'lung', level: 'mild', value: '呼吸 22次/分' },
      { key: 'kidney', level: 'severe', value: '肌酐 206umol/L' },
      { key: 'leftArm', level: 'moderate', value: '左臂活动受限' },
      { key: 'rightArm', level: 'moderate', value: '右臂活动受限' },
      { key: 'leftLeg', level: 'severe', value: '左腿重度乏力' },
      { key: 'rightLeg', level: 'moderate', value: '右腿中度乏力' },
    ],
  };
  return mockByElderId[elderId] ?? [];
}

// 接收档案页面传参，并注入到画像页面患者列表中。
function applyArchivePatientFromQuery() {
  if (route.query.source !== 'archive') {
    return;
  }
  const elderName = String(route.query.elderName ?? '').trim();
  if (!elderName) {
    return;
  }
  const elderId = String(route.query.elderId ?? '').trim() || 'unknown';
  const recordId = `archive-${elderId}`;
  const careType = String(route.query.elderCareType ?? '').trim() || '档案导入';
  const markerStates = normalizeMarkerStates(getArchiveMockMarkerStates(elderId));
  const archiveRecord: PatientDemoItem = {
    id: recordId,
    name: elderName,
    careType,
    checkDate: String(route.query.checkDate ?? '').trim() || '2026-04-21',
    reviewer: String(route.query.reviewer ?? '').trim() || '档案护士',
    scaleScore: String(route.query.scaleScore ?? '').trim() || 'Barthel 70分',
    dietAdvice: String(route.query.dietAdvice ?? '').trim() || '枸杞菊花茶（适配肝肾功能偏颇）',
    carePathTip:
      String(route.query.carePathTip ?? '').trim() || '每日监测血糖1次，每周复查肝肾功能',
    markerStates,
  };
  const targetIndex = patientRecords.value.findIndex((item) => item.id === recordId);
  if (targetIndex === -1) {
    patientRecords.value.unshift(archiveRecord);
  } else {
    patientRecords.value[targetIndex] = archiveRecord;
  }
  activePatientId.value = recordId;
  activeMarkerKey.value = '';
  handleSelectEditorMarker(editorMarkerKey.value);
}

// 跳转到报告页面，生成当前健康画像报告。
function handleGenerateReport() {
  router.push('/report-management');
}

watch(
  () => route.fullPath,
  () => {
    applyArchivePatientFromQuery();
  },
  { immediate: true },
);

handleSelectEditorMarker(editorMarkerKey.value);
</script>

<template>
  <div class="portrait-demo-page">
    <section class="demo-header">
      <h2>老人健康画像可视化</h2>
      <div class="demo-header__actions">
        <span class="label">选择老人</span>
        <ElSelect
          v-model="activePatientId"
          class="patient-select"
          placeholder="请选择老人"
          @change="handlePatientChange"
        >
          <ElOption
            v-for="item in patientRecords"
            :key="item.id"
            :label="`${item.name}（${item.careType}）`"
            :value="item.id"
          />
        </ElSelect>
        <ElButton type="primary" @click="handleGenerateReport">生成报告</ElButton>
      </div>
    </section>

    <section class="portrait-stage">
      <div class="portrait-bg-glow"></div>

      <div class="human-stage">
        <div class="human-center">
          <svg class="body-svg" viewBox="0 0 360 760" aria-hidden="true">
            <circle cx="180" cy="72" r="45" />
            <path d="M180 117 L180 282" />
            <path d="M180 165 C135 182 103 220 93 284 C82 355 88 455 126 522" />
            <path d="M180 165 C225 182 257 220 267 284 C278 355 272 455 234 522" />
            <path d="M133 244 L80 314" />
            <path d="M227 244 L280 314" />
            <path
              d="M180 282 C130 312 120 405 160 458 C170 472 188 472 200 458 C240 405 230 312 180 282 Z"
            />
            <path d="M161 458 L149 632" />
            <path d="M199 458 L212 632" />
            <path d="M149 632 L138 728" />
            <path d="M212 632 L224 728" />
            <path d="M122 725 Q144 742 167 730" />
            <path d="M195 730 Q216 742 238 725" />
            <path d="M134 325 C150 300 170 282 180 278 C190 282 210 300 226 325" />
            <path d="M136 385 C156 356 170 344 180 343 C190 344 204 356 224 385" />
            <path d="M147 443 C164 421 173 414 180 414 C187 414 196 421 213 443" />
          </svg>

          <svg
            class="guide-lines"
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path d="M21 24 L31 24 L35 28" />
            <path d="M79 24 L69 24 L65 28" />
            <path d="M21 53 L30 53 L33 48" />
            <path d="M79 53 L70 53 L67 48" />
            <path d="M21 81 L29 81 L32 76" />
            <path d="M79 81 L71 81 L68 76" />
          </svg>

          <ElTooltip
            v-for="marker in torsoMarkers"
            :key="marker.key"
            :content="`${marker.label}：${marker.value}`"
            placement="top"
          >
            <ElPopover
              :visible="markerPopoverVisible && editorMarkerKey === marker.key"
              placement="right"
              trigger="click"
              :width="280"
            >
              <template #reference>
                <button
                  type="button"
                  class="torso-marker"
                  :class="getMarkerClass(marker)"
                  :style="{ left: `${marker.x}%`, top: `${marker.y}%` }"
                  @click="handleFocusMarker(marker.key)"
                >
                  <span>{{ marker.label }}</span>
                  <span v-if="marker.symbol">{{ marker.symbol }}</span>
                </button>
              </template>
              <div class="marker-popover">
                <div class="marker-popover__title">{{ marker.label }}信息填写</div>
                <div class="editor-row">
                  <span class="editor-label">状态</span>
                  <ElSelect v-model="editorMarkerLevel" class="editor-select">
                    <ElOption label="正常" value="normal" />
                    <ElOption label="轻度异常" value="mild" />
                    <ElOption label="中度异常" value="moderate" />
                    <ElOption label="重度异常" value="severe" />
                  </ElSelect>
                </div>
                <div class="editor-row editor-row--value">
                  <span class="editor-label">数据</span>
                  <ElInput v-model="editorMarkerValue" placeholder="请输入该器官指标或说明" />
                </div>
                <div class="marker-popover__actions">
                  <ElButton size="small" @click="markerPopoverVisible = false">取消</ElButton>
                  <ElButton type="primary" size="small" @click="handleApplyMarkerData">
                    保存
                  </ElButton>
                </div>
              </div>
            </ElPopover>
          </ElTooltip>
        </div>
      </div>

      <aside class="info-panel">
        <header class="summary-header">
          <h3>{{ activePatient?.name }}（{{ activePatient?.careType }}）</h3>
          <p>
            当前焦点：
            {{
              activeMarker
                ? `${activeMarker.label} - ${activeMarker.value}`
                : '点击左侧人体查看详情'
            }}
          </p>
        </header>

        <div class="info-panel-grid">
          <div class="info-panel-main">
            <section class="health-detail">
              <div class="detail-title">健康标签</div>
              <div class="tag-list">
                <ElTag
                  v-for="tag in abnormalTags"
                  :key="tag"
                  size="small"
                  type="warning"
                  effect="dark"
                  class="health-tag"
                >
                  {{ tag }}
                </ElTag>
                <ElTag v-if="abnormalTags.length === 0" size="small" type="success" effect="dark">
                  当前无明显异常
                </ElTag>
              </div>
              <div class="detail-desc">
                {{ detailSummaryText }}
              </div>
              <div class="detail-item">
                <strong>药膳推荐：</strong>{{ activePatient?.dietAdvice }}
              </div>
              <div class="detail-item">
                <strong>护理路径：</strong>{{ activePatient?.carePathTip }}
              </div>
              <div class="detail-item detail-item--meta">
                <span>照护类型：{{ activePatient?.careType }}</span>
                <span>检查日期：{{ activePatient?.checkDate }}</span>
                <span>标记人：{{ activePatient?.reviewer }}</span>
                <span>量表：{{ activePatient?.scaleScore }}</span>
              </div>
            </section>

            <section class="marker-editor">
              <div class="editor-title">器官数据填写</div>
              <div class="editor-row">
                <span class="editor-label">器官</span>
                <ElSelect
                  v-model="editorMarkerKey"
                  class="editor-select"
                  @change="handleEditorMarkerChange"
                >
                  <ElOption
                    v-for="item in markerEditorOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </div>
              <div class="editor-row">
                <span class="editor-label">状态</span>
                <ElSelect v-model="editorMarkerLevel" class="editor-select">
                  <ElOption label="正常" value="normal" />
                  <ElOption label="轻度异常" value="mild" />
                  <ElOption label="中度异常" value="moderate" />
                  <ElOption label="重度异常" value="severe" />
                </ElSelect>
              </div>
              <div class="editor-row editor-row--value">
                <span class="editor-label">数据</span>
                <ElInput
                  v-model="editorMarkerValue"
                  placeholder="请输入器官数据，如：心率 80次/分"
                />
              </div>
              <ElButton type="primary" size="small" @click="handleApplyMarkerData">
                保存数据
              </ElButton>
            </section>

            <ul class="stats-list">
              <li>
                <span>正常</span><strong>{{ statusStats.normal }}</strong>
              </li>
              <li>
                <span>轻度异常</span><strong>{{ statusStats.mild }}</strong>
              </li>
              <li>
                <span>中度异常</span><strong>{{ statusStats.moderate }}</strong>
              </li>
              <li>
                <span>重度异常</span><strong>{{ statusStats.severe }}</strong>
              </li>
            </ul>
          </div>

          <div class="marker-list">
            <button
              v-for="marker in renderMarkers"
              :key="marker.key"
              type="button"
              class="marker-list__item"
              :class="{ 'is-active': marker.key === activeMarkerKey }"
              @click="handleFocusMarker(marker.key)"
            >
              <span class="name">{{ marker.label }}</span>
              <span class="status">{{ marker.text }}{{ marker.symbol }}</span>
              <span class="value">{{ marker.value || `${markerLabelMap[marker.key]}待填写` }}</span>
            </button>
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.portrait-demo-page {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  background: radial-gradient(circle at 50% 20%, #4fc2cf 0%, #1f8ca2 45%, #14758d 100%);
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.demo-header {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.demo-header h2 {
  margin: 0;
  color: rgb(241 255 255 / 94%);
  font-size: 22px;
  letter-spacing: 1px;
}

.demo-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.demo-header__actions .label {
  color: rgb(236 255 255 / 85%);
  font-size: 13px;
}

.patient-select {
  width: 240px;
}

.portrait-stage {
  position: relative;
  flex: 1;
  min-height: 540px;
  border: 1px solid rgb(255 255 255 / 26%);
  border-radius: 16px;
  background: linear-gradient(180deg, rgb(65 177 191 / 55%) 0%, rgb(24 128 149 / 72%) 100%);
  overflow: hidden;
  padding: 20px 18px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(520px, 46%);
  gap: 12px;
}

.portrait-bg-glow {
  position: absolute;
  inset: 14% 36% 14% 10%;
  border-radius: 999px;
  background: radial-gradient(circle, rgb(182 240 245 / 20%) 0%, rgb(182 240 245 / 0%) 75%);
  pointer-events: none;
}

.human-stage {
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
}

.human-center {
  position: relative;
  z-index: 1;
  width: min(100%, 600px);
  height: 100%;
  min-height: 0;
}

.body-svg {
  width: 100%;
  height: 100%;
  stroke: rgb(218 251 255 / 88%);
  stroke-width: 5;
  fill: rgb(186 238 246 / 14%);
  filter: drop-shadow(0 0 10px rgb(157 239 250 / 20%));
}

.guide-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.guide-lines path {
  stroke: rgb(221 255 255 / 70%);
  stroke-width: 0.26;
  fill: none;
}

.torso-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  border: 1px solid rgb(255 255 255 / 38%);
  border-radius: 999px;
  min-width: 66px;
  height: 34px;
  padding: 0 10px;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  background: rgb(27 140 108 / 84%);
}

.torso-marker.is-mild {
  background: rgb(212 152 34 / 92%);
}

.torso-marker.is-moderate {
  background: rgb(225 124 48 / 92%);
}

.torso-marker.is-severe {
  background: rgb(208 63 63 / 92%);
}

.torso-marker.is-active {
  box-shadow: 0 0 0 3px rgb(255 255 255 / 28%);
}

.marker-popover {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.marker-popover__title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.marker-popover :deep(.el-input__wrapper),
.marker-popover :deep(.el-select__wrapper) {
  min-height: 30px;
}

.marker-popover__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.info-panel {
  z-index: 3;
  width: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 14px;
  background: rgb(8 72 89 / 50%);
  padding: 12px;
}

.summary-header {
  flex-shrink: 0;
}

.info-panel-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-height: 0;
  flex: 1;
}

.info-panel-main {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.health-detail {
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 10px;
  padding: 10px;
  background: rgb(9 73 92 / 42%);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-title {
  color: rgb(236 252 255 / 95%);
  font-size: 13px;
  font-weight: 600;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.health-tag {
  margin: 0;
}

.detail-desc,
.detail-item {
  color: rgb(224 249 252 / 92%);
  font-size: 12px;
  line-height: 1.5;
}

.detail-item--meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
}

.marker-editor {
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 10px;
  padding: 10px;
  background: rgb(10 80 97 / 40%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.editor-title {
  color: rgb(236 252 255 / 95%);
  font-size: 13px;
  font-weight: 600;
}

.editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-row--value {
  align-items: flex-start;
}

.editor-label {
  width: 38px;
  flex-shrink: 0;
  color: rgb(223 248 251 / 90%);
  font-size: 12px;
}

.editor-select {
  width: 100%;
}

.stats-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  flex-shrink: 0;
}

.marker-list {
  margin-top: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  min-height: 0;
  overflow: auto;
}

.marker-list__item {
  width: 100%;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 9px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 42px 74px 1fr;
  gap: 6px;
  align-items: center;
  color: #dff8fb;
  background: rgb(8 76 96 / 38%);
  text-align: left;
  cursor: pointer;
}

.marker-list__item.is-active {
  border-color: rgb(255 255 255 / 80%);
  background: rgb(15 103 128 / 54%);
}

.marker-list__item .name {
  color: #fff;
  font-weight: 700;
}

.marker-list__item .status,
.marker-list__item .value {
  font-size: 12px;
}

@media (max-width: 1280px) {
  .portrait-stage {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .human-stage {
    min-height: 420px;
  }

  .info-panel {
    max-height: 320px;
  }

  .info-panel-grid {
    grid-template-columns: 1fr;
    flex: initial;
  }

  .marker-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
