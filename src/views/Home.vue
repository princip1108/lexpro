<template>
  <div class="home">
    <div class="home-hero">
      <div class="home-logo">⚖️</div>
      <h1>法律大模型与检察业务融合应用系统</h1>
      <div class="sub">面向知识检索增强的多模态融合型法律大模型 · 检察业务智能辅助平台</div>
      <div v-for="badge in badges" :key="badge" class="home-badge">{{ badge }}</div>
      <div class="cta"><button class="btn primary lg" type="button" @click="$router.push('/login')">进入系统</button></div>
    </div>
    <div class="home-body">
      <div class="home-sec-title">五大核心功能</div>
      <div class="home-sec-sub">从卷宗解析到审查报告的全流程智能辅助</div>
      <div class="grid g3">
        <div v-for="item in modules" :key="item.title" class="module-card">
          <div class="module-icon" :style="{ background: item.background, color: item.color }" v-html="item.svg" />
          <h3>{{ item.title }}</h3><p>{{ item.description }}</p>
        </div>
      </div>
      <div class="home-sec-title" style="margin-top:44px">业务流程</div>
      <div class="home-sec-sub">卷宗录入 → 智能分析 → 业务融合</div>
      <div class="home-flow">
        <template v-for="(item, index) in steps" :key="item.title">
          <div class="home-step"><div class="n">{{ index + 1 }}</div><h4>{{ item.title }}</h4><p>{{ item.description }}</p></div>
          <div v-if="index < steps.length - 1" class="arrow">→</div>
        </template>
      </div>
      <div style="text-align:center;margin-top:40px"><button class="btn navy lg" type="button" @click="$router.push('/login')">登录系统</button></div>
    </div>
    <div class="home-foot">法律大模型与检察业务融合应用系统 · 支持规则引擎离线运行与大模型 API 无缝切换</div>
  </div>
</template>

<script setup>
const badges = ['文书实体识别', '法律要素识别', '案例摘要生成', '典型案例推送', '检察业务融合']
const modules = [
  { title: '文书实体识别', color: '#2563eb', background: '#eaf1fb', svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 5h16M4 9h16M4 13h10M4 17h6"/></svg>', description: '从询问笔录、起诉书、判决书中自动抽取犯罪嫌疑人、罪名、毒品、地名、时间、组织机构等核心实体。' },
  { title: '法律要素识别', color: '#b8922a', background: '#f6efda', svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 3h6M10 3v6M14 3v6M6 9h12v12H6z"/></svg>', description: '自动识别自首、坦白、累犯、认罪认罚、涉案金额、刑期等量刑要素,输出结构化、可校验的要素清单。' },
  { title: '案例摘要生成', color: '#1f9d55', background: '#e7f6ee', svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 10h16M4 14h10M4 18h6"/></svg>', description: '一键生成规范陈述式、精简列点式、类案对比式三种风格的案件摘要,支持人工编辑确认。' },
  { title: '典型案例推送', color: '#d9822b', background: '#fdf3e7', svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3l2 4h5l-4 3 2 5-5-3-5 3 2-5-4-3h5z"/></svg>', description: '基于结构化过滤与向量召回,结合事实与争议焦点融合排序,智能推送同类参考案例。' },
  { title: '检察业务融合', color: '#2563eb', background: '#eaf1fb', svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16v12H4zM8 7V4h8v3"/></svg>', description: '案卡回填、审查报告一键生成,覆盖受理、审查、出庭全流程的智能辅助与留痕管理。' }
]
const steps = [
  { title: '卷宗录入', description: '上传案件卷宗,自动解析文本' },
  { title: '智能分析', description: '实体 / 要素 / 摘要 / 类案推送' },
  { title: '业务融合', description: '案卡回填与审查报告生成' }
]
</script>
