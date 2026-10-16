const DEFAULT_BASE = "https://handwriter.asia/english";
const BUCKET = "default";
const LESSON_TITLE = "浏览器";
const MENU_ID = "add-to-wordbook";
const CACHE_KEY = "recentCache";
const CACHE_AT_KEY = "recentCacheAt";
const CACHE_LIMIT = 60;

function normalize(raw) {
  return String(raw == null ? "" : raw).replace(/\s+/g, " ").trim();
}

function looksLikeWord(text) {
  return text.length > 0 && !/\s/.test(text) && /^[A-Za-z'\u2019-]+$/.test(text);
}

function kindOf(text) {
  return looksLikeWord(text) ? "word" : "sentence";
}

function dedupeKey(kind, text) {
  return kind + "|" + normalize(text).toLowerCase();
}

async function backendBase() {
  const stored = await chrome.storage.sync.get("backendBase");
  const value = String(stored.backendBase || "").trim() || DEFAULT_BASE;
  return value.replace(/\/+$/, "");
}

async function request(base, items) {
  const response = await fetch(base + "/favorites/sync", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bucket: BUCKET, items })
  });
  if (!response.ok) throw new Error("服务返回 " + response.status);
  return response.json();
}

async function pullItems(base) {
  const data = await request(base, []);
  return data.items || [];
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
  const kind = kindOf(text);
  const key = dedupeKey(kind, text);
  const items = await pullItems(base);
  const existing = items.find(item => !item.deleted && dedupeKey(item.kind, item.text) === key);

  if (existing) {
    if (existing.translation) {
      return { text, translation: existing.translation, kind, status: "exists" };
    }
    const translation = await translate(base, text);
    if (!translation) {
      return { text, translation: "", kind, status: "exists" };
    }
    await request(base, [
      Object.assign({}, existing, { translation: translation, updatedAt: Date.now() })
    ]);
    return { text, translation, kind, status: "filled" };
  }

  const translation = await translate(base, text);
  const item = {
    syncId: crypto.randomUUID(),
    kind,
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
  await request(base, [item]);
  return { text, translation, kind, status: "added" };
}

async function fetchRecent(limit) {
  const base = await backendBase();
  const items = (await pullItems(base))
    .filter(item => !item.deleted)
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  await chrome.storage.local.set({
    [CACHE_KEY]: items.slice(0, CACHE_LIMIT),
    [CACHE_AT_KEY]: Date.now()
  });
  return items.slice(0, limit);
}

function describe(result) {
  if (result.status === "exists") return { message: "已在单词本里了：" + result.text, tone: "warn" };
  if (result.status === "filled") return { message: "已补充翻译：" + result.text, tone: "ok" };
  return { message: "已添加到单词本：" + result.text, tone: "ok" };
}

function setBadge(tone) {
  const map = { ok: ["\u2713", "#2E8B57"], warn: ["=", "#B8860B"], err: ["!", "#C0392B"] };
  const picked = map[tone] || map.ok;
  chrome.action.setBadgeBackgroundColor({ color: picked[1] });
  chrome.action.setBadgeText({ text: picked[0] });
  setTimeout(() => chrome.action.setBadgeText({ text: "" }), 2500);
}

function pageToast(message, tone, detail) {
  const COLORS = { ok: "#2E8B57", warn: "#B8860B", err: "#C0392B" };
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
    "background:" + (COLORS[tone] || COLORS.ok)
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

async function showToast(tabId, message, tone, detail) {
  if (!tabId) {
    setBadge(tone);
    return;
  }
  try {
    await chrome.scripting.executeScript({
      target: { tabId },
      func: pageToast,
      args: [message, tone, detail || ""]
    });
  } catch (error) {
    setBadge(tone);
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
    const view = describe(result);
    setBadge(view.tone);
    await showToast(tab && tab.id, view.message, view.tone, result.translation);
  } catch (error) {
    setBadge("err");
    await showToast(tab && tab.id, "添加失败：" + error.message, "err", "");
  }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "add") {
    addToWordbook(message.text)
      .then(result => sendResponse({ ok: true, result, view: describe(result) }))
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
