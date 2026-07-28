<template>
  <div class="page review-page">
    <div class="page-title-row">
      <h2 class="page-title">审查报告生成</h2>
      <div class="toolbar">
        <el-button :icon="Download" @click="toast('导出任务已创建')">导出</el-button>
        <el-button :icon="Refresh" @click="toast('已重新生成审查报告')">重新生成</el-button>
        <el-button type="primary" :icon="DocumentChecked" @click="toast('草稿已保存')">保存草稿</el-button>
      </div>
    </div>

    <div class="review-layout">
      <SectionCard title="电子卷宗 / 文书预览">
        <template #extra>
          <el-link type="primary" :underline="false">切换材料</el-link>
        </template>
        <div class="dossier-review">
          <aside class="dossier-tree">
            <div class="tree-title">卷宗目录</div>
            <el-tree :data="dossier.tree" node-key="id" default-expand-all highlight-current />
            <div class="mineru-status">
              <span>MinerU解析</span>
              <el-tag type="success" size="small">{{ dossier.info.mineruStatus }}</el-tag>
            </div>
          </aside>
          <article class="document-paper review-paper">
            <h2>中华人民共和国<br />{{ report.document.title }}</h2>
            <div class="doc-code">{{ report.document.caseNo }}</div>
            <p v-for="paragraph in report.document.paragraphs" :key="paragraph" class="doc-paragraph">
              {{ paragraph }}
            </p>
          </article>
        </div>
      </SectionCard>

      <SectionCard>
        <template #extra>
          <span class="case-name">{{ report.caseName }}</span>
        </template>
        <div class="flow-steps">
          <template v-for="(step, index) in report.steps" :key="step">
            <span :class="{ active: index === report.activeStep }">{{ step }}</span>
            <el-icon v-if="index < report.steps.length - 1"><ArrowRight /></el-icon>
          </template>
        </div>
        <el-timeline class="report-timeline">
          <el-timeline-item v-for="section in report.sections" :key="section.title" type="primary">
            <div class="report-section">
              <h3>{{ section.title }}</h3>
              <p>{{ section.content }}</p>
            </div>
          </el-timeline-item>
        </el-timeline>
      </SectionCard>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { ArrowRight, DocumentChecked, Download, Refresh } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import report from '../mock/reviewReport.json'
import dossier from '../mock/dossier.json'

const toast = (message) => {
  ElMessage.success(message)
}
</script>

<style scoped>
.review-layout {
  display: grid;
  grid-template-columns: minmax(560px, 48%) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.dossier-review {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 14px;
}

.dossier-tree {
  min-height: 620px;
  padding: 14px 10px;
  background: #f7f9fd;
  border: 1px solid #edf1f7;
  border-radius: 6px;
}

.tree-title {
  margin: 0 0 12px 4px;
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 700;
}

.dossier-tree :deep(.el-tree) {
  background: transparent;
}

.dossier-tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 4px;
}

.mineru-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 18px;
  padding: 10px;
  color: #64748b;
  font-size: 13px;
  background: #fff;
  border-radius: 6px;
}

.review-paper {
  min-height: 620px;
  padding: 34px 38px;
}

.case-name {
  color: #64748b;
}

.flow-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
  padding: 2px 2px 14px;
  border-bottom: 1px solid #edf1f7;
  font-size: 16px;
  font-weight: 700;
}

.flow-steps span.active {
  color: #e52e3b;
}

.flow-steps .el-icon {
  color: #8b98a8;
}

.report-timeline {
  padding: 16px 12px 4px 30px;
}

.report-timeline :deep(.el-timeline-item__node) {
  width: 16px;
  height: 16px;
  left: -3px;
  background: #5d82ff;
}

.report-timeline :deep(.el-timeline-item__tail) {
  left: 4px;
  border-left-color: #d8e1ef;
}

.report-section {
  margin-bottom: 20px;
  padding: 18px 22px;
  background: #f3f5f8;
  border-radius: 6px;
}

.report-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.report-section p {
  margin: 0;
  color: #5f6f82;
  line-height: 1.8;
}
</style>
