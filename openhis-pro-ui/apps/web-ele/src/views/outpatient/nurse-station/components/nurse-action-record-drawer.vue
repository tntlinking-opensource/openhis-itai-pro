<script setup lang="ts">
import type { ActionRecord, NurseActionRecordTypeFilter } from './types';
import { NURSE_ACTION_RECORD_TYPE_LABEL } from './types';

import { ElButton, ElDrawer, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'NurseActionRecordDrawer',
});

defineProps<Props>();

interface Props {
  records: ActionRecord[];
}

const open = defineModel<boolean>('open', { required: true });
const recordTypeFilter = defineModel<NurseActionRecordTypeFilter>('recordTypeFilter', {
  required: true,
});
</script>

<template>
  <ElDrawer v-model="open" title="执行记录" size="40%">
    <div class="flex h-full min-h-0 flex-col">
      <div class="mb-3 flex items-center gap-2">
        <ElButton
          size="small"
          :type="recordTypeFilter === '' ? 'primary' : 'default'"
          @click="recordTypeFilter = ''"
        >
          全部
        </ElButton>
        <ElButton
          size="small"
          :type="recordTypeFilter === 'EXECUTE' ? 'primary' : 'default'"
          @click="recordTypeFilter = 'EXECUTE'"
        >
          执行
        </ElButton>
        <ElButton
          size="small"
          :type="recordTypeFilter === 'PRINT' ? 'primary' : 'default'"
          @click="recordTypeFilter = 'PRINT'"
        >
          打印
        </ElButton>
      </div>
      <SectionTitle title="记录明细" class="mb-2 shrink-0" />
      <div
        class="bg-muted/60 text-muted-foreground grid grid-cols-[130px_52px_72px_minmax(0,1fr)_72px_52px] gap-2 rounded-md px-2 py-2 text-xs"
      >
        <div>时间</div>
        <div>类型</div>
        <div>操作</div>
        <div>目标</div>
        <div>操作人</div>
        <div>结果</div>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto">
        <div
          v-for="record in records"
          :key="record.id"
          class="grid grid-cols-[130px_52px_72px_minmax(0,1fr)_72px_52px] gap-2 border-b border-[var(--el-border-color-lighter)] px-2 py-2 text-xs"
        >
          <div class="truncate">{{ record.time }}</div>
          <div>
            <ElTag size="small" :type="record.type === 'EXECUTE' ? 'warning' : 'info'">
              {{ NURSE_ACTION_RECORD_TYPE_LABEL[record.type] }}
            </ElTag>
          </div>
          <div class="truncate">{{ record.action }}</div>
          <div class="truncate">{{ record.target }}</div>
          <div class="truncate">{{ record.operator }}</div>
          <div>{{ record.result }}</div>
        </div>
      </div>
    </div>
  </ElDrawer>
</template>
