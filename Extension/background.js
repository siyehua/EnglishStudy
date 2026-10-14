const DEFAULT_BASE = "https://handwriter.asia/english";
const BUCKET = "default";
const LESSON_TITLE = "浏览器";
const MENU_ID = "add-to-wordbook";

function normalize(raw) {
  return String(raw == null ? "" : raw).replace(/\s+/g, " ").trim();
}

function looksLikeWord(text) {
  return text.length > 0 && !/\s/.test(text) && /^[A-Za-z'\u2019-]+$/.test(text);
}

function kindOf(text) {
  return looksLikeWord(text) ? "word" : "sentence";
}

async function backendBase() {
  const stored = await chrome.storage.sync.get("backendBase");
  const value = String(stored.backendBase || "").trim() || DEFAULT_BASE;
  return value.replace(/\/+$/, "");
}

async function translate(base, text) {
  const isWord = looksLikeWord(text);
  const payload = isWord
    ? { word: text, sentence: "" }
    : { word: text.split(" ")[0], sentence: text };
  const response = await fetch(base + "/word-meaning", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new Error("翻译服务返回 " + response.status);
  const data = await response.json();
  if (!isWord) return data.sentenceChinese || "";
  const joined = (data.meanings || [])
    .map(entry => (entry.partOfSpeech ? entry.partOfSpeech + " " + entry.meaning : entry.meaning))
    .filter(part => part && String(part).trim())
    .join("；");
  return joined || data.sentenceChinese || "";
}

async function addToWordbook(rawText) {
  const text = normalize(rawText);
  if (!text) throw new Error("没有可添加的文字");
  const base = await backendBase();
  const translation = await translate(base, text);
  const item = {
    syncId: crypto.randomUUID(),
    kind: kindOf(text),
    text,
    audioUrl: null,
    contentId: "",
    translation,
    lessonTitle: LESSON_TITLE,
    startTime: 0,
    endTime: 0,
    updatedAt: Date.now(),
    deleted: false
  };
  const response = await fetch(base + "/favorites/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bucket: BUCKET, items: [item] })
  });
  if (!response.ok) throw new Error("同步失败 " + response.status);
  return { text, translation, kind: item.kind };
}

async function fetchRecent(limit) {
  const base = await backendBase();
  const response = await fetch(base + "/favorites/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bucket: BUCKET, items: [] })
  });
  if (!response.ok) throw new Error("读取失败 " + response.status);
  const data = await response.json();
  return (data.items || [])
    .filter(item => !item.deleted)
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
    .slice(0, limit);
}

function setBadge(text, color) {
  chrome.action.setBadgeBackgroundColor({ color });
  chrome.action.setBadgeText({ text });
  setTimeout(() => chrome.action.setBadgeText({ text: "" }), 2500);
}

function pageToast(message, ok, detail) {
  const HOST_ID = "__english_study_toast__";
  const existing = document.getElementById(HOST_ID);
  if (existing) existing.remove();
  const host = document.createElement("div");
  host.id = HOST_ID;
  host.style.cssText =
    "position:fixed;left:0;right:0;bottom:28px;z-index:2147483647;display:flex;justify-content:center;pointer-events:none;";
  const root = host.attachShadow({ mode: "open" });
  const card = document.createElement("div");
  card.style.cssText = [
    "max-width:560px",
    "padding:12px 18px",
    "border-radius:12px",
    "text-align:center",
    "color:#fff",
    "box-shadow:0 8px 24px rgba(0,0,0,.25)",
    "font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif",
    "background:" + (ok ? "#2E8B57" : "#C0392B")
  ].join(";");
  const title = document.createElement("div");
  title.textContent = message;
  title.style.cssText = "font-weight:600;";
  card.appendChild(title);
  if (detail) {
    const sub = document.createElement("div");
    sub.textContent = detail;
    sub.style.cssText = "margin-top:4px;opacity:.9;font-size:13px;";
    card.appendChild(sub);
  }
  root.appendChild(card);
  document.documentElement.appendChild(host);
  setTimeout(() => host.remove(), 2800);
}

async function showToast(tabId, message, ok, detail) {
  if (!tabId) {
    setBadge(ok ? "✓" : "!", ok ? "#2E8B57" : "#C0392B");
    return;
  }
  try {
    await chrome.scripting.executeScript({
      target: { tabId },
      func: pageToast,
      args: [message, ok, detail || ""]
    });
  } catch (error) {
    setBadge(ok ? "✓" : "!", ok ? "#2E8B57" : "#C0392B");
  }
}

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: MENU_ID,
      title: "添加到单词本",
      contexts: ["selection"]
    });
  });
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId !== MENU_ID) return;
  try {
    const result = await addToWordbook(info.selectionText);
    setBadge("✓", "#2E8B57");
    await showToast(tab && tab.id, "已添加到单词本：" + result.text, true, result.translation);
  } catch (error) {
    setBadge("!", "#C0392B");
    await showToast(tab && tab.id, "添加失败：" + error.message, false, "");
  }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "add") {
    addToWordbook(message.text)
      .then(result => sendResponse({ ok: true, result }))
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  if (message && message.type === "recent") {
    fetchRecent(message.limit || 20)
      .then(items => sendResponse({ ok: true, items }))
      .catch(error => sendResponse({ ok: false, error: error.message }));
    return true;
  }
  return false;
});
