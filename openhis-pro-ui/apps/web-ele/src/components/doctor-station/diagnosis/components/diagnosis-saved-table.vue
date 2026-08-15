<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  DiagnosisOptionDto,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

import { computed } from 'vue';

import { ElButton, ElOption, ElSelect, ElTag } from 'element-plus';
import { Table } from '@vben-core/table-layout-ui';

import type { DoctorStationDiagnosisScope } from '../services/encounter-diagnosis.service';

import { resolveChronicMedicalTypeTag } from '#/utils/chronic-medical-type';

import { formatDiagnosisYbNoDisplay } from '../services/tcm-diagnosis-display';

defineOptions({ name: 'DiagnosisSavedTable' });

const props = defineProps<{
  rows: EncounterDiagnosisRowDto[];
  stationScope?: DoctorStationDiagnosisScope;
  iptDiseaseTypeOptions?: DiagnosisOptionDto[];
  /** 正在切换主诊的就诊诊断行主键 */
  switchingMainId?: string;
}>();

const emit = defineEmits<{
  'add-click': [];
  remove: [row: EncounterDiagnosisRowDto];
  'ipt-type-change': [row: EncounterDiagnosisRowDto, code: string | undefined];
  /** 住院：按展示列表整体顺序上移/下移 */
  move: [row: EncounterDiagnosisRowDto, direction: 'down' | 'up'];
  /** 点击非主诊「主」标签：切换主诊断 */
  'switch-main': [row: EncounterDiagnosisRowDto];
}>();

const isInpatient = computed(() => props.stationScope === 'inpatient');

/** 主诊永远排第一，其余保持相对顺序 */
function sortMainFirst(rows: EncounterDiagnosisRowDto[]): EncounterDiagnosisRowDto[] {
  const mains: EncounterDiagnosisRowDto[] = [];
  const others: EncounterDiagnosisRowDto[] = [];
  for (const row of rows) {
    if (Number(row.mainDiagnosisFlag) === 1) {
      mains.push(row);
    } else {
      others.push(row);
    }
  }
  return [...mains, ...others];
}

/** 行是否当前主诊 */
function isMainRow(row: EncounterDiagnosisRowDto): boolean {
  return Number(row.mainDiagnosisFlag) === 1;
}

/** 行切换主诊中 */
function isSwitchingMainRow(row: EncounterDiagnosisRowDto): boolean {
  const id = String(row.encounterDiagnosisId ?? '').trim();
  return Boolean(id) && id === String(props.switchingMainId ?? '').trim();
}

/** 主诊标签点击：非主诊时通知父级切换 */
function onMainTagClick(row: EncounterDiagnosisRowDto) {
  if (isMainRow(row) || props.switchingMainId) return;
  emit('switch-main', row);
}

/** 在完整展示列表中的下标（医疗类别分组不影响全局排序语义） */
function resolveRowIndex(row: EncounterDiagnosisRowDto): number {
  const groupKey = String(row.tcmSyndromeGroup ?? '').trim();
  if (groupKey) {
    return (props.rows ?? []).findIndex(
      (item) => String(item.tcmSyndromeGroup ?? '').trim() === groupKey,
    );
  }
  const edId = String(row.encounterDiagnosisId ?? '').trim();
  if (edId) {
    return (props.rows ?? []).findIndex(
      (item) => String(item.encounterDiagnosisId ?? '').trim() === edId,
    );
  }
  const defId = String(row.conditionDefinitionId ?? '').trim();
  return (props.rows ?? []).findIndex(
    (item) => String(item.conditionDefinitionId ?? '').trim() === defId,
  );
}

/** 是否可上移（非首行） */
function canMoveUp(row: EncounterDiagnosisRowDto): boolean {
  return resolveRowIndex(row) > 0;
}

/** 是否可下移（非末行） */
function canMoveDown(row: EncounterDiagnosisRowDto): boolean {
  const idx = resolveRowIndex(row);
  return idx >= 0 && idx < (props.rows?.length ?? 0) - 1;
}

/** 医疗类别展示名（与 YbMedType 常用码对齐；未知码回落码值） */
const MEDICAL_TYPE_LABELS: Record<string, string> = {
  '11': '普通门诊',
  '12': '门诊挂号',
  '13': '急诊',
  '14': '门诊慢特病',
  '15': '特药',
  '16': '中医特色门诊',
  '140104': '门诊慢病',
  '140201': '门诊特病',
  '81': '意外伤害门诊',
  '990404': '门诊特检特治',
};

