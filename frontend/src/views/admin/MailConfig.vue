<template>
  <div class="page-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">邮件配置（SMTP）</span>
          <div class="header-right">
            <span class="mode-label">发送模式：</span>
            <el-switch
              v-model="form.enabled"
              active-text="真实发送"
              inactive-text="模拟发送"
              inline-prompt
              style="width: 110px"
            />
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        style="max-width: 720px"
      >
        <el-divider content-position="left">SMTP 服务器</el-divider>

        <el-form-item label="SMTP 服务器" prop="host">
          <el-input v-model="form.host" placeholder="如 smtp.qq.com" clearable />
        </el-form-item>

        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" :step="1" controls-position="right" />
          <span class="field-hint">QQ/163 常用 465（SSL）或 587（STARTTLS）</span>
        </el-form-item>

        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" placeholder="发件邮箱，如 your-email@qq.com" clearable />
        </el-form-item>

        <el-form-item label="SMTP 授权码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="passwordSet ? '已配置授权码，留空表示不修改' : '请填写邮箱生成的 SMTP 授权码（非登录密码）'"
          />
        </el-form-item>

        <el-form-item label="加密方式">
          <div class="switch-group">
            <el-switch v-model="form.useAuth" active-text="需要认证" />
            <el-switch v-model="form.useSsl" active-text="SSL（端口 465）" />
            <el-switch v-model="form.useStarttls" active-text="STARTTLS（端口 587）" />
          </div>
          <div class="field-hint">SSL 与 STARTTLS 二选一，不要同时开启</div>
        </el-form-item>

        <el-divider content-position="left">发件与邮件内容</el-divider>

        <el-form-item label="发件人地址" prop="fromAddr">
          <el-input v-model="form.fromAddr" placeholder="留空则自动使用登录账号（推荐）" clearable />
        </el-form-item>

        <el-form-item label="发件人显示名">
          <el-input v-model="form.fromName" placeholder="高校教务系统" clearable />
        </el-form-item>

        <el-form-item label="主题前缀">
          <el-input v-model="form.subjectPrefix" placeholder="【学业预警】" clearable />
        </el-form-item>

        <el-form-item label="落款单位">
          <el-input v-model="form.sender" placeholder="教务处" clearable />
        </el-form-item>

        <el-form-item label="学生查询入口">
          <el-input v-model="form.portalUrl" placeholder="http://localhost:5173" clearable />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-button :loading="testing" @click="handleTest">发送测试邮件</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 测试结果 -->
    <el-dialog v-model="resultVisible" title="测试邮件结果" width="560px">
      <el-result
        v-if="testResult.success"
        icon="success"
        title="发送成功"
        :sub-title="testResult.message"
      />
      <el-result v-else icon="error" title="发送失败" :sub-title="testResult.message">
            <template #extra>
              <div v-if="testResult.hint" class="error-hint">{{ testResult.hint }}</div>
            </template>
          </el-result>
      <el-descriptions v-if="testResult.success || testResult.host" :column="1" border size="small" class="result-desc">
        <el-descriptions-item label="收件人">{{ testResult.to }}</el-descriptions-item>
        <el-descriptions-item label="SMTP 服务器">{{ testResult.host }}:{{ testResult.port }}</el-descriptions-item>
        <el-descriptions-item label="登录账号">{{ testResult.username }}</el-descriptions-item>
        <el-descriptions-item label="发件人">{{ testResult.from }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchMailSettings, saveMailSettings, sendMailTest } from '@/api/mailConfig'

const formRef = ref(null)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const passwordSet = ref(false)
const resultVisible = ref(false)
const testResult = reactive({ success: false, message: '', hint: '', to: '', host: '', port: '', username: '', from: '' })

const form = reactive({
  enabled: false,
  host: '',
  port: 465,
  username: '',
  password: '',
  useAuth: true,
  useSsl: true,
  useStarttls: false,
  fromAddr: '',
  fromName: '高校教务系统',
  subjectPrefix: '【学业预警】',
  portalUrl: 'http://localhost:5173',
  sender: '教务处'
})

const rules = {
  host: [{ required: true, message: '请填写 SMTP 服务器地址', trigger: 'blur' }],
  port: [{ required: true, message: '请填写端口', trigger: 'blur' }],
  username: [{ required: true, message: '请填写发件邮箱账号', trigger: 'blur' }],
  fromAddr: [{ type: 'email', message: '发件人邮箱格式不正确', trigger: 'blur' }]
}

async function loadConfig() {
  loading.value = true
  try {
    const res = await fetchMailSettings()
    const d = res.data || {}
    form.enabled = !!d.enabled
    form.host = d.host || ''
    form.port = d.port || 465
    form.username = d.username || ''
    form.password = ''
    passwordSet.value = !!d.passwordSet
    form.useAuth = d.useAuth !== false
    form.useSsl = !!d.useSsl
    form.useStarttls = !!d.useStarttls
    form.fromAddr = d.fromAddr || ''
    form.fromName = d.fromName || '高校教务系统'
    form.subjectPrefix = d.subjectPrefix || '【学业预警】'
    form.portalUrl = d.portalUrl || 'http://localhost:5173'
    form.sender = d.sender || '教务处'
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const res = await saveMailSettings({ ...form })
    passwordSet.value = !!(res.data?.passwordSet ?? true)
    form.password = ''
    ElMessage.success('邮件配置已保存并即时生效')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  let to = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入测试邮件收件人（留空则发送到你本人登记的邮箱）', '发送测试邮件', {
      confirmButtonText: '发送',
      cancelButtonText: '取消',
      inputPlaceholder: '收件邮箱，留空发给自己',
      inputValidator: (v) => !v || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) || '邮箱格式不正确'
    })
    to = (value || '').trim()
  } catch (e) {
    return
  }

  testing.value = true
  try {
    const res = await sendMailTest(to)
    Object.assign(testResult, {
      success: !!res.data?.success,
      message: res.data?.message || '',
      hint: res.data?.hint || '',
      to: res.data?.to || to,
      host: res.data?.host || '',
      port: res.data?.port || '',
      username: res.data?.username || '',
      from: res.data?.from || ''
    })
    resultVisible.value = true
  } finally {
    testing.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
}

.mode-label {
  font-size: 13px;
  color: #606266;
  margin-right: 8px;
}

.switch-group {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.error-hint {
  max-width: 420px;
  margin: 0 auto;
  font-size: 13px;
  color: #e6a23c;
  text-align: left;
}

.result-desc {
  margin-top: 8px;
}
</style>
