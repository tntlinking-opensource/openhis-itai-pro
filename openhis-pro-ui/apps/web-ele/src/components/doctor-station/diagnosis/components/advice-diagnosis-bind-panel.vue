<script setup lang="ts">
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import type { DiagnosisItem, DiagnosisMedicineType } from '../services/diagnosis.types';
import type { DoctorStationDiagnosisScope } from '../services/encounter-diagnosis.service';

import type { TableInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { resolveChronicMedicalTypeTag } from '#/utils/chronic-medical-type';

import { mapEncounterRowToDiagnosisItem } from '../services/encounter-diagnosis-select';
import {
  deleteEncounterDiagnosisRow,
  deleteTcmDiagnosisGroupRow,
  fetchEncounterDiagnosisDisplayList,
  switchMainEncounterDiagnosisRow,
} from '../services/encounter-diagnosis.service';

defineOptions({ name: 'AdviceDiagnosisBindPanel' });

interface Props {
  /** 当前就诊已下诊断候选项 */
  options?: DiagnosisItem[];
  /** 本行已绑定的就诊诊断 id（有序） */
  selectedIds?: string[];
  /** 就诊 id（删除接口必填） */
  encounterId?: string;
  /** 患者 id（中医病证组删除必填） */
  patientId?: string;
  /** 门诊/住院诊断删除路由 */
  stationScope?: DoctorStationDiagnosisScope;
  /** 只读：仅展示已选，不可打开选择/删除芯片 */
  disabled?: boolean;
  /** 仅展示指定医学类型（中药方头传 tcm） */
  medicineTypeFilter?: DiagnosisMedicineType | '';
}

/** 打开选择弹窗后重新拉取的就诊诊断（父级同步候选项/快照） */
export interface AdviceDiagnosisOptionsRefreshedPayload {
  displayRows: EncounterDiagnosisRowDto[];
  items: DiagnosisItem[];
}

const props = withDefaults(defineProps<Props>(), {
  options: () => [],
  selectedIds: () => [],
  encounterId: '',
  patientId: '',
  stationScope: 'outpatient',
  disabled: false,
  medicineTypeFilter: '',
});

const emit = defineEmits<{
  'update:selectedIds': [value: string[]];
  /** 打开「追加诊断」录入弹窗 */
  addDiagnosis: [];
  /** 已调用删除接口成功，父级应刷新候选项 */
  deleted: [encounterDiagnosisId: string];
  /** 已切换主诊断成功，父级应刷新候选项 */
  mainSwitched: [encounterDiagnosisId: string];
  /** 打开选择弹窗时已拉取最新就诊诊断 */
  optionsRefreshed: [payload: AdviceDiagnosisOptionsRefreshedPayload];
}>();

interface DiagnosisChip {
  id: string;
  name: string;
  isMain: boolean;
  /** 慢 / 特 / 慢特；非慢特病为空 */
  medicalTypeTag: string;
}

interface PickRow {
  id: string;
  name: string;
  code: string;
  isMain: boolean;
  /** 慢 / 特 / 慢特；非慢特病为空 */
  medicalTypeTag: string;
  medicineType: 'tcm' | 'western';
  tcmSyndromeGroup?: string;
}

const pickOpen = ref(false);
/** 弹窗内勾选快照（确定前不写回行） */
const draftIds = ref<string[]>([]);
const tableRef = ref<TableInstance>();
/** 程序化回显勾选时忽略 selection-change */
const syncingSelection = ref(false);
/** 当前正在删除的就诊诊断 id */
const deletingId = ref('');
/** 当前正在切换主诊的就诊诊断 id */
const switchingMainId = ref('');
/** 打开选择弹窗时拉取最新列表中 */
const optionsLoading = ref(false);
/** 弹窗内最新候选项（打开时拉取；关闭后回落 props） */
const liveOptions = ref<DiagnosisItem[] | null>(null);
/** 并发打开时丢弃过期响应 */
let optionsRefreshSeq = 0;
/**
 * 已点「追加诊断」、等待录入弹窗保存落库。
 * 保存后把本次新增诊断直接带入医嘱，避免回到本弹窗再勾一次。
 */
const appendPending = ref(false);
/** 点击「追加诊断」时的候选项 id 快照（相对此快照识别本次新增） */
const appendBaselineIds = ref<string[]>([]);

/** 按医学类型过滤候选项（中药方仅中医病证） */
function filterByMedicineType(items: DiagnosisItem[]): DiagnosisItem[] {
  const filter = String(props.medicineTypeFilter ?? '').trim();
  if (!filter) {
    return items;
  }
  return items.filter((item) => item.medicineType === filter);
}

/** 弹窗打开用最新拉取结果，关闭后用父级 props */
const effectiveOptions = computed(() => filterByMedicineType(liveOptions.value ?? props.options));

/** 同列表已存在主诊行（用于前端二次提示；后端仍强制唯一） */
const existingMainRow = computed(() => pickRows.value.find((row) => row.isMain));

/** 选项 id → 诊断项 */
const optionById = computed(() => {
  const map = new Map<string, DiagnosisItem>();
  for (const item of effectiveOptions.value) {
    const id = String(item.encounterDiagnosisId ?? item.id ?? '').trim();
    if (!id) continue;
    map.set(id, item);
  }
  return map;
});

/** 本次就诊已开立诊断列表（主诊永远排第一） */
const pickRows = computed((): PickRow[] => {
  const rows: PickRow[] = [];
  for (const item of effectiveOptions.value) {
    const id = String(item.encounterDiagnosisId ?? item.id ?? '').trim();
    const name = String(item.name ?? '').trim();
    if (!id || !name) continue;
    const tcmSyndromeGroup = String(item.tcmSyndromeGroup ?? '').trim() || undefined;
    rows.push({
      id,
      name,
      code: String(item.code ?? '').trim(),
      isMain: Number(item.mainDiagnosisFlag) === 1,
      medicalTypeTag: resolveChronicMedicalTypeTag(item.medicalTypeCode),
      medicineType: item.medicineType === 'tcm' ? 'tcm' : 'western',
      ...(tcmSyndromeGroup ? { tcmSyndromeGroup } : {}),
    });
  }
  const mains: PickRow[] = [];
  const others: PickRow[] = [];
  // 主诊置顶，其余保持原相对顺序
  for (const row of rows) {
    if (row.isMain) {
      mains.push(row);
    } else {
      others.push(row);
    }
  }
  return [...mains, ...others];
});

/** 外侧芯片：按已选顺序展示 */
const selectedChips = computed((): DiagnosisChip[] => {
  const chips: DiagnosisChip[] = [];
  for (const rawId of props.selectedIds ?? []) {
    const id = String(rawId ?? '').trim();
    if (!id) continue;
    const hit = optionById.value.get(id);
    const name = String(hit?.name ?? '').trim();
    if (!name) continue;
    chips.push({
      id,
      name,
      isMain: Number(hit?.mainDiagnosisFlag) === 1,
      medicalTypeTag: resolveChronicMedicalTypeTag(hit?.medicalTypeCode),
    });
  }
  return chips;
});

/** 弹窗「选择结果」芯片（草稿） */
const draftResultChips = computed((): DiagnosisChip[] => {
  const chips: DiagnosisChip[] = [];
  for (const id of draftIds.value) {
    const hit = optionById.value.get(id);
    const name = String(hit?.name ?? '').trim();
    if (!name) continue;
    chips.push({
      id,
      name,
      isMain: Number(hit?.mainDiagnosisFlag) === 1,
      medicalTypeTag: resolveChronicMedicalTypeTag(hit?.medicalTypeCode),
    });
  }
  return chips;
});

/** 主诊优先排序后回写 */
function orderIdsPrimaryFirst(ids: string[]): string[] {
  const unique: string[] = [];
  const seen = new Set<string>();
  // 去重保序
  for (const raw of ids) {
    const id = String(raw ?? '').trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    unique.push(id);
  }
  const mainIdSet = new Set(
    effectiveOptions.value
      .filter((d) => Number(d.mainDiagnosisFlag) === 1)
      .map((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim())
      .filter(Boolean),
  );
  const mains = unique.filter((id) => mainIdSet.has(id));
  const others = unique.filter((id) => !mainIdSet.has(id));
  return [...mains, ...others];
}

/** 按 draftIds 同步表格勾选 */
async function syncTableSelection() {
  await nextTick();
  const table = tableRef.value;
  if (!table) return;
  syncingSelection.value = true;
  table.clearSelection();
  const selected = new Set(draftIds.value);
  // 回显当前草稿勾选
  for (const row of pickRows.value) {
    if (selected.has(row.id)) {
      table.toggleRowSelection(row, true);
    }
  }
  await nextTick();
  syncingSelection.value = false;
}

/** 打开选择弹窗时拉取患者最新已开立诊断 */
async function refreshOptionsOnOpen() {
  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  if (!encounterId || !patientId) {
    return;
  }
  const seq = ++optionsRefreshSeq;
  optionsLoading.value = true;
  try {
    const displayRows = await fetchEncounterDiagnosisDisplayList(
      { encounterId, patientId },
      props.stationScope,
    );
    if (seq !== optionsRefreshSeq || !pickOpen.value) {
      return;
    }
    const items = filterByMedicineType(
      displayRows
        .map((row) => mapEncounterRowToDiagnosisItem(row))
        .filter((item): item is DiagnosisItem => item !== null),
    );
    liveOptions.value = items;
    emit('optionsRefreshed', { displayRows, items });
    // 剔除已不存在的草稿勾选
    const valid = new Set(items.map((item) => String(item.encounterDiagnosisId ?? item.id ?? '')));
    draftIds.value = draftIds.value.filter((id) => valid.has(id));
    void syncTableSelection();
  } catch {
    // 全局拦截器已提示；弹窗仍展示父级缓存候选项
  } finally {
    if (seq === optionsRefreshSeq) {
      optionsLoading.value = false;
    }
  }
}

/** 打开勾选弹窗 */
function openPickDialog() {
  if (props.disabled) {
    return;
  }
  draftIds.value = [...(props.selectedIds ?? [])];
  pickOpen.value = true;
}

/**
 * 弹窗 @open：每次打开都重新拉取已开立诊断（不复用上次缓存）。
 * 放在 @open 而非仅 click，避免 destroy-on-close / 关闭动画竞态漏拉。
 */
function onPickDialogOpen() {
  draftIds.value = [...(props.selectedIds ?? [])];
  // 先清空 live，强制走本次接口结果，避免闪旧数据
  liveOptions.value = null;
  void syncTableSelection();
  void refreshOptionsOnOpen();
}

/** 弹窗完全关闭后清理（勿再写 pickOpen，避免与再次打开竞态） */
function onPickDialogClosed() {
  clearAppendPending();
  liveOptions.value = null;
  optionsLoading.value = false;
  optionsRefreshSeq += 1;
}

/** 表格多选变更：同步草稿勾选 */
function onTableSelectionChange(rows: PickRow[]) {
  if (syncingSelection.value) return;
  draftIds.value = orderIdsPrimaryFirst(rows.map((r) => r.id).filter(Boolean));
}

/** 行点击切换勾选（避开勾选框/主诊标签/删除按钮） */
function onPickRowClick(row: PickRow, _column: unknown, event: Event) {
  const target = event.target as HTMLElement | null;
  if (!target) return;
  // 交互控件自身已有行为，避免重复切换
  if (
    target.closest(
      '.el-checkbox, .el-button, button, a, input, .dx-pick__main-tag-btn, .dx-pick__main-tag',
    )
  ) {
    return;
  }
  const table = tableRef.value;
  if (!table) return;
  const selected = draftIds.value.includes(row.id);
  table.toggleRowSelection(row, !selected);
}

/** 从候选项提取就诊诊断 id */
function collectOptionIds(items: DiagnosisItem[] | undefined): string[] {
  const ids: string[] = [];
  // 保序去重，供追加前后对比
  for (const item of items ?? []) {
    const id = String(item.encounterDiagnosisId ?? item.id ?? '').trim();
    if (!id || ids.includes(id)) continue;
    ids.push(id);
  }
  return ids;
}

/** 清理追加诊断等待态 */
function clearAppendPending() {
  appendPending.value = false;
  appendBaselineIds.value = [];
}

/**
 * 追加诊断保存成功后：合并本次新增 id，直接写回并关闭「选择诊断」。
 * @returns 是否已完成带入
 */
function applyAppendedDiagnoses(candidateIds: string[]): boolean {
  if (!appendPending.value || !pickOpen.value) {
    return false;
  }
  const baseline = new Set(appendBaselineIds.value);
  // 只带入相对追加前快照的新增项，避免打开时刷新误触发
  const incoming = candidateIds
    .map((id) => String(id ?? '').trim())
    .filter((id) => Boolean(id) && !baseline.has(id));
  if (incoming.length === 0) {
    return false;
  }
  // 草稿已有 + 本次新增，主诊优先
  const merged = orderIdsPrimaryFirst([...draftIds.value, ...incoming]);
  draftIds.value = merged;
  clearAppendPending();
  emit('update:selectedIds', merged);
  pickOpen.value = false;
  return true;
}

/** 确定：主诊优先后写回 */
function confirmPick() {
  clearAppendPending();
  emit('update:selectedIds', orderIdsPrimaryFirst(draftIds.value));
  pickOpen.value = false;
}

/** 取消：仅关闭，清理交给 @closed */
function cancelPick() {
  pickOpen.value = false;
}

/** 外侧删除单条绑定 */
function removeChip(id: string) {
  if (props.disabled) {
    return;
  }
  const next = (props.selectedIds ?? []).filter((x) => String(x ?? '').trim() !== id);
  emit('update:selectedIds', orderIdsPrimaryFirst(next));
}

/** 结果区移除草稿勾选 */
function removeDraftChip(id: string) {
  draftIds.value = draftIds.value.filter((x) => x !== id);
  void syncTableSelection();
}

/** 弹窗内追加诊断：打开录入弹窗，保存后自动带入 */
function handleAppendDiagnosis() {
  if (optionsLoading.value) {
    ElMessage.warning('诊断列表加载中，请稍候再追加');
    return;
  }
  appendPending.value = true;
  // 以当前列表为基线，保存后只自动带入新增项
  appendBaselineIds.value = collectOptionIds(effectiveOptions.value);
  emit('addDiagnosis');
}

/** 从草稿/已选中剔除已删除诊断 id */
function scrubDeletedId(deletedId: string) {
  draftIds.value = draftIds.value.filter((id) => id !== deletedId);
  const nextSelected = (props.selectedIds ?? []).filter(
    (id) => String(id ?? '').trim() !== deletedId,
  );
  if (nextSelected.length !== (props.selectedIds ?? []).length) {
    emit('update:selectedIds', orderIdsPrimaryFirst(nextSelected));
  }
}

/** 列表删除已开立诊断（调删除接口） */
async function handleDeleteRow(row: PickRow) {
  const encounterId = String(props.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法删除诊断');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除诊断「${row.name}」？删除后不可恢复。`, '删除诊断', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  deletingId.value = row.id;
  try {
    const groupKey = String(row.tcmSyndromeGroup ?? '').trim();
    const patientId = String(props.patientId ?? '').trim();
    if (groupKey && patientId) {
      await deleteTcmDiagnosisGroupRow(encounterId, patientId, groupKey, props.stationScope);
    } else {
      await deleteEncounterDiagnosisRow(encounterId, row.id, props.stationScope);
    }
    ElMessage.success('已删除');
    scrubDeletedId(row.id);
    emit('deleted', row.id);
    // 删除后重新拉取，避免 liveOptions 挡住父级刷新结果
    void refreshOptionsOnOpen();
  } catch {
    // 全局拦截器已提示
  } finally {
    deletingId.value = '';
  }
}

/** 列表切换主诊断（调切换接口；同就诊仅一条/一组） */
async function handleSwitchMainRow(row: PickRow) {
  if (row.isMain) {
    return;
  }
  const encounterId = String(props.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法切换主诊断');
    return;
  }
  const currentMain = existingMainRow.value;
  const confirmText = currentMain
    ? `确认将「${row.name}」设为主诊断？当前主诊断「${currentMain.name}」将取消主诊（一次就诊仅允许一个主诊断）。`
    : `确认将「${row.name}」设为主诊断？`;
  try {
    await ElMessageBox.confirm(confirmText, '切换主诊断', {
      type: 'warning',
      confirmButtonText: '设为主诊',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  switchingMainId.value = row.id;
  try {
    await switchMainEncounterDiagnosisRow(
      encounterId,
      row.id,
      props.stationScope,
      String(props.patientId ?? '').trim() || undefined,
    );
    ElMessage.success('已设为主诊断');
    emit('mainSwitched', row.id);
    // 切换主诊后重新拉取，保证主诊标记与列表一致
    void refreshOptionsOnOpen();
  } catch {
    // 全局拦截器已提示
  } finally {
    switchingMainId.value = '';
  }
}

/** 主诊标签点击：非主诊时触发切换 */
function onMainTagClick(row: PickRow) {
  if (row.isMain || switchingMainId.value) return;
  void handleSwitchMainRow(row);
}

watch(
  () => props.options,
  (next) => {
    if (!pickOpen.value) return;
    // 追加诊断/父级刷新后同步 live，避免挡住最新候选项
    liveOptions.value = [...next];
    const valid = new Set(collectOptionIds(next));
    draftIds.value = draftIds.value.filter((id) => valid.has(id));
    if (appendPending.value) {
      const baseline = new Set(appendBaselineIds.value);
      // 相对追加前快照新出现的候选项 = 本次录入保存的诊断
      const appearedIds = collectOptionIds(next).filter((id) => !baseline.has(id));
      if (applyAppendedDiagnoses(appearedIds)) {
        return;
      }
    }
    void syncTableSelection();
  },
);

watch(
  () => props.selectedIds,
  (next) => {
    if (!pickOpen.value || !appendPending.value) return;
    const baseline = new Set(appendBaselineIds.value);
    // 父级（如中药方头）已把新开立诊断写入绑定时，同步带入并关闭
    const addedIds = (next ?? [])
      .map((id) => String(id ?? '').trim())
      .filter((id) => Boolean(id) && !baseline.has(id));
    applyAppendedDiagnoses(addedIds);
  },
);
</script>

<template>
  <div class="dx-bind" :class="{ 'dx-bind--disabled': disabled }">
    <div
      class="dx-bind__panel"
      :class="{ 'dx-bind__panel--empty': selectedChips.length === 0 }"
      role="button"
      tabindex="0"
      @click="openPickDialog"
      @keydown.enter.prevent="openPickDialog"
    >
      <template v-if="selectedChips.length > 0">
        <span
          v-for="chip in selectedChips"
          :key="chip.id"
          class="dx-bind__chip"
          :class="{ 'dx-bind__chip--main': chip.isMain }"
          @click.stop
        >
          <span v-if="chip.isMain" class="dx-bind__badge">主</span>
          <span class="dx-bind__name" :title="chip.name">{{ chip.name }}</span>
          <span v-if="chip.medicalTypeTag" class="dx-bind__badge dx-bind__badge--chronic">{{
            chip.medicalTypeTag
          }}</span>
          <button
            v-if="!disabled"
            type="button"
            class="dx-bind__remove"
            aria-label="移除诊断"
            @click="removeChip(chip.id)"
          >
            ×
          </button>
        </span>
      </template>
      <span v-else class="dx-bind__placeholder">点击选择诊断</span>
    </div>
    <ElButton
      v-if="!disabled"
      link
      type="primary"
      class="dx-bind__pick-btn"
      @click="openPickDialog"
    >
      选择诊断
    </ElButton>
  </div>

  <ElDialog
    v-model="pickOpen"
    title="选择诊断"
    width="920px"
    append-to-body
    destroy-on-close
    @open="onPickDialogOpen"
    @closed="onPickDialogClosed"
  >
    <div class="diagnosis-dialog flex flex-col gap-3">
      <div class="toolbar-row">
        <p class="toolbar-tip">
          以下为本次就诊已开立的诊断，请勾选后绑定到本条医嘱；若需新增请点击「追加诊断」。
        </p>
        <ElButton
          type="primary"
          plain
          :loading="optionsLoading"
          :disabled="optionsLoading"
          @click="handleAppendDiagnosis"
        >
          追加诊断
        </ElButton>
      </div>

      <div
        v-if="pickRows.length > 0 || optionsLoading"
        class="table-wrap"
        v-loading="optionsLoading"
      >
        <ElTable
          ref="tableRef"
          :data="pickRows"
          row-key="id"
          border
          height="320"
          highlight-current-row
          class="dx-pick__table"
          @selection-change="onTableSelectionChange"
          @row-click="onPickRowClick"
        >
          <ElTableColumn type="selection" width="48" align="center" />
          <ElTableColumn prop="code" label="诊断编码" width="160" show-overflow-tooltip />
          <ElTableColumn label="主诊" width="72" align="center">
            <template #default="{ row }">
              <button
                type="button"
                class="dx-pick__main-tag-btn"
                :class="{ 'is-main': row.isMain, 'is-loading': switchingMainId === row.id }"
                :disabled="Boolean(switchingMainId) && switchingMainId !== row.id"
                :title="row.isMain ? '当前主诊断' : '点击设为主诊断'"
                @click="onMainTagClick(row)"
              >
                <ElTag
                  size="small"
                  effect="dark"
                  :type="row.isMain ? 'primary' : 'info'"
                  class="dx-pick__main-tag"
                  :class="{ 'is-main': row.isMain }"
                >
                  主
                </ElTag>
              </button>
            </template>
          </ElTableColumn>
          <ElTableColumn label="诊断名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="dx-pick__name-cell">
                <span>{{ row.name }}</span>
                <span v-if="row.medicalTypeTag" class="dx-pick__chronic-tag">{{
                  row.medicalTypeTag
                }}</span>
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="类型" width="88" align="center">
            <template #default="{ row }">
              {{ row.medicineType === 'tcm' ? '中医' : '西医' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="88" align="center" fixed="right">
            <template #default="{ row }">
              <ElButton
                link
                type="danger"
                :loading="deletingId === row.id"
                @click="handleDeleteRow(row)"
              >
                删除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <div v-else class="table-wrap dx-pick__empty-wrap" v-loading="optionsLoading">
        <ElEmpty description="本次就诊暂无已开立诊断，请先点击右上角「追加诊断」" :image-size="72">
          <ElButton
            type="primary"
            :loading="optionsLoading"
            :disabled="optionsLoading"
            @click="handleAppendDiagnosis"
          >
            追加诊断
          </ElButton>
        </ElEmpty>
      </div>

      <div class="form-row form-row--result">
        <span class="label">已选</span>
        <div class="result-tags">
          <template v-if="draftResultChips.length > 0">
            <ElTag
              v-for="chip in draftResultChips"
              :key="chip.id"
              class="result-tag"
              closable
              :type="chip.isMain ? 'primary' : 'info'"
              effect="plain"
              @close="removeDraftChip(chip.id)"
            >
              <span v-if="chip.isMain" class="result-tag__main">主</span>
              {{ chip.name }}
              <span v-if="chip.medicalTypeTag" class="result-tag__chronic">{{
                chip.medicalTypeTag
              }}</span>
            </ElTag>
          </template>
          <span v-else class="result-placeholder"
            >请在上方已开立诊断列表中勾选，绑定到本条医嘱</span
          >
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <ElButton @click="cancelPick">取消</ElButton>
        <ElButton type="primary" @click="confirmPick">确定</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.dx-bind {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.dx-bind--disabled .dx-bind__panel {
  cursor: default;
}

.dx-bind__panel {
  flex: 1;
  min-width: 0;
  min-height: 32px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.dx-bind__pick-btn {
  flex-shrink: 0;
  margin-left: 0;
  padding-left: 4px;
  padding-right: 4px;
}

.dx-bind__panel:hover,
.dx-bind__panel:focus-visible {
  outline: none;
}

.dx-bind__panel--empty {
  min-height: 32px;
  padding: 0 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.dx-bind__placeholder {
  font-size: 14px;
  line-height: 30px;
  color: var(--el-text-color-placeholder);
}

.dx-bind__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 6px 2px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  font-size: 14px;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.dx-bind__chip--main {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.dx-bind__badge {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.dx-bind__badge--chronic {
  background: transparent;
  border: 1.5px solid #0f766e;
  color: #0f766e;
  font-weight: 700;
}

.dx-bind__name {
  max-width: 12em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dx-bind__remove {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 18px;
  line-height: 20px;
  cursor: pointer;
}

.dx-bind__remove:hover {
  background: var(--el-fill-color);
  color: var(--el-color-danger);
}

.diagnosis-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  margin: 0;
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.5;
}

.table-wrap {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.dx-pick__table :deep(.el-table__body tr) {
  cursor: pointer;
}

.dx-pick__empty-wrap {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dx-pick__dash {
  color: var(--el-text-color-placeholder);
}

.dx-pick__main-tag-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  line-height: 1;
}

.dx-pick__main-tag-btn.is-main {
  cursor: default;
}

.dx-pick__main-tag-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.dx-pick__main-tag:not(.is-main) {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
  color: var(--el-text-color-secondary);
}

.dx-pick__main-tag-btn:not(.is-main):not(:disabled):hover .dx-pick__main-tag {
  background: var(--el-color-primary-light-7);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.dx-pick__name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-width: 0;
}

.dx-pick__chronic-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 1.5px solid #0f766e;
  border-radius: 3px;
  background: transparent;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.form-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.form-row--result {
  align-items: center;
  min-height: 32px;
}

.label {
  width: 70px;
  flex-shrink: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 32px;
  text-align: right;
}

.result-tags {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 2px 0;
  border: none;
  background: transparent;
}

.result-placeholder {
  font-size: 14px;
  color: var(--el-text-color-placeholder);
}

.result-tag {
  max-width: 100%;
}

.result-tag__main {
  margin-right: 4px;
  font-weight: 600;
}

.result-tag__chronic {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border: 1.5px solid #0f766e;
  border-radius: 3px;
  background: transparent;
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  vertical-align: middle;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
