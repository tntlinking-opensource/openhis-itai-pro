<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton } from 'element-plus';

const STORAGE_KEY = 'booking-schedule-guide-dismissed';
const visible = ref(true);
const router = useRouter();

onMounted(() => {
  visible.value = localStorage.getItem(STORAGE_KEY) !== '1';
});

/** 关闭顶部说明条 */
function dismiss() {
  visible.value = false;
  localStorage.setItem(STORAGE_KEY, '1');
}

/** 跳转到排班设置补模板 */
function goScheduleTemplate() {
  void router.push('/booking/schedule-template');
}
</script>

<template>
  <div v-if="visible" class="schedule-page-guide">
    <div class="schedule-page-guide__body">
      <p class="schedule-page-guide__title">怎么排班、怎么现场挂号占号？</p>
      <p><strong>路径 A · 只排某一天</strong>：点「新建日程」→ 保存 → 发布 → 放号。</p>
      <p>
        <strong>路径 B · 按模板生成</strong>：先在「排班设置」配好日/周排班 →
        本页「生成正式排班」：<strong>日排班选单日</strong>，<strong>周排班选区间</strong> → 发布 →
        放号。
      </p>
      <p class="schedule-page-guide__status">
        完成度：<span>生成正式排班</span> → <span>发布（绿格）</span> → <span>放号</span> →
        挂号台选号源现场挂号（余量减 1）
      </p>
    </div>
    <div class="schedule-page-guide__actions">
      <ElButton link type="primary" @click="goScheduleTemplate">排班设置</ElButton>
      <ElButton link type="primary" @click="dismiss">知道了</ElButton>
    </div>
  </div>
</template>

<style scoped>
.schedule-page-guide {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 14px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}

.schedule-page-guide__body {
  flex: 1;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.schedule-page-guide__title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.schedule-page-guide__body p {
  margin: 0 0 4px;
}

.schedule-page-guide__status {
  margin-top: 6px !important;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schedule-page-guide__status span {
  color: var(--el-text-color-primary);
}

.schedule-page-guide__actions {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
}
</style>
