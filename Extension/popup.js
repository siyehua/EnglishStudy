const byId = id => document.getElementById(id);
let busy = false;

function setStatus(text, ok) {
  const node = byId("status");
  node.textContent = text || "";
  node.className = "status" + (text ? (ok ? " ok" : " err") : "");
}

function send(message) {
  return chrome.runtime.sendMessage(message);
}

async function loadRecent() {
  const list = byId("list");
  list.innerHTML = '<li class="empty">加载中…</li>';
  const response = await send({ type: "recent", limit: 20 });
  if (!response || !response.ok) {
    const reason = response ? response.error : "扩展后台无响应";
    list.innerHTML = '<li class="empty">读取失败：' + reason + '</li>';
    return;
  }
  if (!response.items.length) {
    list.innerHTML = '<li class="empty">还没有收藏</li>';
    return;
  }
  list.innerHTML = "";
  response.items.forEach(item => {
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
  setStatus("已添加：" + (response.result.translation || response.result.text), true);
  loadRecent();
}

byId("submit").addEventListener("click", submit);
byId("text").addEventListener("keydown", event => {
  if (event.key === "Enter") submit();
});
byId("openOptions").addEventListener("click", () => chrome.runtime.openOptionsPage());
loadRecent();
