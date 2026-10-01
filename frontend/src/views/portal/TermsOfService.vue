<template>
  <div class="policy-page">
    <article class="policy card glass">
      <h1>用户协议</h1>
      <p class="meta">生效日期：{{ effectiveDate }}　|　适用站点：{{ siteName }}</p>

      <p>
        欢迎使用 {{ siteName }}（以下称"本站"）。本站是个人独立运营的非经营性博客。
        在你注册账号或发表评论之前，请仔细阅读本协议；注册、评论或继续使用本站，即视为你已同意本协议全部内容。
      </p>

      <h2>一、服务内容</h2>
      <ul>
        <li>本站提供文章浏览、公告、图片与资源展示、评论交流，以及"沙盒世界"等互动内容；</li>
        <li>本站不提供用户之间的即时通讯、群组聊天、文件互传等社交功能；</li>
        <li>服务可能因维护、升级或不可抗力临时中断，我们会尽量提前公告。</li>
      </ul>

      <h2>二、账号注册与安全</h2>
      <ul>
        <li>注册需通过邮箱验证码校验，请使用你本人可用的邮箱；</li>
        <li>账号仅限本人使用，不得转让、出租或与他人共享；</li>
        <li>请自行保管好密码，本站以 BCrypt 加盐哈希存储密码，无法找回明文，如忘记可通过邮箱重置；</li>
        <li>发现账号异常（被盗用、异常登录等）请立即修改密码并联系管理员。</li>
      </ul>

      <h2>三、用户行为规范</h2>
      <p>你在本站发布的一切内容（评论、昵称、头像等）不得含有下列信息：</p>
      <ul>
        <li>反对宪法确定的基本原则，危害国家安全、荣誉和利益的；</li>
        <li>煽动颠覆国家政权、分裂国家、破坏国家统一的；</li>
        <li>煽动民族仇恨、民族歧视，破坏民族团结的；</li>
        <li>宣扬恐怖主义、极端主义，或煽动实施恐怖活动的；</li>
        <li>散布谣言、虚假信息，扰乱经济秩序和社会秩序的；</li>
        <li>散布淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪的；</li>
        <li>侮辱、诽谤他人，侵害他人名誉权、隐私权、知识产权等合法权益的；</li>
        <li>发布广告、垃圾信息、恶意链接、外挂、木马等；</li>
        <li>法律、行政法规禁止的其他内容。</li>
      </ul>
      <p>
        本站对评论采取"敏感词过滤 + 后台审核 + 人工处置"的管理方式：评论提交后需审核通过才会公开展示；
        管理员有权对违规内容执行拒绝、删除、限制评论权限直至封禁账号的处理，并保留相关记录。
        任何用户均可对违规内容使用评论旁的「举报」按钮反馈，我们会在 24 小时内核实处置。
      </p>

      <h2>四、内容与知识产权</h2>
      <ul>
        <li>本站原创文章、图片与页面设计的著作权归本站运营者所有，转载请注明出处并保留原文链接；</li>
        <li>你发表的评论，其著作权归你所有，但你授予本站在站内展示、编辑排版与依规处置的权利；</li>
        <li>如你认为本站内容侵犯了你的权利，请联系我们，核实后将及时删除或更正。</li>
      </ul>

      <h2>五、违规处理</h2>
      <p>
        对违反本协议的用户，本站可依据情节采取：提醒、删除内容、限制评论、暂停或永久封禁账号等措施；
        涉嫌违法犯罪的，将依法向有关部门报告并配合调查。处理措施与依据会记录在后台审计日志中。
      </p>

      <h2>六、免责声明</h2>
      <ul>
        <li>本站内容仅供参考与交流，不构成任何专业建议；</li>
        <li>"沙盒世界"中的角色与剧情均由 AI 模型自动生成，属虚构内容，不代表本站观点；</li>
        <li>因不可抗力、网络故障、第三方服务（如邮箱、AI 接口、GitHub 评论组件）异常造成的服务中断或数据延迟，本站不承担责任，但会尽力恢复；</li>
        <li>你因使用本站内容而产生的任何后果，由你自行承担。</li>
      </ul>

      <h2>七、协议修改与终止</h2>
      <p>
        本站有权根据法律法规与运营需要修改本协议，修改后会在站内公告；继续使用即视为接受。
        你可以随时申请注销账号终止本协议；若你严重违反本协议，本站可立即停止向你提供服务。
      </p>

      <h2>八、法律适用与争议解决</h2>
      <p>本协议适用中华人民共和国法律。因本协议产生的争议，双方应友好协商；协商不成的，提交本站运营者所在地有管辖权的人民法院解决。</p>

      <h2>九、联系方式</h2>
      <p>
        运营者：{{ siteName }} 站长
        <template v-if="contactEmail"><br />联系邮箱：<a :href="'mailto:' + contactEmail">{{ contactEmail }}</a></template>
        <template v-else><br />联系邮箱：请见站内公告（管理员可在「系统设置 → 联系邮箱」中补充）</template>
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
