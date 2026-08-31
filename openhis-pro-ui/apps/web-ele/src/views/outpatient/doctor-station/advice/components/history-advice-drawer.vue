<script setup lang="ts">
import type {
  HistoryAdviceEncounterItem,
  HistoryAdviceRowItem,
} from '../services/history-advice.types';

import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { computed, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { mapEncounterRowToDiagnosisItem } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { fetchEncounterDiagnosisDisplayList } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import AdviceDiagnosisSummaryCell from './advice-diagnosis-summary-cell.vue';
import { resolvePrimaryDiagnosisChronicTag } from '../services/advice-diagnosis-display';
import { getSkinTestDisplayTagType } from '../services/advice-list';
import {
  fetchPatientHistoryDoctorStationAdvice,
  sortHistoryEncounterList,
} from '../services/history-advice.service';

defineOptions({ name: 'HistoryAdviceDrawer' });

const props = withDefaults(defineProps<Props>(), {
  patientId: '',
  encounterId: '',
});

const emit = defineEmits<{
  reuse: [rows: HistoryAdviceRowItem[]];
}>();

interface Props {
  patientId?: string;
  encounterId?: string;
}

const open = defineModel<boolean>('open', { default: false });

type AdviceTypeFilter = 'ALL' | 'DEVICE' | 'MEDICATION' | 'SERVICE';

const searchKeyword = ref('');
const adviceTypeFilter = ref<AdviceTypeFilter>('ALL');
const selectedEncounterId = ref('');
const tableSelection = ref<HistoryAdviceRowItem[]>([]);
const loading = ref(false);
const historyBundles = ref<
  Array<{ encounter: HistoryAdviceEncounterItem; rows: HistoryAdviceRowItem[] }>
>([]);
/** encounterId → 诊断候选项（慢病角标） */
const diagnosisItemsByEncounterId = ref(new Map<string, DiagnosisItem[]>());

/** 左栏就诊列表：按时间倒序，当前就诊置顶 */
const encounterList = computed(() => {
  const list = historyBundles.value.map((b) => b.encounter);
  return sortHistoryEncounterList(list);
});

/** 当前选中就诊下的全部医嘱行 */
const activeEncounterRows = computed(() => {
  const id = selectedEncounterId.value.trim();
  if (!id) return [];
  return historyBundles.value.find((b) => b.encounter.encounterId === id)?.rows ?? [];
});

/** 当前选中就诊诊断候选项 */
const activeDiagnosisItems = computed(() => {
  const id = selectedEncounterId.value.trim();
  if (!id) return [];
  return diagnosisItemsByEncounterId.value.get(id) ?? [];
});

/** 关键字 + 类型过滤后的表格行 */
const filteredTableRows = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  return activeEncounterRows.value.filter((row) => {
    if (adviceTypeFilter.value !== 'ALL') {
      const rt = String(row.resourceType ?? '').toUpperCase();
      if (rt !== adviceTypeFilter.value) return false;
    }
    if (!kw) return true;
    const blob = [
      row.content,
      row.type,
      row.diagnosisSummary,
      row.orderingDoctor,
      row.extension,
      row.dosageText,
    ]
      .join(' ')
      .toLowerCase();
    return blob.includes(kw);
  });
});

/** 当前选中就诊摘要 */
const activeEncounter = computed<HistoryAdviceEncounterItem | null>(() => {
  const id = selectedEncounterId.value.trim();
  if (!id) return null;
  return encounterList.value.find((e) => e.encounterId === id) ?? null;
});

/** 可复用选中条数 */
const reusableSelectedCount = computed(() => {
  return tableSelection.value.filter((r) => r.reusable !== false).length;
});

/** 左栏就诊卡主诊慢病角标 */
function encounterPrimaryChronicTag(encounterId: string): string {
  const items = diagnosisItemsByEncounterId.value.get(encounterId) ?? [];
  return resolvePrimaryDiagnosisChronicTag(items);
}

/** 并行拉取各历史就诊诊断（失败单就诊降级为空，不阻断列表） */
async function loadHistoryDiagnosisMaps(patientId: string, encounterIds: string[]) {
  const uniqueIds = [...new Set(encounterIds.map((id) => id.trim()).filter(Boolean))];
  if (uniqueIds.length === 0) {
    diagnosisItemsByEncounterId.value = new Map();
    return;
  }
  const next = new Map<string, DiagnosisItem[]>();
  await Promise.all(
    uniqueIds.map(async (historyEncounterId) => {
      try {
        const displayList = await fetchEncounterDiagnosisDisplayList(
          { encounterId: historyEncounterId, patientId },
          'outpatient',
        );
        next.set(
          historyEncounterId,
          displayList
            .map((row) => mapEncounterRowToDiagnosisItem(row))
            .filter((item): item is DiagnosisItem => item !== null),
        );
      } catch {
        next.set(historyEncounterId, []);
      }
    }),
  );
  diagnosisItemsByEncounterId.value = next;
}

