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

/** 按当前顺序重写 sortNo（1 起） */
function reindex(list: ProcedureLineDto[]) {
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
}

/** 删除手术行 */
function handleRemove(index: number) {
  const list = [...(rows.value ?? [])];
  list.splice(index, 1);
  reindex(list);
  rows.value = list;
  markDirty();
}

/**
 * 上移/下移手术行（与诊断出院组同构）。
 *
 * @param index 当前行下标
 * @param direction -1 上移 / 1 下移
 */
function handleMove(index: number, direction: -1 | 1) {
  const list = [...(rows.value ?? [])];
  const target = index + direction;
  if (target < 0 || target >= list.length) {
    return;
  }
  const current = list[index];
  const neighbor = list[target];
  if (!current || !neighbor) {
    return;
  }
  list[index] = neighbor;
  list[target] = current;
  reindex(list);
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
      <ElTableColumn type="index" label="序号" width="52" align="center" />
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
      <ElTableColumn label="一助" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.asit1Name"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="二助" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.asit2Name"
            size="small"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="护士" width="90" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.nurseName"
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
      <ElTableColumn label="主手术" width="80" align="center">
        <template #default="{ row }">
          <ElInput
            v-model="row.mainOprnFlag"
            size="small"
            :disabled="props.readonly"
            placeholder="1/0"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!props.readonly" label="操作" width="156" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton link type="primary" :disabled="$index <= 0" @click="handleMove($index, -1)">
            上移
          </ElButton>
          <ElButton
            link
            type="primary"
            :disabled="$index >= (rows?.length ?? 0) - 1"
            @click="handleMove($index, 1)"
          >
            下移
          </ElButton>
          <ElButton link type="danger" @click="handleRemove($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
