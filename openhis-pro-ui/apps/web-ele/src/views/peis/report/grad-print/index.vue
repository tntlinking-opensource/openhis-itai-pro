<script setup lang="ts">
import { ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { EXAM_KIND_LABEL } from '#/api/peis/exam-history';
import {
  checkArchiveCompletenessApi,
  type ArchiveCompletenessDto,
} from '#/api/peis/health-archive';
import {
  listGradReportsByStudentNoApi,
  printGradCombinedApi,
  type GradReportRowDto,
} from '#/api/peis/report-print';
import { printPeisReport } from '#/utils/print/print-peis-report';

const studentNo = ref('');
const rows = ref<GradReportRowDto[]>([]);
const selected = ref<GradReportRowDto[]>([]);
const printing = ref(false);
const completeness = ref<ArchiveCompletenessDto | null>(null);

function kindLabel(kind?: string) {
  if (!kind) return '—';
  return EXAM_KIND_LABEL[kind] || kind;
}

async function search() {
  completeness.value = null;
  const res = await listGradReportsByStudentNoApi({ studentNo: studentNo.value });
  rows.value = res.rows || [];
  try {
    const check = await checkArchiveCompletenessApi({
      patientId: res.patientId,
      studentNo: res.studentNo || studentNo.value,
    });
    completeness.value = check.completeness || null;
  } catch {
    // ignore
  }
}

async function printSelected() {
  const ids = selected.value.map((r) => r.encounterId!).filter(Boolean);
  if (!ids.length) {
    ElMessage.warning('请勾选历年体检');
    return;
  }
  if (completeness.value?.level === 'block') {
    try {
      await ElMessageBox.confirm(
        '学生体检档案存在阻断项（如缺定稿报告/既往史），仍要继续合打吗？',
        '档案不完整',
        { type: 'warning' },
      );
    } catch {
      return;
    }
  }
  printing.value = true;
  try {
    await printGradCombinedApi({ encounterIds: ids });
    let successCount = 0;
    for (const encounterId of ids) {
      const result = await printPeisReport({ encounterId, notifyOnBizConfirm: false });
      if (result.ok) {
        successCount += 1;
      }
    }
    if (successCount > 0) {
      ElMessage.success(`已成功打印 ${successCount} 份体检报告`);
    }
  } finally {
    printing.value = false;
  }
}

function onSelectionChange(val: GradReportRowDto[]) {
  selected.value = val;
}
</script>

<template>
  <Page auto-content-height content-class="p-4">
    <ElForm inline class="mb-4">
      <ElFormItem label="学号">
        <ElInput v-model="studentNo" placeholder="毕业合打：输入学号" clearable />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" @click="search">查询历年</ElButton>
        <ElButton :loading="printing" @click="printSelected">合打选中</ElButton>
      </ElFormItem>
    </ElForm>

    <ElAlert
      v-if="completeness"
      class="mb-4"
      :type="
        completeness.level === 'block'
          ? 'error'
          : completeness.level === 'warn'
            ? 'warning'
            : 'success'
      "
      :title="completeness.summary || '档案提示'"
      show-icon
      :closable="false"
    >
      <ul v-if="completeness.missingItems?.length" class="list-disc pl-5">
        <li v-for="item in completeness.missingItems" :key="item.code">
          {{ item.message }}
        </li>
      </ul>
    </ElAlert>

    <ElTable :data="rows" border @selection-change="onSelectionChange">
      <ElTableColumn type="selection" width="48" />
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="examKind" label="类型" width="110">
        <template #default="{ row }">
          {{ kindLabel(row.examKind) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="calendarYear" label="年份" width="80" />
      <ElTableColumn prop="statusEnum" label="状态" width="100" />
      <ElTableColumn prop="createdAt" label="体检时间" min-width="160" />
      <ElTableColumn prop="finalReport" label="可合打" width="80">
        <template #default="{ row }">
          <ElTag :type="row.finalReport ? 'success' : 'info'" size="small">
            {{ row.finalReport ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
    </ElTable>
  </Page>
</template>
