/* ═══════════════════════════════════════════════════════════════════════
   app.js — YPDlp Frontend Logic
   ═══════════════════════════════════════════════════════════════════════ */

const API = "";   // same origin

async function parseResponse(resp) {
    const text = await resp.text();
    try {
        return JSON.parse(text);
    } catch (e) {
        throw new Error(`Server returned invalid response: ${text.substring(0, 100)}...`);
    }
}

// ─── State ───────────────────────────────────────────────────────────────
let currentInfo = null;
let currentType = "video";  // "video" | "audio"
let currentJobId = null;
let pollTimer = null;

// ─── DOM refs ────────────────────────────────────────────────────────────
const $url = document.getElementById("urlInput");
const $fetchBtn = document.getElementById("fetchBtn");
const $errorMsg = document.getElementById("errorMsg");
const $infoCard = document.getElementById("infoCard");
const $formatCard = document.getElementById("formatCard");
const $progressCard = document.getElementById("progressCard");
const $doneCard = document.getElementById("doneCard");

// ── Init: populate formats ──────────────────────────────────────────────
populateFormats("video");

// ── Enter key to fetch ──────────────────────────────────────────────────
$url.addEventListener("keydown", (e) => {
    if (e.key === "Enter") fetchInfo();
});


/* ═══════════════════════════════════════════════════════════════════════
   Fetch Video Info
   ═══════════════════════════════════════════════════════════════════════ */
async function fetchInfo() {
    const url = $url.value.trim();
    if (!url) return showError("Please enter a URL.");

    hideError();
    setFetchLoading(true);
    hideAll();

    try {
        const resp = await fetch(`${API}/api/info`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ url }),
        });
        const data = await parseResponse(resp);
        if (!resp.ok) throw new Error(data.error || "Failed to fetch info");

        currentInfo = data;
        renderInfo(data);
        $infoCard.hidden = false;
        $formatCard.hidden = false;
    } catch (err) {
        showError(err.message);
    } finally {
        setFetchLoading(false);
    }
}


/* ═══════════════════════════════════════════════════════════════════════
   Render Video Info
   ═══════════════════════════════════════════════════════════════════════ */
function renderInfo(info) {
    document.getElementById("infoThumb").src = info.thumbnail;
    document.getElementById("infoTitle").textContent = info.title;
    document.getElementById("infoChannel").textContent = `📺 ${info.channel}`;
    document.getElementById("infoDuration").textContent = info.duration;
    document.getElementById("infoViews").textContent = `👁 ${info.views} views`;
}


/* ═══════════════════════════════════════════════════════════════════════
   Type & Format Selectors
   ═══════════════════════════════════════════════════════════════════════ */
function setType(type) {
    currentType = type;
    // Toggle buttons
    document.querySelectorAll("#typeToggle .toggle-btn").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.value === type);
    });
    // Show/hide quality
    document.getElementById("qualityGroup").style.display = type === "video" ? "" : "none";
    populateFormats(type);
}

function populateFormats(type) {
    const sel = document.getElementById("formatSelect");
    sel.innerHTML = "";
    const options = type === "video"
        ? ["mp4", "mkv", "webm", "avi"]
        : ["mp3", "m4a", "flac", "wav", "ogg", "opus"];
    options.forEach(f => {
        const opt = document.createElement("option");
        opt.value = f;
        opt.textContent = f.toUpperCase();
        sel.appendChild(opt);
    });
}


/* ═══════════════════════════════════════════════════════════════════════
   Start Download
   ═══════════════════════════════════════════════════════════════════════ */
async function startDownload() {
    if (!currentInfo) return showError("Fetch video info first.");

    const container = document.getElementById("formatSelect").value;
    const quality = document.getElementById("qualitySelect").value;

    hideError();
    $formatCard.hidden = true;
    $progressCard.hidden = false;
    document.getElementById("progressTitle").textContent = "Starting download…";
    document.getElementById("progressBar").style.width = "0%";
    document.getElementById("progressPct").textContent = "0%";
    document.getElementById("progressSpeed").textContent = "—";
    document.getElementById("progressEta").textContent = "ETA —";

    try {
        const resp = await fetch(`${API}/api/download`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                url: currentInfo.url,
                format: container,
                quality: quality,
            }),
        });
        const data = await parseResponse(resp);
        if (!resp.ok) throw new Error(data.error || "Download failed");

        currentJobId = data.job_id;
        pollProgress();
    } catch (err) {
        showError(err.message);
        $progressCard.hidden = true;
        $formatCard.hidden = false;
    }
}


/* ═══════════════════════════════════════════════════════════════════════
   Poll Progress
   ═══════════════════════════════════════════════════════════════════════ */
function pollProgress() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
        try {
            const resp = await fetch(`${API}/api/status/${currentJobId}`);
            const data = await parseResponse(resp);

            if (data.status === "downloading" || data.status === "processing") {
                document.getElementById("progressTitle").textContent =
                    data.status === "processing" ? "Post-processing…" : "Downloading…";
                document.getElementById("progressBar").style.width = `${data.progress}%`;
                document.getElementById("progressPct").textContent = `${data.progress}%`;
                document.getElementById("progressSpeed").textContent = data.speed || "—";
                document.getElementById("progressEta").textContent = data.eta ? `ETA ${data.eta}` : "";
            } else if (data.status === "done") {
                clearInterval(pollTimer);
                pollTimer = null;
                showDone(data);
            } else if (data.status === "error") {
                clearInterval(pollTimer);
                pollTimer = null;
                showError(data.error || "Download failed");
                $progressCard.hidden = true;
                $formatCard.hidden = false;
            }
        } catch { /* ignore network hiccups */ }
    }, 800);
}


