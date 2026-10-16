const byId = id => document.getElementById(id);
const CACHE_KEY = "recentCache";
const CACHE_AT_KEY = "recentCacheAt";
const LIMIT = 20;
let busy = false;

function setStatus(text, ok) {
  const node = byId("status");
  node.textContent = text || "";
  node.className = "status" + (text ? (ok ? " ok" : " err") : "");
}

function send(message) {
  return chrome.runtime.sendMessage(message);
}

function renderList(items) {
  const list = byId("list");
  if (!items.length) {
    list.innerHTML = '<li class="empty">还没有收藏</li>';
    return;
  }
  list.innerHTML = "";
  items.forEach(item => {
    const row = document.createElement("li");
    const word = document.createElement("div");
    word.className = "word";
    word.textContent = item.text;
    const trans = document.createElement("div");
    trans.className = "trans";
    trans.textContent = item.translation || "（无翻译）";
    row.appendChild(word);
    row.appendChild(trans);
    list.appendChild(row);
  });
}

function showAge(at) {
  const node = byId("freshness");
  if (!at) {
    node.textContent = "";
    return;
  }
  const seconds = Math.max(0, Math.round((Date.now() - at) / 1000));
  if (seconds < 60) node.textContent = seconds + " 秒前更新";
  else if (seconds < 3600) node.textContent = Math.round(seconds / 60) + " 分钟前更新";
  else node.textContent = Math.round(seconds / 3600) + " 小时前更新";
}

async function refresh() {
  byId("freshness").textContent = "同步中…";
  const response = await send({ type: "recent", limit: LIMIT });
  if (!response || !response.ok) {
    byId("freshness").textContent = "同步失败";
    if (!byId("list").children.length || byId("list").querySelector(".empty")) {
      const reason = response ? response.error : "扩展后台无响应";
      byId("list").innerHTML = '<li class="empty">读取失败：' + reason + '</li>';
    }
    return;
  }
  renderList(response.items);
  showAge(Date.now());
}

async function paintFromCache() {
  const cached = await chrome.storage.local.get([CACHE_KEY, CACHE_AT_KEY]);
  const items = cached[CACHE_KEY];
  if (!Array.isArray(items)) return false;
  renderList(items.slice(0, LIMIT));
  showAge(cached[CACHE_AT_KEY]);
  return true;
}

async function submit() {
  if (busy) return;
  const value = byId("text").value.trim();
  if (!value) {
    setStatus("请输入内容", false);
    return;
  }
  busy = true;
  byId("submit").disabled = true;
  setStatus("处理中…", true);
  const response = await send({ type: "add", text: value });
  busy = false;
  byId("submit").disabled = false;
  if (!response || !response.ok) {
    setStatus("失败：" + (response ? response.error : "扩展后台无响应"), false);
    return;
  }
  byId("text").value = "";
  setStatus(response.view.message, response.view.tone === "ok");
  refresh();
}

byId("submit").addEventListener("click", submit);
byId("text").addEventListener("keydown", event => {
  if (event.key === "Enter") submit();
});
byId("openOptions").addEventListener("click", () => chrome.runtime.openOptionsPage());

(async () => {
  const painted = await paintFromCache();
  if (!painted) byId("list").innerHTML = '<li class="empty">加载中…</li>';
  refresh();
})();
