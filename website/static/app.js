/* ═══════════════════════════════════════════════════════════════════════
   app.js — YPDlp Frontend Logic
   ═══════════════════════════════════════════════════════════════════════ */

const API = "";   // same origin

// ─── State ───────────────────────────────────────────────────────────────
let currentInfo   = null;
let currentType   = "video";  // "video" | "audio"
let currentJobId  = null;
let pollTimer     = null;

// ─── DOM refs ────────────────────────────────────────────────────────────
const $url        = document.getElementById("urlInput");
const $fetchBtn   = document.getElementById("fetchBtn");
const $errorMsg   = document.getElementById("errorMsg");
const $infoCard   = document.getElementById("infoCard");
const $formatCard = document.getElementById("formatCard");
const $progressCard = document.getElementById("progressCard");
const $doneCard   = document.getElementById("doneCard");

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
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || "Failed to fetch info");

        currentInfo = data;
        renderInfo(data);
        $infoCard.hidden   = false;
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
    document.getElementById("infoThumb").src      = info.thumbnail;
    document.getElementById("infoTitle").textContent    = info.title;
    document.getElementById("infoChannel").textContent  = `📺 ${info.channel}`;
    document.getElementById("infoDuration").textContent = info.duration;
    document.getElementById("infoViews").textContent    = `👁 ${info.views} views`;
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
    const quality   = document.getElementById("qualitySelect").value;

    hideError();
    $formatCard.hidden    = true;
    $progressCard.hidden  = false;
    document.getElementById("progressTitle").textContent = "Starting download…";
    document.getElementById("progressBar").style.width   = "0%";
    document.getElementById("progressPct").textContent   = "0%";
    document.getElementById("progressSpeed").textContent = "—";
    document.getElementById("progressEta").textContent   = "ETA —";

    try {
        const resp = await fetch(`${API}/api/download`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                url:     currentInfo.url,
                format:  container,
                quality: quality,
            }),
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || "Download failed");

        currentJobId = data.job_id;
        pollProgress();
    } catch (err) {
        showError(err.message);
        $progressCard.hidden = true;
        $formatCard.hidden   = false;
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
            const data = await resp.json();

            if (data.status === "downloading" || data.status === "processing") {
                document.getElementById("progressTitle").textContent =
                    data.status === "processing" ? "Post-processing…" : "Downloading…";
                document.getElementById("progressBar").style.width = `${data.progress}%`;
                document.getElementById("progressPct").textContent  = `${data.progress}%`;
                document.getElementById("progressSpeed").textContent = data.speed || "—";
                document.getElementById("progressEta").textContent   = data.eta ? `ETA ${data.eta}` : "";
            } else if (data.status === "done") {
                clearInterval(pollTimer);
                pollTimer = null;
                showDone(data);
            } else if (data.status === "error") {
                clearInterval(pollTimer);
                pollTimer = null;
                showError(data.error || "Download failed");
                $progressCard.hidden = true;
                $formatCard.hidden   = false;
            }
        } catch { /* ignore network hiccups */ }
    }, 800);
}


/* ═══════════════════════════════════════════════════════════════════════
   Done
   ═══════════════════════════════════════════════════════════════════════ */
function showDone(data) {
    $progressCard.hidden = true;
    $doneCard.hidden     = false;

    document.getElementById("doneFilename").textContent = data.filename || "Download complete";
    document.getElementById("doneLink").href = `${API}/api/file/${currentJobId}`;
}


/* ═══════════════════════════════════════════════════════════════════════
   Reset
   ═══════════════════════════════════════════════════════════════════════ */
function resetUI() {
    currentInfo  = null;
    currentJobId = null;
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    $url.value = "";
    hideAll();
}


/* ═══════════════════════════════════════════════════════════════════════
   Helpers
   ═══════════════════════════════════════════════════════════════════════ */
function hideAll() {
    $infoCard.hidden     = true;
    $formatCard.hidden   = true;
    $progressCard.hidden = true;
    $doneCard.hidden     = true;
}

function showError(msg) {
    $errorMsg.textContent = msg;
    $errorMsg.hidden = false;
}

function hideError() {
    $errorMsg.hidden = true;
}

function setFetchLoading(loading) {
    const text   = $fetchBtn.querySelector(".btn-text");
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
