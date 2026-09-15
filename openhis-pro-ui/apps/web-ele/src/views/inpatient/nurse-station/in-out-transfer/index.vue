<script setup lang="ts">
import type { AdmissionBedOperationIntent } from '../services/admission-bed-operation';
import type {
  BedBoardBedCell,
  BedBoardHouseGroup,
  BedBoardOccupantCell,
} from '../services/map-bed-board';
import type { PatientItem } from '../types';

import { computed, onActivated, onMounted, onUnmounted, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { buildAdmissionBedOperationIntent } from '../services/admission-bed-operation';
import {
  collectDischargeOccupantEncounterIds,
  isEncounterInDischargeBedBoard,
} from '../services/discharge-tab-scope';
import { encounterStatusToDischargeLabel } from '../services/map-discharge-pending';
import {
  formatBedBoardNursingLevelLabel,
  formatBedBoardOccupantMeta,
  isBedOperationalInUse,
  isBedVacant,
} from '../services/map-bed-board';
import { readNurseStationBedDragPatientId } from '../services/nurse-station-bed-drag-payload';

import DischargePanel from './discharge-panel.vue';

interface Props {
  /** 当前主操作患者 encounterId */
  activePatientId?: null | string;
  /** 接口返回的床位图分组 */
  bedBoardGroups: BedBoardHouseGroup[];
  bedBoardLoading?: boolean;
  patients: PatientItem[];
  /** 病区 id → 显示名称（分组标题用） */
  regionNameById: Record<string, string>;
  /** 出 Tab 切换时按 panel 重载床位图 */
  onReloadBedBoard?: (panel: 'admission' | 'discharge') => void | Promise<void>;
  /** 出院/清床成功后刷新队列与床位图 */
  onWorkbenchRefresh?: () => void | Promise<void>;
  /** 出 Tab 跳转医嘱执行页（保持当前出院患者选中） */
  onGoToOrderExecute?: () => void | Promise<void>;
  /** 与左侧列表共用的拖拽启动（占床卡、列表卡） */
  onPatientDragStart: (e: DragEvent, row: PatientItem) => void;
}

type BedFilter = 'all' | 'empty' | 'occupied';
type InOutPanel = 'admission' | 'discharge';

defineOptions({
  name: 'InpatientNurseStationInOutTransferModule',
});

const props = withDefaults(defineProps<Props>(), {
  activePatientId: null,
  bedBoardGroups: () => [],
  bedBoardLoading: false,
  patients: () => [],
  regionNameById: () => ({}),
  onReloadBedBoard: undefined,
  onWorkbenchRefresh: undefined,
  onGoToOrderExecute: undefined,
});

const emit = defineEmits<{
  selectPatient: [id: string];
  /** 首次分床：打开入科选床弹窗 */
  openBedOperation: [payload: { patient: PatientItem; intent: AdmissionBedOperationIntent }];
  /** 换床/互换：确认后直接提交 */
  executeDirectBed: [payload: { patient: PatientItem; intent: AdmissionBedOperationIntent }];
}>();

/** 床位筛选：仅影响展示高亮 */
const bedFilter = ref<BedFilter>('all');
const activePanel = ref<InOutPanel>('admission');

/** 占床患者 encounterId → 列表行（待分床提示用） */
const patientByEncounterId = computed(() => {
  const map = new Map<string, PatientItem>();
  for (const p of props.patients) {
    map.set(p.id, p);
  }
  return map;
});

/** 展示范围内未分床患者（REGISTERED 或无床位 id） */
const unassignedInScope = computed(() => {
  const houseIds = new Set(props.bedBoardGroups.map((g) => g.houseId));
  return props.patients.filter(
    (p) =>
      (!p.bedLocationId || p.statusEnum === 'REGISTERED') &&
      (houseIds.size === 0 || houseIds.has(p.wardId) || !p.wardId),
  );
});

/** 出 Tab 床位图占床患者 id（panel=discharge 已由后端过滤） */
const dischargeOccupantEncounterIds = computed(() =>
  collectDischargeOccupantEncounterIds(props.bedBoardGroups),
);

/** 出 Tab 当前患者：仅认床位图占床块，不认左侧在院列表 */
const selectedDischargePatient = computed((): PatientItem | null => {
  const id = String(props.activePatientId ?? '').trim();
  if (!isEncounterInDischargeBedBoard(id, dischargeOccupantEncounterIds.value)) {
    return null;
  }
  for (const house of props.bedBoardGroups) {
    for (const bed of house.beds) {
      if (bed.occupant?.encounterId === id) {
        return patientItemFromOccupant(bed.occupant, house, bed);
      }
    }
  }
  return null;
});

/** 出 Tab 床位图：仅展示占床格 */
const dischargeRegionGroups = computed(() => {
  return visibleRegionGroups.value
    .map((region) => ({
      ...region,
      houses: region.houses
        .map((house) => ({
          ...house,
          beds: house.beds.filter((bed) => !!bed.occupant),
        }))
        .filter((house) => house.beds.length > 0),
    }))
    .filter((region) => region.houses.length > 0);
});

/** 按病区分组后的病房列表（同一病区内多病房） */
const visibleRegionGroups = computed(() => {
  const regionMap = new Map<
    string,
    { regionId: string; regionTitle: string; houses: BedBoardHouseGroup[] }
  >();
  for (const house of props.bedBoardGroups) {
    const regionId = house.regionId || '_unknown';
    let region = regionMap.get(regionId);
    if (!region) {
      region = {
        regionId,
        regionTitle: props.regionNameById[regionId] || '病区',
        houses: [],
      };
      regionMap.set(regionId, region);
    }
    region.houses.push(house);
  }
  return [...regionMap.values()];
});

/** 判断卡片在当前筛选下是否灰显（空床/占床以 operationalEnum 为准） */
function isDimmed(bed: BedBoardBedCell) {
  const occupied = isBedOperationalInUse(bed.operationalEnum);
  if (bedFilter.value === 'all') return false;
  if (bedFilter.value === 'empty') return occupied;
  return !occupied;
}

/** 占床卡片点击后与左侧患者列表单选同步 */
function handleOccupantClick(encounterId: string) {
  emit('selectPatient', encounterId);
}

/** 占床患者在队列中缺失时，用床位图字段拼最小 PatientItem */
function patientItemFromOccupant(
  occupant: BedBoardOccupantCell,
  house: BedBoardHouseGroup,
  bed: BedBoardBedCell,
): PatientItem {
  return {
    id: occupant.encounterId,
    name: occupant.patientName,
    gender: occupant.gender,
    age: occupant.age,
    level: occupant.nursingLevel,
    priorityEnum: occupant.priorityEnum,
    feeType: '—',
    diagnosis: occupant.diagnosis,
    accountBalance: occupant.accountBalance,
    regionId: house.regionId,
    wardId: house.houseId,
    bedNo: bed.bedName,
    bedLocationId: bed.bedId,
    statusEnum: occupant.statusEnum,
    statusEnumText: occupant.statusEnumText,
    pendingAudit: 0,
    pendingExecute: 0,
    taskTag: '',
    riskText: '',
    selected: false,
  };
}

/** 按就诊 id 解析拖拽患者：优先左侧队列，否则扫床位图占床块 */
function resolveDragPatientById(encounterId: string): PatientItem | undefined {
  const fromList = patientByEncounterId.value.get(encounterId);
  if (fromList) {
    return fromList;
  }
  for (const house of props.bedBoardGroups) {
    for (const bed of house.beds) {
      if (bed.occupant?.encounterId === encounterId) {
        return patientItemFromOccupant(bed.occupant, house, bed);
      }
    }
  }
  return undefined;
}

/** 拖拽悬停高亮：床位放置视觉反馈 */
const dropHoverBedKey = ref<string | null>(null);

interface BedDragSourceInfo {
  fromBedLabel: string;
  hasBed: boolean;
  patientName: string;
}

const bedDragSource = ref<BedDragSourceInfo | null>(null);

function bedDropKey(houseId: string, bedId: string) {
  return `${houseId}::${bedId}`;
}

/** 拖拽经过床位时标记可放置区域 */
function onBedDragOver(houseId: string, bedId: string) {
  dropHoverBedKey.value = bedDropKey(houseId, bedId);
}

/** 全局 dragend：清除悬停高亮 */
function onDocumentDragEnd() {
  dropHoverBedKey.value = null;
  bedDragSource.value = null;
}

/** 接收左侧患者卡片 dragstart */
function onBedDragBridgeStart(ev: Event) {
  const d = (ev as CustomEvent<BedDragSourceInfo>).detail;
  if (d?.patientName) {
    bedDragSource.value = {
      fromBedLabel: d.fromBedLabel,
      hasBed: d.hasBed,
      patientName: d.patientName,
    };
  }
}

onMounted(() => {
  document.addEventListener('dragend', onDocumentDragEnd);
  window.addEventListener('nurse-station-bed-drag-start', onBedDragBridgeStart as EventListener);
});

/** KeepAlive 重新激活时重载当前 panel 床位图，避免校对出院医嘱后出 Tab 仍显示旧空态 */
onActivated(() => {
  void props.onReloadBedBoard?.(activePanel.value === 'discharge' ? 'discharge' : 'admission');
});
onUnmounted(() => {
  document.removeEventListener('dragend', onDocumentDragEnd);
  window.removeEventListener('nurse-station-bed-drag-start', onBedDragBridgeStart as EventListener);
});

/** 入科 / 出 Tab 切换时重载对应 panel 的床位图 */
watch(activePanel, (panel) => {
  void props.onReloadBedBoard?.(panel === 'discharge' ? 'discharge' : 'admission');
});

/** 出 Tab 床位图加载后：选中范围内首人，或清空不在范围内的左侧选中 */
watch(
  [activePanel, dischargeOccupantEncounterIds],
  ([panel, scopeIds]) => {
    if (panel !== 'discharge') return;
    const currentId = String(props.activePatientId ?? '').trim();
    if (scopeIds.size === 0) {
      return;
    }
    if (!isEncounterInDischargeBedBoard(currentId, scopeIds)) {
      const firstId = [...scopeIds][0];
      if (firstId) {
        emit('selectPatient', firstId);
      }
    }
  },
  { immediate: true },
);

/** 出院 / 清床成功后刷新工作台数据 */
async function handleDischargeWorkbenchRefresh() {
  await props.onWorkbenchRefresh?.();
}

/** 占床卡片开始拖拽（与左侧列表同一载荷） */
function onOccupiedBedDragStart(e: DragEvent, house: BedBoardHouseGroup, bed: BedBoardBedCell) {
  if (!bed.occupant) {
    return;
  }
  const row = resolveDragPatientById(bed.occupant.encounterId);
  if (!row) {
    return;
  }
  props.onPatientDragStart(e, row);
}

/**
 * 在床位上释放：首次分床打开入科弹窗；换床/互换由父级直接提交。
 */
function onDropOnBed(e: DragEvent, house: BedBoardHouseGroup, bed: BedBoardBedCell) {
  dropHoverBedKey.value = null;
  const patientId = readNurseStationBedDragPatientId(e);
  if (!patientId) {
    return;
  }
  const occupant = bed.occupant;
  const dragged = resolveDragPatientById(patientId);
  if (!dragged) {
    return;
  }
  if (occupant?.encounterId === dragged.id) {
    return;
  }

  const targetOccupied = isBedOperationalInUse(bed.operationalEnum);
  if (targetOccupied && !dragged.bedLocationId) {
    ElMessage.warning('待分床患者不能换至已占床位，请拖向空床完成分床');
    return;
  }

  const regionName = props.regionNameById[house.regionId] || '病区';
  const intent = buildAdmissionBedOperationIntent(
    dragged,
    house.houseId,
    bed.bedId,
    bed.bedName,
    house.houseName,
    regionName,
    house.regionId,
    targetOccupied,
    occupant?.encounterId,
    occupant?.patientName,
  );
  if (!intent) {
    return;
  }
  if (intent.type === 'assign') {
    emit('openBedOperation', { patient: dragged, intent });
    return;
  }
  emit('executeDirectBed', { patient: dragged, intent });
}
</script>

<template>
  <div v-loading="bedBoardLoading" class="in-out-transfer-module">
    <div class="panel-switch-bar">
      <ElSegmented
        v-model="activePanel"
        :options="[
          { label: '入科', value: 'admission' },
          { label: '出院', value: 'discharge' },
        ]"
      />
    </div>

    <template v-if="activePanel === 'admission'">
      <div class="bed-map-toolbar">
        <div class="bed-map-toolbar__filters">
          <span class="bed-map-toolbar__label">床位</span>
          <ElRadioGroup v-model="bedFilter" size="small">
            <ElRadioButton value="all">全部</ElRadioButton>
            <ElRadioButton value="empty">空床</ElRadioButton>
            <ElRadioButton value="occupied">占床</ElRadioButton>
          </ElRadioGroup>
        </div>
        <div class="bed-map-toolbar__hint">
          待分床拖向空床需填写体征；换床、互换松手后确认即生效
        </div>
      </div>

      <ElAlert
        v-if="unassignedInScope.length > 0"
        class="bed-map-alert"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>
          待分床 {{ unassignedInScope.length }} 人：
          <span class="bed-map-unassigned-names">{{
            unassignedInScope.map((p) => p.name).join('、')
          }}</span>
        </template>
      </ElAlert>

      <div class="bed-map-scroll">
        <template v-if="visibleRegionGroups.length > 0">
          <section
            v-for="region in visibleRegionGroups"
            :key="region.regionId"
            class="bed-map-ward"
          >
            <header class="bed-map-ward__title">{{ region.regionTitle }}</header>
            <div v-for="house in region.houses" :key="house.houseId" class="bed-map-room">
              <div class="bed-map-room__head">
                <span class="bed-map-room__name">{{ house.houseName }}</span>
                <span class="bed-map-room__count">{{ house.beds.length }} 床</span>
              </div>
              <div class="bed-map-room__grid">
                <div
                  v-for="bed in house.beds"
                  :key="bed.bedId"
                  class="bed-card-wrap"
                  :class="{
                    'is-dimmed': isDimmed(bed),
                    'is-drop-target': dropHoverBedKey === bedDropKey(house.houseId, bed.bedId),
                  }"
                  @dragover.prevent="onBedDragOver(house.houseId, bed.bedId)"
                  @drop.prevent="onDropOnBed($event, house, bed)"
                >
                  <div
                    v-show="
                      dropHoverBedKey === bedDropKey(house.houseId, bed.bedId) && bedDragSource
                    "
                    class="bed-drop-floater"
                    :class="{
                      'bed-drop-floater--assign': isBedVacant(bed),
                      'bed-drop-floater--swap': isBedOperationalInUse(bed.operationalEnum),
                    }"
                  >
                    <template v-if="bed.occupant">
                      <div class="bed-drop-floater__title">换床预览</div>
                      <div class="bed-drop-floater__line">
                        「{{ bedDragSource?.patientName }}」 ↔ 「{{ bed.occupant.patientName }}」
                      </div>
                      <div class="bed-drop-floater__sub">
                        {{
                          bedDragSource?.hasBed
                            ? '松手后：确认即互换床位'
                            : '松手后：待分床患者不能占已占床位'
                        }}
                      </div>
                    </template>
                    <template v-else>
                      <div class="bed-drop-floater__title">分床至此</div>
                      <div class="bed-drop-floater__line">
                        {{ bedDragSource?.patientName }} → 床位 {{ bed.bedName }}
                      </div>
                      <div class="bed-drop-floater__sub">
                        {{
                          bedDragSource?.hasBed
                            ? '松手后：确认即换至该空床'
                            : '松手后：打开入科选床填写体征后分床'
                        }}
                      </div>
                    </template>
                  </div>
                  <div
                    v-if="bed.occupant"
                    class="bed-card bed-card--occupied bed-card--draggable"
                    draggable="true"
                    :class="{
                      'is-selected': patientByEncounterId.get(bed.occupant.encounterId)?.selected,
                    }"
                    role="button"
                    tabindex="0"
                    @click="handleOccupantClick(bed.occupant.encounterId)"
                    @keydown.enter.prevent="handleOccupantClick(bed.occupant.encounterId)"
                    @dragstart.stop="onOccupiedBedDragStart($event, house, bed)"
                  >
                    <span class="bed-card__stripe bed-card__stripe--in"></span>
                    <div class="bed-card__main">
                      <div class="bed-card__row1">
                        <span class="bed-card__no">{{ bed.bedName }}</span>
                        <span class="bed-card__status">
                          {{ bed.occupant.statusEnumText?.trim() || '在院' }}
                        </span>
                      </div>
                      <div class="bed-card__name">{{ bed.occupant.patientName }}</div>
                      <div class="bed-card__meta">
                        {{ formatBedBoardOccupantMeta(bed.occupant) }}
                      </div>
                      <ElTag
                        v-if="formatBedBoardNursingLevelLabel(bed.occupant.nursingLevel)"
                        size="small"
                        type="info"
                        effect="plain"
                        class="bed-card__level"
                      >
                        {{ formatBedBoardNursingLevelLabel(bed.occupant.nursingLevel) }}
                      </ElTag>
                    </div>
                  </div>
                  <div
                    v-else-if="isBedVacant(bed)"
                    class="bed-card bed-card--empty bed-card--droppable"
                  >
                    <span class="bed-card__stripe bed-card__stripe--empty"></span>
                    <div class="bed-card__main bed-card__main--empty">
                      <span class="bed-card__no bed-card__no--muted">{{ bed.bedName }}</span>
                      <span class="bed-card__empty-label">空床</span>
                    </div>
                  </div>
                  <div v-else class="bed-card bed-card--empty">
                    <span class="bed-card__stripe bed-card__stripe--in"></span>
                    <div class="bed-card__main bed-card__main--empty">
                      <span class="bed-card__no bed-card__no--muted">{{ bed.bedName }}</span>
                      <span class="bed-card__empty-label">占床</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>
        <ElEmpty v-else description="当前筛选下无可展示床位" />
      </div>
    </template>
    <div v-else class="discharge-layout">
      <div class="bed-map-toolbar bed-map-toolbar--discharge">
        <div class="bed-map-toolbar__hint">点击占床患者办理出院；结算完成后可清床离院</div>
      </div>
      <div class="bed-map-scroll bed-map-scroll--discharge">
        <template v-if="dischargeRegionGroups.length > 0">
          <section
            v-for="region in dischargeRegionGroups"
            :key="region.regionId"
            class="bed-map-ward"
          >
            <header class="bed-map-ward__title">{{ region.regionTitle }}</header>
            <div v-for="house in region.houses" :key="house.houseId" class="bed-map-room">
              <div class="bed-map-room__head">
                <span class="bed-map-room__name">{{ house.houseName }}</span>
                <span class="bed-map-room__count">{{ house.beds.length }} 床</span>
              </div>
              <div class="bed-map-room__grid">
                <div v-for="bed in house.beds" :key="bed.bedId" class="bed-card-wrap">
                  <div
                    v-if="bed.occupant"
                    class="bed-card bed-card--occupied bed-card--selectable"
                    :class="{
                      'is-selected': activePatientId === bed.occupant.encounterId,
                    }"
                    role="button"
                    tabindex="0"
                    @click="handleOccupantClick(bed.occupant.encounterId)"
                    @keydown.enter.prevent="handleOccupantClick(bed.occupant.encounterId)"
                  >
                    <span class="bed-card__stripe bed-card__stripe--out"></span>
                    <div class="bed-card__main">
                      <div class="bed-card__row1">
                        <span class="bed-card__no">{{ bed.bedName }}</span>
                      </div>
                      <div class="bed-card__name">{{ bed.occupant.patientName }}</div>
                      <div class="bed-card__meta">
                        {{
                          encounterStatusToDischargeLabel(
                            bed.occupant.statusEnum,
                            bed.occupant.statusEnumText,
                          )
                        }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>
        <ElEmpty
          v-else
          description="当前范围无待出院/待结算占床患者，请先在医生站开立并校对出院医嘱"
        />
      </div>
      <DischargePanel
        v-if="dischargeRegionGroups.length > 0"
        :patient="selectedDischargePatient"
        :on-refresh="handleDischargeWorkbenchRefresh"
        :on-go-to-order-execute="props.onGoToOrderExecute"
      />
    </div>
  </div>
</template>

<style scoped>
.in-out-transfer-module {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 100%;
  background: #eef9fd;
}

.panel-switch-bar {
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  padding: 8px 12px;
}

.bed-map-toolbar {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: space-between;
  padding: 8px 12px;
}

.bed-map-toolbar__filters {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.bed-map-toolbar__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-right: 4px;
}

.bed-map-toolbar__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.bed-map-unassigned-names {
  font-weight: 600;
}

.bed-map-alert {
  flex-shrink: 0;
  margin: 8px 12px 0;
}

.bed-map-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 12px 12px;
}