/** 拉取患者历史医嘱（无数据或接口不可用时保持空列表） */
async function loadHistoryAdvice() {
  const patientId = preserveApiIdForRequest(props.patientId ?? '');
  const encounterId = preserveApiIdForRequest(props.encounterId ?? '');
  if (!patientId) {
    historyBundles.value = [];
    diagnosisItemsByEncounterId.value = new Map();
    return;
  }
  if (!encounterId) {
    historyBundles.value = [];
    diagnosisItemsByEncounterId.value = new Map();
    ElMessage.warning('缺少当前就诊信息，无法查询历史医嘱');
    return;
  }
  loading.value = true;
  try {
    historyBundles.value = await fetchPatientHistoryDoctorStationAdvice(patientId, encounterId);
    // 医嘱行返回后补诊断，供慢病角标（与复用落库同源）
    const historyEncounterIds = historyBundles.value.map((b) => b.encounter.encounterId);
    await loadHistoryDiagnosisMaps(patientId, historyEncounterIds);
  } finally {
    loading.value = false;
  }
}

/** 打开时重置筛选并拉数 */
function initSelectionOnOpen() {
  tableSelection.value = [];
  searchKeyword.value = '';
  adviceTypeFilter.value = 'ALL';
  diagnosisItemsByEncounterId.value = new Map();
  void loadHistoryAdvice().then(() => {
    const first = encounterList.value[0];
    selectedEncounterId.value = first?.encounterId ?? '';
  });
}

/** 切换左侧就诊卡片 */
function handleEncounterClick(encounterId: string) {
  selectedEncounterId.value = encounterId;
  tableSelection.value = [];
}

/** 表格勾选变化 */
function handleSelectionChange(rows: HistoryAdviceRowItem[]) {
  tableSelection.value = rows;
}

/** 执行复用（由父级拉目录并静默保存） */
function applyReuse(rows: HistoryAdviceRowItem[]) {
  if (rows.length === 0) return;
  emit('reuse', rows);
}

/** 单行复用入口 */
function handleReuseSingle(row: HistoryAdviceRowItem) {
  if (row.reusable === false) {
    ElMessage.warning(row.reuseWarning || '该医嘱不可复用');
    return;
  }
  applyReuse([row]);
}

/** 复用选中或整次就诊 */
function handleReuseSelected(mode: 'encounter' | 'selection') {
  const rows =
    mode === 'encounter'
      ? activeEncounterRows.value.filter((r) => r.reusable !== false)
      : tableSelection.value.filter((r) => r.reusable !== false);
  if (rows.length === 0) {
    ElMessage.warning(mode === 'encounter' ? '本次就诊没有可复用的医嘱' : '请先勾选可复用的医嘱');
    return;
  }
  applyReuse(rows);
}

watch(open, (visible) => {
  if (visible) initSelectionOnOpen();
});
</script>

