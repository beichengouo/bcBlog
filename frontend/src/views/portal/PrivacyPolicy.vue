<template>
  <div class="policy-page">
    <article class="policy card glass">
      <h1>隐私政策</h1>
      <p class="meta">生效日期：{{ effectiveDate }}　|　适用站点：{{ siteName }}</p>

      <p>
        本站（{{ siteName }}，以下称"本站"）由个人独立运营，为非经营性个人博客。
        本站重视你的个人信息安全，本政策说明我们会收集哪些信息、如何使用与保护这些信息，以及你可以如何行使自己的权利。
        使用本站即表示你已阅读并同意本政策。
      </p>

      <h2>一、我们收集哪些信息</h2>
      <ul>
        <li><b>账号信息</b>：注册时你填写的邮箱、昵称；密码以 <b>BCrypt 加盐哈希</b>形式存储，本站无法还原你的明文密码；头像为可选项，你可以随时留空。</li>
        <li><b>你发布的内容</b>：评论内容、发布时间、所属文章，以及必要的审核与处置记录（谁在什么时候做了通过/拒绝/删除操作）。</li>
        <li><b>日志信息</b>：访问与登录时服务器自动记录的<b>网络源 IP 地址、访问时间、请求地址、客户端浏览器标识（User-Agent）、操作类型与结果</b>。日志仅用于安全防护、故障排查与依法配合监管，不作他用。</li>
        <li><b>本站不收集</b>：身份证号、真实姓名、住址、手机号、银行卡等敏感个人信息；本站也没有定位、通讯录、相册等权限请求。</li>
      </ul>

      <h2>二、信息如何使用</h2>
      <ul>
        <li>提供账号登录、评论发布与审核、积分/等级等站内功能；</li>
        <li>安全防护：识别异常登录（例如异地或异常时段登录时给你发送提醒邮件）、防止刷评论与恶意注册；</li>
        <li>依法配合：在法律法规要求或监管部门、公安机关依法查询时，按程序提供相应记录。</li>
      </ul>

      <h2>三、Cookie 与本地存储</h2>
      <p>
        本站自身不使用第三方广告或统计 Cookie，仅在浏览器本地存储少量偏好设置，例如：白天/夜间主题、上次浏览的沙盒世界、
        天气卡片是否收起、手机端栏目导航选择。这些数据保存在你自己的浏览器中，清除浏览器数据即会消失。
        如果你使用第三方登录或评论组件（见下条），相关组件可能写入其自身的 Cookie。
      </p>

      <h2>四、信息存储与保护</h2>
      <ul>
        <li><b>存储地点</b>：数据库与服务器均位于中华人民共和国境内。</li>
        <li><b>加密措施</b>：账号密码使用 BCrypt 加盐哈希；邮件授权码、第三方接口密钥等敏感配置使用 <b>AES-GCM 加密存储</b>，主密钥保存在服务器本地文件中，不随数据库一起泄露。</li>
        <li><b>权限控制</b>：后台按"超级管理员 / 一级管理员 / 二级管理员"分级授权，敏感菜单需二次校验安全密码；所有后台操作留有审计日志。</li>
        <li><b>留存期限</b>：账号信息在你注销前持续保存；访问与操作日志按法律法规要求留存<b>不少于六个月</b>，到期自动清理；评论在你或管理员删除后不再展示，处置记录按规定保留。</li>
        <li><b>备份</b>：数据库定期备份，备份文件同样存放于境内服务器。</li>
      </ul>

      <h2>五、我们如何共享信息</h2>
      <ul>
        <li>本站<b>不出售、不出租</b>你的个人信息，也不将其用于广告投放；</li>
        <li>除法律法规要求或监管、司法机关依法查询外，不会向第三方提供你的个人信息；</li>
        <li><b>可选的第三方组件</b>：若本站开启了 Gitalk 评论（基于 GitHub Issues），你的评论将发布到对应的 GitHub 仓库，此时数据由 GitHub 按其隐私政策处理；本站前台的天气与 IP 归属地查询会向第三方接口（uapis.cn）发送请求以获取展示用的天气/地域信息，不涉及你的账号信息；</li>
        <li>站内"沙盒世界"中的角色由 AI 模型生成，调用的是第三方 AI 服务商接口，发送的内容为角色设定与剧情文本，<b>不包含你的账号信息</b>。</li>
      </ul>

      <h2>六、你的权利</h2>
      <ul>
        <li><b>查询与更正</b>：登录后可在「用户中心」查看和修改昵称、头像等资料；</li>
        <li><b>删除</b>：你可以申请删除自己的评论与账号，我们会在核实身份后的 7 个工作日内处理；</li>
        <li><b>注销账号</b>：可联系管理员注销，注销后你的账号信息将被删除或匿名化，法律要求留存的日志不在此列；</li>
        <li><b>投诉与举报</b>：如发现违法有害信息或认为你的权益受到影响，可通过评论旁的「举报」按钮或下方联系方式反馈，我们会在 24 小时内处理。</li>
      </ul>

      <h2>七、未成年人保护</h2>
      <p>
        本站面向成年用户。若你未满 18 周岁，请在监护人指导下使用本站，并避免填写任何与服务无关的个人信息。
        我们不会主动收集未成年人的个人信息；如发现误收集，将及时删除。
      </p>

      <h2>八、政策更新</h2>
      <p>本政策如发生重大变更，我们会在本站以公告形式提示。继续使用本站即表示接受更新后的政策。</p>

      <h2>九、联系方式</h2>
      <p>
        运营者：{{ siteName }} 站长
        <template v-if="contactEmail"><br />联系邮箱：<a :href="'mailto:' + contactEmail">{{ contactEmail }}</a></template>
        <template v-else><br />联系邮箱：请见站内公告（管理员可在「系统设置 → 联系邮箱」中补充）</template>
        <br />我们将在收到反馈后的 24 小时内响应，涉及个人信息权利的请求在 7 个工作日内处理。
      </p>
    </article>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPortalConfig } from '@/api/config'

const siteName = ref('本站')
const contactEmail = ref('')
const effectiveDate = '2026-10-01'

onMounted(async () => {
  try {
    const cfg = await getPortalConfig()
    siteName.value = cfg.siteName || '本站'
    contactEmail.value = cfg.siteContactEmail || ''
  } catch (e) {
    // 配置读取失败时用默认文案
  }
})
</script>

<style scoped>
.policy-page {
  max-width: 900px;
  margin: 0 auto;
  padding: calc(var(--header-height) + 28px) 20px 48px;
}
.policy {
  padding: 28px 30px 34px;
  border-radius: 18px;
  line-height: 1.9;
  color: var(--text);
}
.policy h1 {
  margin: 0 0 6px;
  font-size: 24px;
  color: var(--text-strong);
}
.policy .meta {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--text-muted);
}
.policy h2 {
  margin: 26px 0 8px;
  font-size: 16px;
  color: var(--text-strong);
}
.policy ul {
  margin: 0;
  padding-left: 20px;
}
.policy li {
  margin-bottom: 6px;
}
.policy a {
  color: var(--accent);
}
@media (max-width: 640px) {
  .policy {
    padding: 20px 16px 24px;
  }
}
</style>