const MEDICAL_TYPE_SORT: Record<string, number> = {
  '11': 1,
  '13': 2,
  '14': 3,
  '140104': 4,
  '140201': 5,
  '16': 6,
  '15': 7,
  '12': 8,
};

type MedicalTypeGroup = {
  key: string;
  label: string;
  rows: EncounterDiagnosisRowDto[];
};

/** 空码与后端保存默认一致，按普通门诊归组 */
function resolveMedicalTypeKey(row: EncounterDiagnosisRowDto): string {
  const code = String(row.medicalTypeCode ?? '').trim();
  return code || '11';
}

function resolveMedicalTypeLabel(code: string): string {
  return MEDICAL_TYPE_LABELS[code] ?? `医疗类别 ${code}`;
}

/** 诊断名旁角标：慢 / 特 / 慢特 */
function medicalTypeTagOf(row: EncounterDiagnosisRowDto): string {
  return resolveChronicMedicalTypeTag(row.medicalTypeCode);
}

/** 按医疗类别分组；仅一种类别时不分组展示；组内主诊置顶 */
const medicalTypeGroups = computed<MedicalTypeGroup[]>(() => {
  const bucket = new Map<string, EncounterDiagnosisRowDto[]>();
  for (const row of props.rows ?? []) {
    const key = resolveMedicalTypeKey(row);
    const list = bucket.get(key);
    if (list) {
      list.push(row);
    } else {
      bucket.set(key, [row]);
    }
  }
  return [...bucket.entries()]
    .map(([key, rows]) => ({
      key,
      label: resolveMedicalTypeLabel(key),
      rows: sortMainFirst(rows),
    }))
    .sort((a, b) => {
      const sa = MEDICAL_TYPE_SORT[a.key] ?? 100;
      const sb = MEDICAL_TYPE_SORT[b.key] ?? 100;
      if (sa !== sb) return sa - sb;
      return a.key.localeCompare(b.key);
    });
});

const shouldGroupByMedicalType = computed(() => medicalTypeGroups.value.length > 1);

/** 单类别时仍用原列表（主诊置顶）；多类别时按组渲染 */
const displayGroups = computed<MedicalTypeGroup[]>(() => {
  if (!shouldGroupByMedicalType.value) {
    return [
      {
        key: 'all',
        label: '',
        rows: sortMainFirst(props.rows ?? []),
      },
    ];
  }
  return medicalTypeGroups.value;
});

const tableColumns = computed<TableColumn[]>(() => {
  const cols: TableColumn[] = [
    { type: 'index', label: '#', width: 52, align: 'center', headerAlign: 'center' },
    { prop: 'conditionCode', label: '诊断编码', width: 160 },
    {
      prop: 'ybNo',
      label: '医保码',
      width: 160,
      slot: 'ybNo',
    },
    { prop: 'conditionDefinitionName', label: '诊断名称', minWidth: 200, slot: 'dxName' },
    {
      prop: 'diagnosisKindText',
      label: '类别',
      width: 100,
      align: 'center',
      headerAlign: 'center',
    },
  ];
  if (isInpatient.value) {
    cols.push({
      prop: 'iptDiseaseTypeCode',
      label: '诊断类型',
      width: 168,
      align: 'center',
      headerAlign: 'center',
      slot: 'iptType',
    });
  }
  cols.push(
    {
      prop: 'mainDiagnosisFlag',
      label: '主诊',
      width: 72,
      align: 'center',
      headerAlign: 'center',
      slot: 'mainFlag',
    },
    {
      label: '操作',
      // 住院含上移/下移，门诊仅删除
      width: isInpatient.value ? 200 : 100,
      align: 'center',
      headerAlign: 'center',
      slot: 'actions',
    },
  );
  return cols;
});

function rowKeyOf(row: EncounterDiagnosisRowDto) {
  return (
    row.tcmSyndromeGroup?.trim() ||
    row.encounterDiagnosisId ||
    row.conditionDefinitionId ||
    row.conditionDefinitionName ||
    ''
  );
}

function onIptTypeSelectChange(
  row: EncounterDiagnosisRowDto,
  val: string | number | boolean | undefined,
) {
  const code = val === undefined || val === null || val === '' ? undefined : String(val);
  emit('ipt-type-change', row, code);
}
</script>

