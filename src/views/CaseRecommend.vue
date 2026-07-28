<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">典型案例推送</h2>
      <span class="muted">按案件事实、法条和审理要素检索并推送典型案例</span>
    </div>

    <SectionCard title="检索条件">
      <el-form :model="form" label-width="92px" class="search-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="请输入案件标题" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="罪名">
              <el-input v-model="form.charge" placeholder="如：诈骗罪" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="法条">
              <el-input v-model="form.law" placeholder="如：刑法第二百六十六条" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="案件类型">
              <el-select v-model="form.caseType" placeholder="请选择">
                <el-option v-for="item in options.caseTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="案件等级">
              <el-select v-model="form.caseLevel" placeholder="请选择">
                <el-option v-for="item in options.caseLevels" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="法院等级">
              <el-select v-model="form.courtLevel" placeholder="请选择">
                <el-option v-for="item in options.courtLevels" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="地区">
              <el-input v-model="form.region" placeholder="请输入地区" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="判决时间">
              <el-date-picker
                v-model="form.dateRange"
                type="daterange"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="审理程序">
              <el-select v-model="form.trialProcedure" placeholder="请选择">
                <el-option v-for="item in options.trialProcedures" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="文书类型">
              <el-select v-model="form.documentType" placeholder="请选择">
                <el-option v-for="item in options.documentTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="法院名称">
              <el-input v-model="form.courtName" placeholder="请输入法院名称" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="案件事实">
              <div class="fact-row">
                <el-input
                  v-model="form.caseFact"
                  type="textarea"
                  :rows="5"
                  resize="none"
                  placeholder="请输入或粘贴案件事实，系统将用于推送典型案例"
                />
                <el-button type="primary" plain @click="extractFocus">提取争议焦点</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div v-if="focusVisible" class="focus-box">
        <strong>争议焦点</strong>
        <ol>
          <li v-for="item in options.disputeFocus" :key="item">{{ item }}；</li>
        </ol>
      </div>

      <div class="search-actions">
        <el-button type="primary" :icon="Search" @click="doSearch">检索</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <el-button :icon="Filter">高级筛选</el-button>
      </div>
    </SectionCard>

    <SectionCard title="典型案例推送结果">
      <div class="result-list">
        <CaseCard v-for="item in visibleResults" :key="item.caseNo" :item="item">
          <template #actions>
            <el-button size="small" @click="openDetail(item)">查看详情</el-button>
            <el-button size="small" type="primary" plain @click="message('已引用到审查报告')">
              引用到审查报告
            </el-button>
            <el-button size="small" type="warning" plain @click="message('已加入收藏')">加入收藏</el-button>
          </template>
        </CaseCard>
      </div>
    </SectionCard>

    <el-drawer v-model="drawerVisible" title="案例详情" size="520px">
      <div v-if="currentCase" class="drawer-detail">
        <h3>{{ currentCase.title }}</h3>
        <p class="muted">{{ currentCase.caseNo }} · {{ currentCase.courtName }}</p>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="案件类型">{{ currentCase.caseType }}</el-descriptions-item>
          <el-descriptions-item label="案件等级">{{ currentCase.caseLevel }}</el-descriptions-item>
          <el-descriptions-item label="法院等级">{{ currentCase.courtLevel }}</el-descriptions-item>
          <el-descriptions-item label="判决时间">{{ currentCase.judgmentDate }}</el-descriptions-item>
          <el-descriptions-item label="匹配度">{{ currentCase.matchRate }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <strong>案情摘要</strong>
          <p>{{ currentCase.summary }}</p>
        </div>
        <div class="detail-section">
          <strong>详情说明</strong>
          <p>{{ currentCase.detail }}</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Filter, Refresh, Search } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import CaseCard from '../components/common/CaseCard.vue'
import options from '../mock/caseSearchOptions.json'
import caseResults from '../mock/caseResults.json'

const initialForm = {
  title: '',
  charge: '',
  law: '',
  caseType: '',
  caseLevel: '',
  courtLevel: '',
  region: '',
  dateRange: [],
  trialProcedure: '',
  documentType: '',
  courtName: '',
  caseFact: ''
}

const form = reactive({ ...initialForm })
const visibleResults = ref(caseResults)
const focusVisible = ref(false)
const drawerVisible = ref(false)
const currentCase = ref(null)

const extractFocus = () => {
  focusVisible.value = true
  ElMessage.success('已提取争议焦点')
}

const doSearch = () => {
  visibleResults.value = caseResults
  ElMessage.success('已完成典型案例推送')
}

const reset = () => {
  Object.assign(form, initialForm)
  focusVisible.value = false
  visibleResults.value = []
}

const openDetail = (item) => {
  currentCase.value = item
  drawerVisible.value = true
}

const message = (text) => {
  ElMessage.success(text)
}
</script>

<style scoped>
.search-form :deep(.el-select),
.search-form :deep(.el-date-editor) {
  width: 100%;
}

.fact-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px;
  gap: 12px;
  width: 100%;
}

.fact-row .el-button {
  height: 120px;
  white-space: normal;
}

.focus-box {
  margin: 4px 0 16px 92px;
  padding: 14px 18px;
  line-height: 1.8;
  background: #f4f8ff;
  border: 1px solid #dbe8ff;
  border-radius: 6px;
}

.focus-box strong {
  color: var(--primary);
}

.focus-box ol {
  margin: 8px 0 0;
  padding-left: 20px;
}

.search-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.result-list {
  display: grid;
  gap: 14px;
}

.drawer-detail h3 {
  margin: 0 0 8px;
  font-size: 20px;
}

.detail-section {
  margin-top: 18px;
  padding: 14px;
  line-height: 1.8;
  background: #f7f9fd;
  border-radius: 6px;
}

.detail-section p {
  margin: 8px 0 0;
  color: #5f6f82;
}
</style>