<template>
  <ElDrawer
    v-model="open"
    title="历史医嘱"
    size="72%"
    append-to-body
    destroy-on-close
    class="history-advice-drawer"
  >
    <div v-loading="loading" class="history-advice-root">
      <div class="history-advice-panels">
        <section class="encounter-panel">
          <div class="encounter-panel__header">
            <span class="encounter-panel__title">就诊记录</span>
            <span class="encounter-panel__count">{{ encounterList.length }} 次</span>
          </div>
          <div class="encounter-panel__list">
            <p
              v-if="!(patientId ?? '').trim()"
              class="encounter-panel__empty"
              style="color: var(--el-text-color-secondary)"
            >
              请先选择患者
            </p>
            <template v-else-if="encounterList.length > 0">
              <button
                v-for="item in encounterList"
                :key="item.encounterId"
                type="button"
                class="encounter-card"
                :class="{
                  'encounter-card--active': item.encounterId === selectedEncounterId,
                  'encounter-card--current': item.isCurrentEncounter,
                }"
                @click="handleEncounterClick(item.encounterId)"
              >
                <div class="encounter-card__head">
                  <span class="encounter-card__time">{{ item.visitTime }}</span>
                  <ElTag v-if="item.isCurrentEncounter" size="small" type="primary" effect="light">
                    本次
                  </ElTag>
                </div>
                <div class="encounter-card__dept">
                  {{ item.departmentName }} · {{ item.visitTypeText }}
                </div>
                <div class="encounter-card__dx">
                  <span>{{ item.primaryDiagnosis }}</span>
                  <span
                    v-for="tag in [encounterPrimaryChronicTag(item.encounterId)]"
                    :key="`${item.encounterId}-chronic`"
                    v-show="tag"
                    class="encounter-card__chronic"
                  >
                    {{ tag }}
                  </span>
                </div>
                <div class="encounter-card__meta">
                  <span>{{ item.orderingDoctor }}</span>
                  <span>{{ item.adviceCountSummary }}</span>
                </div>
              </button>
            </template>
            <ElEmpty
              v-else
              description="暂无历史就诊"
              :image-size="64"
              class="encounter-panel__empty"
            />
          </div>
        </section>

        <section class="advice-panel">
          <div class="advice-panel__header">
            <div class="advice-panel__filters">
              <ElSelect v-model="adviceTypeFilter" class="advice-panel__type-select">
                <ElOption label="全部类型" value="ALL" />
                <ElOption label="药品" value="MEDICATION" />
                <ElOption label="诊疗" value="SERVICE" />
                <ElOption label="耗材" value="DEVICE" />
              </ElSelect>
              <ElInput
                v-model="searchKeyword"
                clearable
                placeholder="搜索医嘱名称、诊断..."
                :prefix-icon="Search"
                class="advice-panel__search"
              />
            </div>
            <div class="advice-panel__actions">
              <ElButton
                type="primary"
                :disabled="tableSelection.length === 0"
                @click="handleReuseSelected('selection')"
              >
                复用选中{{ tableSelection.length > 0 ? `(${reusableSelectedCount})` : '' }}
              </ElButton>
              <ElButton plain @click="handleReuseSelected('encounter')">复用整次就诊</ElButton>
            </div>
          </div>

          <div v-if="activeEncounter" class="advice-panel__banner">
            <span class="advice-panel__banner-label">当前查看</span>
            <span class="advice-panel__banner-text">
              {{ activeEncounter.visitTime }} · {{ activeEncounter.departmentName }} ·
              {{ activeEncounter.primaryDiagnosis }}
            </span>
          </div>

          <div class="advice-panel__table-wrap">
            <ElTable
              v-if="filteredTableRows.length > 0"
              :data="filteredTableRows"
              border
              stripe
              class="history-advice-table"
              height="100%"
              @selection-change="handleSelectionChange"
            >
              <ElTableColumn type="selection" width="42" align="center" />
              <ElTableColumn prop="type" label="类型" width="88" align="center" />
              <ElTableColumn prop="content" label="医嘱内容" min-width="320">
                <template #default="{ row }">
                  <AdviceOrderDisplayCell
                    :row-key="row.key"
                    :group-items="row.groupItems"
                    :content="row.content"
                    :dosage-text="row.dosageText"
                    :extension="row.extension"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="startTime" label="开立时间" width="160" align="center" />
              <ElTableColumn prop="diagnosisSummary" label="诊断" min-width="140" align="center">
                <template #default="{ row }">
                  <AdviceDiagnosisSummaryCell
                    :diagnosis-summary="row.diagnosisSummary"
                    :encounter-diagnosis-ids="row.encounterDiagnosisIds"
                    :diagnosis-items="activeDiagnosisItems"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="orderingDoctor" label="医生" width="80" align="center" />
              <ElTableColumn prop="skinTestText" label="皮试" width="72" align="center">
                <template #default="{ row }">
                  <ElTag
                    v-if="getSkinTestDisplayTagType(row)"
                    :type="getSkinTestDisplayTagType(row)!"
                    size="small"
                    effect="light"
                  >
                    {{ row.skinTestText }}
                  </ElTag>
                  <span v-else>{{ row.skinTestText }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="72" align="center" fixed="right">
                <template #default="{ row }">
                  <ElButton
                    link
                    type="primary"
                    :disabled="row.reusable === false"
                    @click="handleReuseSingle(row)"
                  >
                    复用
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
            <ElEmpty
              v-else
              description="暂无医嘱数据"
              :image-size="72"
              class="advice-panel__empty"
            />
          </div>

          <footer class="advice-panel__footer">
            已选 {{ tableSelection.length }} 条 · 当前列表 {{ filteredTableRows.length }} 条
          </footer>
        </section>
      </div>
    </div>
  </ElDrawer>
</template>

<style scoped lang="scss">
.history-advice-drawer :deep(.el-drawer__body) {
  padding: 12px;
  overflow: hidden;
}

.history-advice-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.history-advice-panels {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
  height: 100%;
}

.encounter-panel,
.advice-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.encounter-panel__header,
.advice-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 10px 12px;
  background: #eaf4ff;
  border-bottom: 1px solid #dbeafe;
}

.encounter-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.encounter-panel__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.encounter-panel__list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.encounter-panel__empty {
  padding: 2rem 0.5rem;
  text-align: center;
  font-size: 13px;
}

.encounter-card {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition: all 0.15s ease;
}

.encounter-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: #f8fbff;
  box-shadow: 0 2px 8px rgb(64 158 255 / 8%);
}

.encounter-card--active {
  border-color: var(--el-color-primary);
  background: #f0f9ff;
}

.encounter-card--current {
  border-left: 3px solid var(--el-color-primary);
}

.encounter-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.encounter-card__time {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.encounter-card__dept {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-primary);
}

.encounter-card__dx {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-regular);
}

.encounter-card__chronic {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 1.5px solid #0f766e;
  border-radius: 3px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.encounter-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.advice-panel__header {
  flex-wrap: wrap;
  gap: 8px;
}

.advice-panel__filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.advice-panel__type-select {
  width: 112px;
  flex-shrink: 0;
}

.advice-panel__search {
  width: 220px;
  max-width: 100%;
}

.advice-panel__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.advice-panel__banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.advice-panel__banner-label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.advice-panel__banner-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.advice-panel__table-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.history-advice-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff !important;
}

.history-advice-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
  overflow-y: auto;
}

.advice-panel__empty {
  padding: 48px 0;
}

.advice-panel__footer {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 12px;
  text-align: right;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
</style>
