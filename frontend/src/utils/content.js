import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import css from 'highlight.js/lib/languages/css'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import markdown from 'highlight.js/lib/languages/markdown'

// 注册常用语言，避免引入完整 highlight.js 导致包体过大
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('css', css)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('markdown', markdown)

/** 语言名称映射，用于代码块左上角标签。 */
function languageLabel(codeEl) {
  const cls = codeEl.className || ''
  const match = cls.match(/language-([\w-]+)/)
  return match ? match[1] : ''
}

/** 复制文本到剪贴板，失败时退回 textarea 方案。 */
export async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  textarea.remove()
}

/**
 * 给富文本正文做增强处理：
 * 1. 给图片增加缩放光标，配合灯箱使用
 * 2. 给代码块增加语言标签、高亮和复制按钮
 */
export function decorateContent(root) {
  if (!root) return
  // 只处理正文里的代码块，避免误伤页面其他代码
  root.querySelectorAll('pre code').forEach((code) => {
    const pre = code.parentElement
    if (!pre || pre.dataset.decorated === '1') return

    // 高亮代码；语言未知时交给 highlight.js 自动识别
    try {
      const lang = languageLabel(code)
      if (lang && hljs.getLanguage(lang)) {
        code.innerHTML = hljs.highlight(code.textContent || '', { language: lang }).value
      } else {
        const auto = hljs.highlightAuto(code.textContent || '')
        code.innerHTML = auto.value
      }
      code.classList.add('hljs')
    } catch (e) {
      // 高亮失败时保留原代码
    }

    const toolbar = document.createElement('div')
    toolbar.className = 'code-toolbar'
    const label = document.createElement('span')
    label.className = 'code-lang'
    label.textContent = languageLabel(code) || 'code'
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.className = 'code-copy'
    btn.textContent = '复制'
    btn.addEventListener('click', async () => {
      try {
        await copyText(code.textContent || '')
        btn.textContent = '已复制'
      } catch (e) {
        btn.textContent = '复制失败'
      }
      setTimeout(() => {
        btn.textContent = '复制'
      }, 1600)
    })
    toolbar.appendChild(label)
    toolbar.appendChild(btn)
    pre.insertBefore(toolbar, pre.firstChild)
    pre.dataset.decorated = '1'
  })
}
