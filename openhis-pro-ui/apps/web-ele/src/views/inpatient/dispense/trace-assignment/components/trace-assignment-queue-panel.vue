<script setup lang="ts">
import type { PatientTraceAssignmentQueueRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.types';

import { nextTick, ref, watch } from 'vue';

import { Search } from '@zxplat/icons';

import { ElButton, ElInput, ElRadio, ElRadioGroup, ElTable, ElTableColumn } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 工作台模式：待分配 / 已分配改码 */
export type TraceAssignmentWorkspaceMode = 'assign' | 'revise';

defineOptions({
  name: 'TraceAssignmentQueuePanel',
});

const props = withDefaults(
  defineProps<{
    loading?: boolean;
    patients: PatientTraceAssignmentQueueRowDto[];
    /** 当前勾选的就诊 ID 列表 */
    selectedEncounterIds: string[];
  }>(),
  {
    loading: false,
  },
);

const emit = defineEmits<{
  refresh: [];
  search: [];
  /** 勾选变化（多选） */
  selectionChange: [encounterIds: string[]];
  /** 单击行设为右侧焦点患者 */
  focus: [encounterId: string];
}>();

const keyword = defineModel<string>('keyword', { required: true });
const workspaceMode = defineModel<TraceAssignmentWorkspaceMode>('workspaceMode', {
  required: true,
});

const tableRef = ref<InstanceType<typeof ElTable> | null>(null);
/** 表内同步勾选时跳过回写，避免与外部 selected 互触发 */
const syncingSelection = ref(false);

/** 行主键（就诊 ID 字符串） */
function resolveEncounterId(row: PatientTraceAssignmentQueueRowDto): string {
  return preserveApiIdForRequest(row.encounterId) ?? '';
}

/** 同步外部已选到表格勾选态 */
async function syncTableSelection() {
  const table = tableRef.value;
  if (!table) {
    return;
  }
  syncingSelection.value = true;
  table.clearSelection();
  const selectedSet = new Set(props.selectedEncounterIds);
  for (const row of props.patients) {
    const id = resolveEncounterId(row);
    if (id && selectedSet.has(id)) {
      table.toggleRowSelection(row, true);
    }
  }
  await nextTick();
  syncingSelection.value = false;
}

/** 表格勾选变化 → 上抛就诊 ID 列表 */
function handleSelectionChange(rows: PatientTraceAssignmentQueueRowDto[]) {
  if (syncingSelection.value) {
    return;
  }
  const ids: string[] = [];
  for (const row of rows) {
    const id = resolveEncounterId(row);
    if (id) {
      ids.push(id);
    }
  }
  emit('selectionChange', ids);
}

/** 单击行：设为焦点并保证已勾选 */
function handleRowClick(row: PatientTraceAssignmentQueueRowDto) {
  const id = resolveEncounterId(row);
  if (!id) {
    return;
  }
  emit('focus', id);
  if (!props.selectedEncounterIds.includes(id)) {
    emit('selectionChange', [...props.selectedEncounterIds, id]);
  }
}

watch(
  () => [props.patients, props.selectedEncounterIds] as const,
  () => {
    void syncTableSelection();
  },
  { deep: true },
);
</script>

<template>
  <div class="trace-queue-panel flex h-full min-h-0 flex-col">
    <div class="mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="患者队列" />
      <ElRadioGroup v-model="workspaceMode" class="flex flex-wrap gap-x-3 gap-y-1">
        <ElRadio value="assign">待分配</ElRadio>
        <ElRadio value="revise">已分配改码</ElRadio>
      </ElRadioGroup>
      <div class="flex min-w-0 items-center gap-1.5">
        <ElInput
          v-model="keyword"
          clearable
          placeholder="姓名 / 床号 / 就诊号"
          class="min-w-0 flex-1"
          @keyup.enter="emit('search')"
        >
          <template #prefix>
            <Search class="h-4 w-4" />
          </template>
        </ElInput>
        <ElButton plain class="shrink-0" :loading="loading" @click="emit('search')">
          查询
        </ElButton>
        <ElButton plain class="shrink-0" :loading="loading" @click="emit('refresh')">
          刷新
        </ElButton>
      </div>
      <div class="text-xs text-[var(--el-text-color-secondary)]">
        已选 {{ selectedEncounterIds.length }} 人（可多选）
        <span v-if="workspaceMode === 'revise'"> · 用于分配错误后改码</span>
      </div>
    </div>

    <div class="trace-queue-panel__body min-h-0 flex-1 overflow-auto px-2 pb-2">
      <ElTable
        ref="tableRef"
        v-loading="loading"
        :data="patients"
        class="order-table order-table--horizontal-only"
        :row-key="resolveEncounterId"
        stripe
        height="100%"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
      >
        <ElTableColumn type="selection" width="42" align="center" reserve-selection />
        <ElTableColumn prop="bedName" label="床号" width="72" align="center" />
        <ElTableColumn prop="patientName" label="患者" min-width="88" show-overflow-tooltip />
        <ElTableColumn
          v-if="workspaceMode === 'assign'"
          prop="pendingGroupCount"
          label="待分配"
          width="72"
          align="center"
        />
        <ElTableColumn v-else prop="assignedGroupCount" label="已分配" width="72" align="center" />
      </ElTable>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../styles/order-table.scss';

.trace-queue-panel__body {
  min-height: 0;
}

.trace-queue-panel__body :deep(.el-table) {
  --el-table-header-bg-color: #eef9fd;
}
</style>
