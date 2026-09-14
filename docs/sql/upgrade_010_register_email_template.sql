-- 更新默认注册验证码邮件模板
UPDATE sys_email_template SET content_html = '<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>二次元邮箱验证码 · 新风格</title>
</head>
<body style="margin:0; padding:0; background-color:#f5eef8; font-family: ''Segoe UI'', ''Yu Gothic'', ''Hiragino Sans'', ''Meiryo'', system-ui, sans-serif;">
  <div style="max-width:600px; margin:0 auto; padding:28px 16px; background-color:#f5eef8;">

    <!-- 主卡片：圆角柔和 + 渐变边框效果 -->
    <div style="background: linear-gradient(135deg, #ffffff, #fef7ff); border-radius:36px; box-shadow:0 20px 40px rgba(186, 140, 200, 0.18); border:1px solid #f0d9f0; padding:0; overflow:hidden; position:relative;">

      <!-- 顶部彩条：二次元渐变横幅 -->
      <div style="height:10px; background: linear-gradient(90deg, #ffb6d9, #d9b8ff, #b8e0ff, #ffb6d9); background-size: 300% 100%;"></div>

      <!-- 内容区 -->
      <div style="padding:28px 30px 26px;">

        <!-- 头部：左图标 + 标题（左右布局） -->
        <div style="display:flex; align-items:center; gap:14px; margin-bottom:6px;">
          <div style="flex-shrink:0; width:52px; height:52px; border-radius:50%; background: linear-gradient(145deg, #ffe0f0, #ffd0e8); display:flex; align-items:center; justify-content:center; font-size:26px; box-shadow:0 4px 12px rgba(255, 150, 200, 0.35);">
            ✉️
          </div>
          <div>
            <h2 style="margin:0; color:#8a4b6e; font-size:23px; font-weight:700; letter-spacing:0.3px;">
              {{siteName}} 邮箱验证
            </h2>
            <p style="margin:4px 0 0; color:#b98aa8; font-size:13px; letter-spacing:1px;">
              ✦ 一封来自二次元的小邮件 ✦
            </p>
          </div>
        </div>

        <!-- 分隔线 -->
        <div style="height:1px; background: linear-gradient(90deg, transparent, #f0d0e0, transparent); margin:20px 0 22px;"></div>

        <!-- 问候语 -->
        <p style="color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 6px;">
          你好呀～ <span style="color:#c95a82;">(｡•̀ᴗ-)✧</span>
        </p>
        <p style="color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 20px;">
          你正在注册 <strong style="color:#b3416b;">{{siteName}}</strong> 账号，本次的验证码是：
        </p>

        <!-- 验证码区域：左侧装饰条 + 大号验证码 -->
        <div style="display:flex; align-items:stretch; background: linear-gradient(120deg, #fff5fb, #f8f0ff); border-radius:24px; border:1.5px solid #f0d5f0; overflow:hidden; margin-bottom:20px; box-shadow: inset 0 2px 12px #fce8f8;">
          <!-- 左侧竖向装饰条 -->
          <div style="width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;"></div>
          <!-- 验证码内容 -->
          <div style="flex:1; padding:18px 20px; text-align:center;">
            <div style="font-size:13px; color:#b98aa8; letter-spacing:2px; margin-bottom:6px;">— 你的专属验证码 —</div>
            <div style="font-size:34px; font-weight:800; letter-spacing:10px; color:#c44b7a; text-shadow: 0 0 14px #ffcce0, 2px 2px 0 #ffe8f2;">
              {{code}}
            </div>
          </div>
          <!-- 右侧竖向装饰条 -->
          <div style="width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;"></div>
        </div>

        <!-- 有效期提示：淡底 + 小图标 -->
        <div style="display:flex; align-items:center; justify-content:center; gap:8px; background:#faf3ff; border-radius:50px; padding:10px 16px; margin-bottom:22px; border:1px dashed #e5c8e5;">
          <span style="font-size:16px;">⏳</span>
          <span style="color:#9b7a8e; font-size:14px;">验证码 5 分钟内有效，请勿泄露给他人。</span>
        </div>

        <!-- 小贴士区域：与整体风格统一 -->
        <div style="background:#fff8fd; border-radius:20px; padding:14px 18px; border-left:4px solid #d9b8ff; margin-bottom:8px;">
          <p style="margin:0; color:#8a6b7e; font-size:13.5px; line-height:1.8;">
            <span style="color:#b34e74; font-weight:600;">🎀 小贴士</span>
            <span style="color:#c9a5bc; margin:0 6px;">|</span>
            如果没收到邮件，记得看看垃圾箱哦～ 祝你注册顺利！
          </p>
        </div>

        <!-- 底部装饰：三个小圆点 -->
        <div style="text-align:center; margin-top:22px; letter-spacing:6px; color:#e5c8e5; font-size:12px;">
          ● ● ●
        </div>

        <!-- 页脚 -->
        <p style="color:#cbb0c4; font-size:11px; text-align:center; margin:18px 0 0; border-top:1px solid #f5e5f5; padding-top:14px; letter-spacing:0.5px;">
          ☆ {{siteName}} 二次元邮件站 ☆
        </p>

      </div>
    </div>

    <!-- 卡片下方小装饰 -->
    <div style="text-align:center; margin-top:14px; color:#d9b8d9; font-size:13px; opacity:0.6; letter-spacing:3px;">
      ✿ ～ ✿
    </div>
  </div>
</body>
</html>
', update_time = NOW() WHERE scenario = 'register_code';
