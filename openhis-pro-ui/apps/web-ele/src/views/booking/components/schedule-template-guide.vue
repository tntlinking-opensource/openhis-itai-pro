<script setup lang="ts">
import { useRouter } from 'vue-router';

import { ElButton } from 'element-plus';

const router = useRouter();

/** 模板配置完成后，跳转到门诊排班落地 */
function goOutpatientSchedule() {
  void router.push('/booking/outpatient-schedule');
}
</script>

<template>
  <div class="schedule-template-guide">
    <div class="schedule-template-guide__main">
      <p class="schedule-template-guide__title">科室排班惯例（模板）— 保存后尚不能预约</p>
      <p class="schedule-template-guide__lead">
        本页只配置<strong>可复用的惯例</strong>。患者能约号须到
        <strong>门诊排班</strong>：生成 → 发布 → 放号。
      </p>
      <ol class="schedule-template-guide__steps">
        <li><strong>① 放号时段</strong> — 先配好上午/下午各段起止时间与放号数量</li>
        <li><strong>② 日排班</strong> — 选号别、医师号或科室号，并绑定上一步的放号时段</li>
        <li>
          <strong>③ 周排班</strong> — 选出诊科室与排班方式（按科室 /
          按医师），再为周一～周日各选日排班（按医师时一周可排多位）
        </li>
        <li><strong>门诊排班</strong> — 「生成正式排班」选日期 → 发布 → 放号</li>
      </ol>
      <p class="schedule-template-guide__note">
        只排某一天、一位医生？可跳过本页，直接在「门诊排班」点「新建日程」。
      </p>
    </div>
    <ElButton type="primary" plain @click="goOutpatientSchedule">前往门诊排班</ElButton>
  </div>
</template>

<style scoped>
.schedule-template-guide {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.schedule-template-guide__main {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.schedule-template-guide__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.schedule-template-guide__lead {
  margin: 0 0 8px;
}

.schedule-template-guide__steps {
  margin: 0 0 8px;
  padding-left: 20px;
}

.schedule-template-guide__steps li {
  margin-bottom: 4px;
}

.schedule-template-guide__note {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