<template>
  <section class="diagnosis-table-wrap">
    <div class="diagnosis-list-header">
      <ElButton type="primary" class="mb-2" @click="emit('add-click')">新增诊断</ElButton>
    </div>

    <div
      class="diagnosis-med-type-groups min-h-0 flex-1"
      :class="{ 'is-flat': !shouldGroupByMedicalType }"
    >
      <div
        v-for="group in displayGroups"
        :key="group.key"
        class="diagnosis-med-type-group"
        :class="{ 'is-flat': !shouldGroupByMedicalType }"
      >
        <div v-if="shouldGroupByMedicalType" class="diagnosis-med-type-group__title">
          {{ group.label }}
          <span class="diagnosis-med-type-group__count">{{ group.rows.length }}</span>
        </div>
        <Table
          class="min-h-0"
          :class="{ 'flex-1': !shouldGroupByMedicalType }"
          :row-key="rowKeyOf"
          :table-data="group.rows"
          :table-columns="tableColumns"
          :show-pagination="false"
          :toolbar-config="{
            // 全部工具关闭时须 enabled:false，否则 Vxe 仍渲染空工具条（线上多出一行空白）
            enabled: false,
            custom: false,
            export: false,
            refresh: false,
            zoom: false,
          }"
          border
        >
          <template #ybNo="{ row }">
            <span>{{ formatDiagnosisYbNoDisplay(row.ybNo) }}</span>
          </template>
          <template #dxName="{ row }">
            <span class="dx-name-cell">
              <span class="dx-name-cell__text" :title="row.conditionDefinitionName">
                {{ row.conditionDefinitionName }}
              </span>
              <span v-if="medicalTypeTagOf(row)" class="dx-chronic-tag">
                {{ medicalTypeTagOf(row) }}
              </span>
            </span>
          </template>
          <template #iptType="{ row }">
            <ElSelect
              :model-value="row.iptDiseaseTypeCode ?? ''"
              clearable
              filterable
              placeholder="选择诊断类型"
              class="w-full"
              @change="(val) => onIptTypeSelectChange(row, val)"
            >
              <ElOption
                v-for="opt in props.iptDiseaseTypeOptions ?? []"
                :key="opt.value ?? opt.label"
                :label="opt.label ?? opt.value"
                :value="opt.value ?? ''"
              />
            </ElSelect>
          </template>
          <template #mainFlag="{ row }">
            <button
              type="button"
              class="dx-main-tag-btn"
              :class="{
                'is-main': isMainRow(row),
                'is-loading': isSwitchingMainRow(row),
              }"
              :disabled="Boolean(props.switchingMainId) && !isSwitchingMainRow(row)"
              :title="isMainRow(row) ? '当前主诊断' : '点击设为主诊断'"
              @click="onMainTagClick(row)"
            >
              <ElTag
                size="small"
                effect="dark"
                :type="isMainRow(row) ? 'primary' : 'info'"
                class="dx-main-tag"
                :class="{ 'is-main': isMainRow(row) }"
              >
                主
              </ElTag>
            </button>
          </template>
          <template #actions="{ row }">
            <template v-if="isInpatient">
              <ElButton
                link
                type="primary"
                :disabled="!canMoveUp(row)"
                @click="emit('move', row, 'up')"
              >
                上移
              </ElButton>
              <ElButton
                link
                type="primary"
                :disabled="!canMoveDown(row)"
                @click="emit('move', row, 'down')"
              >
                下移
              </ElButton>
            </template>
            <ElButton link type="danger" @click="emit('remove', row)">删除</ElButton>
          </template>
        </Table>
      </div>
    </div>
  </section>
</template>

<style scoped>
.diagnosis-list-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.diagnosis-table-wrap {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.diagnosis-med-type-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.diagnosis-med-type-groups.is-flat {
  gap: 0;
  overflow: hidden;
}

.diagnosis-med-type-group.is-flat {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.diagnosis-med-type-group__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.diagnosis-med-type-group__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 500;
}

.dx-main-tag-btn {
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

.dx-main-tag-btn.is-main {
  cursor: default;
}

.dx-main-tag-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.dx-main-tag:not(.is-main) {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
  color: var(--el-text-color-secondary);
}

.dx-main-tag-btn:not(.is-main):not(:disabled):hover .dx-main-tag {
  background: var(--el-color-primary-light-7);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.dx-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-width: 0;
}

.dx-name-cell__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dx-chronic-tag {
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
</style>
