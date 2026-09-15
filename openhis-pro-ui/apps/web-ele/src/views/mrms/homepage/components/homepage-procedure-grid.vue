<script setup lang="ts">
import type { ProcedureLineDto } from '#/api/mrms/types';

import { ElButton, ElDatePicker, ElInput, ElTable, ElTableColumn } from 'element-plus';

import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { newProcedureRow } from '../homepage-form.util';

defineOptions({ name: 'HomepageProcedureGrid' });

interface Props {
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
});

const rows = defineModel<ProcedureLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

/** 标记脏数据 */
function markDirty() {
  emit('dirty');
}

/** 新增手术行 */
function handleAdd() {
  const nextSort = (rows.value?.length ?? 0) + 1;
  const list = [...(rows.value ?? [])];
  list.push(newProcedureRow(nextSort));
  rows.value = list;
  markDirty();
}

/** 删除手术行 */
function handleRemove(index: number) {
  const list = [...(rows.value ?? [])];
  list.splice(index, 1);
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
  rows.value = list;
  markDirty();
}
</script>

<template>
  <div class="hp-proc-grid">
    <div v-if="!props.readonly" class="mb-2 flex justify-end">
      <ElButton type="primary" size="small" @click="handleAdd">新增手术</ElButton>
    </div>
    <ElTable :data="rows" border size="small">
      <ElTableColumn label="编码" width="140" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.oprnOprtCode"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="手术操作名称" min-width="180">
        <template #default="{ row }">
          <ElInput
            v-model="row.oprnOprtName"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="日期" width="170" align="center">
        <template #default="{ row }">
          <ElDatePicker
            v-model="row.oprnDate"
            type="datetime"
            size="small"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            style="width: 100%"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="级别" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.oprnLevelCode"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="切口" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.oprnIncisionCode"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="愈合" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.healGradeCode"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.anstWayCode"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="术者" width="100" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.operDrName"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉医师" width="100" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.anstDrName"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!props.readonly" label="操作" width="80" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton link type="danger" @click="handleRemove($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
