<script setup lang="ts">
import type { ActionRecord } from './types';

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
const recordTypeFilter = defineModel<'全部' | '打印' | '执行'>('recordTypeFilter', {
  required: true,
});
</script>

<template>
  <ElDrawer v-model="open" title="执行记录" size="40%">
    <div class="flex h-full min-h-0 flex-col">
      <div class="mb-3 flex items-center gap-2">
        <ElButton
          size="small"
          :type="recordTypeFilter === '全部' ? 'primary' : 'default'"
          @click="recordTypeFilter = '全部'"
        >
          全部
        </ElButton>
        <ElButton
          size="small"
          :type="recordTypeFilter === '执行' ? 'primary' : 'default'"
          @click="recordTypeFilter = '执行'"
        >
          执行
        </ElButton>
        <ElButton
          size="small"
          :type="recordTypeFilter === '打印' ? 'primary' : 'default'"
          @click="recordTypeFilter = '打印'"
        >
          打印
        </ElButton>
      </div>
      <SectionTitle title="记录明细" class="mb-2 shrink-0" />
      <div
        class="bg-muted/60 text-muted-foreground grid grid-cols-[140px_60px_100px_1fr_60px] gap-2 rounded-md px-2 py-2 text-xs"
      >
        <div>时间</div>
        <div>类型</div>
        <div>操作</div>
        <div>目标</div>
        <div>结果</div>
      </div>
      <div class="mt-2 min-h-0 flex-1 space-y-2 overflow-y-auto">
        <div
          v-for="record in records"
          :key="record.id"
          class="bg-muted/45 grid grid-cols-[140px_60px_100px_1fr_60px] items-center gap-2 rounded-md px-3 py-2 text-xs text-gray-700"
        >
          <div>{{ record.time }}</div>
          <div>{{ record.type }}</div>
          <div>{{ record.action }}</div>
          <div class="truncate">{{ record.target }}</div>
          <div>
            <ElTag size="small" type="success">{{ record.result }}</ElTag>
          </div>
        </div>
        <div v-if="records.length === 0" class="py-10 text-center text-sm text-gray-400">
          暂无记录
        </div>
      </div>
    </div>
  </ElDrawer>
</template>
