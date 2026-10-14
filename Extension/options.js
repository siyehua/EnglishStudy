const DEFAULT_BASE = "https://handwriter.asia/english";
const byId = id => document.getElementById(id);

function flash(text) {
  byId("status").textContent = text;
  setTimeout(() => { byId("status").textContent = ""; }, 1800);
}

async function restore() {
  const stored = await chrome.storage.sync.get("backendBase");
  byId("backendBase").value = stored.backendBase || DEFAULT_BASE;
}

byId("save").addEventListener("click", async () => {
  const value = byId("backendBase").value.trim().replace(/\/+$/, "");
  await chrome.storage.sync.set({ backendBase: value || DEFAULT_BASE });
  byId("backendBase").value = value || DEFAULT_BASE;
  flash("已保存");
});

byId("reset").addEventListener("click", async () => {
  await chrome.storage.sync.set({ backendBase: DEFAULT_BASE });
  byId("backendBase").value = DEFAULT_BASE;
  flash("已恢复默认");
});

restore();