.bed-map-ward {
  margin-bottom: 16px;
}

.bed-map-ward__title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 2px;
}

.bed-map-room {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 10px;
  padding: 10px 12px 12px;
}

.bed-map-room__head {
  align-items: baseline;
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.bed-map-room__name {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.bed-map-room__count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.bed-map-room__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.bed-card-wrap {
  flex: 0 0 auto;
  position: relative;
  transition: opacity 0.15s ease;
}

.bed-drop-floater {
  align-items: center;
  backdrop-filter: blur(2px);
  background: rgb(255 255 255 / 94%);
  border-radius: 8px;
  box-shadow:
    0 0 0 2px var(--el-color-primary),
    0 8px 20px rgb(15 23 42 / 12%);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 4px;
  inset: 0;
  justify-content: center;
  padding: 8px 6px;
  pointer-events: none;
  position: absolute;
  text-align: center;
  z-index: 10;
}

.bed-drop-floater__title {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.bed-drop-floater__line {
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  word-break: break-all;
}

.bed-drop-floater__sub {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.35;
}

.bed-drop-floater--assign .bed-drop-floater__title {
  color: var(--el-color-success);
}

.bed-drop-floater--swap .bed-drop-floater__title {
  color: var(--el-color-warning);
}

.bed-card-wrap.is-dimmed {
  opacity: 0.38;
}

.bed-card-wrap.is-drop-target .bed-card {
  box-shadow: 0 0 0 2px var(--el-color-primary);
}

.bed-card--droppable {
  cursor: copy;
}

.bed-card {
  border-radius: 8px;
  box-sizing: border-box;
  display: flex;
  min-height: 88px;
  overflow: hidden;
  position: relative;
  width: 132px;
}

.bed-card--occupied {
  background: #fff;
  border: 1px solid var(--el-border-color);
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
  outline: none;
}

.bed-card--draggable {
  cursor: grab;
}

.bed-card--draggable:active {
  cursor: grabbing;
}

.bed-card--selectable {
  cursor: pointer;
}

.bed-card--occupied:hover {
  border-color: var(--el-color-primary-light-5);
}

.bed-card--occupied.is-selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}

.bed-card--empty {
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
}

.bed-card__stripe {
  flex-shrink: 0;
  width: 4px;
}

.bed-card__stripe--in {
  background: #22c55e;
}

.bed-card__stripe--out {
  background: #f59e0b;
}

.bed-card__stripe--empty {
  background: var(--el-border-color);
}

.bed-card__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 8px;
}

.bed-card__main--empty {
  align-items: center;
  justify-content: center;
  padding: 8px;
}

.bed-card__row1 {
  align-items: baseline;
  display: flex;
  gap: 4px;
  justify-content: space-between;
  min-width: 0;
}

.bed-card__no {
  color: var(--el-text-color-primary);
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.bed-card__no--muted {
  color: var(--el-text-color-secondary);
  font-size: 16px;
  font-weight: 600;
}

.bed-card__level {
  align-self: flex-start;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bed-card__name {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bed-card__meta {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bed-card__empty-label {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.bed-card__status {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
}

.discharge-layout {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.bed-map-toolbar--discharge {
  flex-shrink: 0;
}

.bed-map-scroll--discharge {
  flex: 0 0 auto;
  max-height: 36%;
  min-height: 120px;
}
</style>