/* ═══════════════════════════════════════════════════════════════════════
   Done
   ═══════════════════════════════════════════════════════════════════════ */
function showDone(data) {
    $progressCard.hidden = true;
    $doneCard.hidden = false;

    document.getElementById("doneFilename").textContent = data.filename || "Download complete";
    document.getElementById("doneLink").href = `${API}/api/file/${currentJobId}`;
}


/* ═══════════════════════════════════════════════════════════════════════
   Reset
   ═══════════════════════════════════════════════════════════════════════ */
function resetUI() {
    currentInfo = null;
    currentJobId = null;
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    $url.value = "";
    hideAll();
}


/* ═══════════════════════════════════════════════════════════════════════
   Helpers
   ═══════════════════════════════════════════════════════════════════════ */
function hideAll() {
    $infoCard.hidden = true;
    $formatCard.hidden = true;
    $progressCard.hidden = true;
    $doneCard.hidden = true;
}

function showError(msg) {
    $errorMsg.textContent = msg;
    $errorMsg.hidden = false;
}

function hideError() {
    $errorMsg.hidden = true;
}

function setFetchLoading(loading) {
    const text = $fetchBtn.querySelector(".btn-text");
    const loader = $fetchBtn.querySelector(".btn-loader");
    if (loading) {
        text.textContent = "Fetching…";
        loader.hidden = false;
        $fetchBtn.disabled = true;
    } else {
        text.textContent = "Fetch Info";
        loader.hidden = true;
        $fetchBtn.disabled = false;
    }
}


/* ─── Smooth navbar on scroll ────────────────────────────────────────── */
window.addEventListener("scroll", () => {
    const nav = document.getElementById("navbar");
    nav.style.background = window.scrollY > 60
        ? "rgba(10,10,10,0.92)"
        : "rgba(10,10,10,0.72)";
});


/* ═══════════════════════════════════════════════════════════════════════
   Authentication — Cookie Upload
   ═══════════════════════════════════════════════════════════════════════ */

function toggleAuthPanel() {
    const modal = document.getElementById("authModal");
    modal.hidden = !modal.hidden;
    if (!modal.hidden) checkAuthStatus();
}

async function checkAuthStatus() {
    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 3000); // 3 second timeout

        const resp = await fetch(`${API}/api/auth-status`, { signal: controller.signal });
        clearTimeout(timeoutId);

        const data = await parseResponse(resp);
        const icon = document.getElementById("authStatusIcon");
        const text = document.getElementById("authStatusText");
        const dot = document.getElementById("authDot");
        const label = document.getElementById("authLabel");

        if (data.authenticated) {
            const method = data.method === "cookies" ? "Cookies" : "PO Token";
            icon.textContent = "🟢";
            text.textContent = `Authenticated via ${method}`;
            dot.className = "auth-dot auth-dot-green";
            label.textContent = "Signed In";
        } else {
            icon.textContent = "🔴";
            text.textContent = "Not authenticated — YouTube may block downloads";
            dot.className = "auth-dot auth-dot-red";
            label.textContent = "Sign In";
        }
    } catch {
        document.getElementById("authStatusIcon").textContent = "⚪";
        document.getElementById("authStatusText").textContent = "Could not check status (Server offline?)";
    }
}

// File input change
document.getElementById("cookieFileInput").addEventListener("change", (e) => {
    if (e.target.files.length) uploadCookieFile(e.target.files[0]);
});

// Drag and drop
const dropZone = document.getElementById("dropZone");
dropZone.addEventListener("dragover", (e) => { e.preventDefault(); dropZone.classList.add("drag-over"); });
dropZone.addEventListener("dragleave", () => dropZone.classList.remove("drag-over"));
dropZone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropZone.classList.remove("drag-over");
    if (e.dataTransfer.files.length) uploadCookieFile(e.dataTransfer.files[0]);
});

async function uploadCookieFile(file) {
    const msgEl = document.getElementById("authMsg");
    msgEl.hidden = false;
    msgEl.className = "auth-msg auth-msg-info";
    msgEl.textContent = "Uploading cookies...";

    const form = new FormData();
    form.append("file", file);

    try {
        const resp = await fetch(`${API}/api/upload-cookies`, { method: "POST", body: form });
        const data = await parseResponse(resp);
        if (!resp.ok) throw new Error(data.error || "Upload failed");

        msgEl.className = "auth-msg auth-msg-success";
        msgEl.textContent = "✔ " + data.message;
        checkAuthStatus();
    } catch (err) {
        msgEl.className = "auth-msg auth-msg-error";
        msgEl.textContent = "✘ " + err.message;
    }
}

async function clearCookies() {
    try {
        await fetch(`${API}/api/clear-cookies`, { method: "POST" });
        const msgEl = document.getElementById("authMsg");
        msgEl.hidden = false;
        msgEl.className = "auth-msg auth-msg-info";
        msgEl.textContent = "Cookies cleared.";
        checkAuthStatus();
    } catch { }
}

// Check auth on page load
checkAuthStatus();

