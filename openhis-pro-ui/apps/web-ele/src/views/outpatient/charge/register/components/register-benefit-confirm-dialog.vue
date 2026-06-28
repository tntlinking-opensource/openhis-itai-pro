<script setup lang="ts">
import type {
  StudentEligibilityPreviewDto,
  StudentRecordPreviewDto,
  YbRegisterBenefitPreviewDto,
} from '#/api/outpatient-registration';

import { ref } from 'vue';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

import RegisterBenefitPreviewPanel from './register-benefit-preview-panel.vue';

defineOptions({
  name: 'RegisterBenefitConfirmDialog',
});

const visible = ref(false);
const benefitKind = ref<'INSURANCE' | 'STUDENT'>('INSURANCE');
const ybPreview = ref<null | YbRegisterBenefitPreviewDto>(null);
const studentRecords = ref<StudentRecordPreviewDto[]>([]);
const studentEligibility = ref<null | StudentEligibilityPreviewDto>(null);

const insuranceContext = ref<{
  cardNo?: string;
  contractId?: string;
  mediumType?: string;
  ybCardInfo?: string;
  ybCardSn?: string;
  ybCertno?: string;
  ybMediumType?: string;
}>({});

function openInsuranceView(payload: {
  cardNo?: string;
  contractId?: string;
  mediumType?: string;
  preview: YbRegisterBenefitPreviewDto;
  ybCardInfo?: string;
  ybCardSn?: string;
  ybCertno?: string;
  ybMediumType?: string;
}) {
  benefitKind.value = 'INSURANCE';
  ybPreview.value = payload.preview;
  studentRecords.value = [];
  studentEligibility.value = null;
  insuranceContext.value = {
    cardNo: payload.cardNo,
    contractId: payload.contractId,
    mediumType: payload.mediumType,
    ybCardInfo: payload.ybCardInfo,
    ybCardSn: payload.ybCardSn,
    ybCertno: payload.ybCertno,
    ybMediumType: payload.ybMediumType,
  };
  visible.value = true;
}

function openStudentView(payload: {
  eligibility?: StudentEligibilityPreviewDto;
  records: StudentRecordPreviewDto[];
}) {
  benefitKind.value = 'STUDENT';
  ybPreview.value = null;
  studentRecords.value = payload.records;
  studentEligibility.value = payload.eligibility ?? null;
  insuranceContext.value = {};
  visible.value = true;
}

defineExpose({
  openInsuranceView,
  openStudentView,
});
</script>

<template>
  <AppDialog
    v-model="visible"
    title="待遇详情"
    width="760px"
    destroy-on-close
    class="register-benefit-detail-dialog"
  >
    <div class="register-benefit-detail-dialog__body">
      <RegisterBenefitPreviewPanel
        v-if="benefitKind === 'INSURANCE' && ybPreview"
        :preview="ybPreview"
        :card-no="insuranceContext.cardNo"
        :medium-type="insuranceContext.mediumType"
        :contract-id="insuranceContext.contractId"
        :yb-card-info="insuranceContext.ybCardInfo"
        :yb-card-sn="insuranceContext.ybCardSn"
        :yb-certno="insuranceContext.ybCertno"
        :yb-medium-type="insuranceContext.ybMediumType"
        :scrollable-tables="true"
      />

      <div v-else-if="benefitKind === 'STUDENT'" class="space-y-3 text-sm">
        <div v-if="studentEligibility" class="flex items-center gap-2">
          <span class="text-gray-500">资格结论</span>
          <ElTag :type="studentEligibility.eligible ? 'success' : 'danger'">
            {{
              studentEligibility.reasonMessage || (studentEligibility.eligible ? '通过' : '不通过')
            }}
          </ElTag>
        </div>
        <ElTable :data="studentRecords" size="small">
          <ElTableColumn prop="studentNo" label="学号" width="110" />
          <ElTableColumn prop="name" label="姓名" width="90" />
          <ElTableColumn prop="college" label="学院" min-width="100" />
          <ElTableColumn prop="grade" label="年级" width="80" />
          <ElTableColumn prop="leaveTimeText" label="离校时间" width="120" />
        </ElTable>
        <div v-if="studentRecords.length === 0" class="py-4 text-center text-gray-400">
          暂无学籍记录
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="visible = false">关闭</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.register-benefit-detail-dialog__body {
  max-height: calc(85vh - 140px);
  overflow-y: auto;
  padding-right: 4px;
}
</style>

<style>
.register-benefit-detail-dialog.el-dialog .el-dialog__body {
  padding-top: 8px;
  padding-bottom: 8px;
}
</style>
