let myChart;
let totalSeconds = 0;
let timerInterval = null;
let isTimerRunning = false;

// ── Web Audio API アラーム ────────────────────────────────────────────────
// ユーザーのタップ操作（toggleMainTimer）でAudioContextを初期化する。
// ブラウザの自動再生ポリシー対策: ユーザー操作なしに音を鳴らすとブロックされる。
// iOS のマナーモードはブラウザからは制御不可。イヤホン接続時は多くの場合鳴る。
let _audioCtx = null;

function ensureAudioContext() {
    if (!_audioCtx) {
        _audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (_audioCtx.state === 'suspended') {
        _audioCtx.resume();
    }
}

function playBeep(frequency, duration, volume) {
    if (!_audioCtx) return;
    const osc = _audioCtx.createOscillator();
    const gain = _audioCtx.createGain();
    osc.connect(gain);
    gain.connect(_audioCtx.destination);
    osc.frequency.value = frequency;
    osc.type = 'sine';
    gain.gain.setValueAtTime(volume, _audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, _audioCtx.currentTime + duration);
    osc.start(_audioCtx.currentTime);
    osc.stop(_audioCtx.currentTime + duration);
}

function playWarningBeep() {
    playBeep(660, 0.2, 0.7);
}

function playEndAlarm() {
    playBeep(1047, 0.35, 0.9);
    setTimeout(() => playBeep(1047, 0.35, 0.9), 420);
    setTimeout(() => playBeep(1047, 0.35, 0.9), 840);
    if (navigator.vibrate) navigator.vibrate([300, 120, 300, 120, 300]);
}
// ──────────────────────────────────────────────────────────────────────────
// 選択されたトレーニング一覧（別ファイルと共通で使えるようグローバルで初期化）
let selectedTrainings = window.selectedTrainings || [];
// 参照をグローバルに公開（他スクリプトと共有）
window.selectedTrainings = selectedTrainings;

// タイマー用の経過時間をパース
function parseTimeToSeconds(timeString) {
    const [h, m, s] = (timeString || '00:00:00').split(':').map(Number);
    return h * 3600 + m * 60 + s;
}

// 秒数を HH:MM:SS 形式に変換
function formatDuration(seconds) {
    const h = Math.floor(seconds / 3600).toString().padStart(2, '0');
    const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${h}:${m}:${s}`;
}

// DOMContentLoaded 時にタイマーを初期化
function initializeTimer() {
    const initialDurationElem = document.getElementById('initialDuration');
    if (initialDurationElem) {
        totalSeconds = parseTimeToSeconds(initialDurationElem.value);
        document.getElementById('totalTimer').textContent = formatDuration(totalSeconds);
    }
}

// 1. カレンダー・共通
function selectDate(date) {
    const titleElement = document.getElementById('selected-date-title');
    const listElement = document.getElementById('training-list');

    // メニュー画面は詳細パネルを持たないため、日付クリック時は画面遷移する。
    if (!titleElement || !listElement) {
        window.location.href = "/menu?date=" + encodeURIComponent(date);
        return;
    }

    titleElement.innerText = date + " のトレーニング詳細";
    listElement.innerHTML = '<li>読み込み中...</li>';

    const userId = document.getElementById('userIdForGraph')?.value;
    const query = userId
        ? `/admin/api/training-details?userId=${encodeURIComponent(userId)}&date=${encodeURIComponent(date)}`
        : `/admin/api/training-details?date=${encodeURIComponent(date)}`;

    // サーバーにその日のデータをリクエスト
    fetch(query)
        .then(response => response.json())
        .then(data => {
            listElement.innerHTML = ''; // クリア
            
            if (data.length === 0) {
                const li = document.createElement('li');
                li.textContent = 'この日の記録はありません';
                listElement.appendChild(li);
                return;
            }

            // 取得したデータをリストに追加
            data.forEach(item => {
                const li = document.createElement('li');
                li.style.padding = "8px";
                li.style.borderBottom = "1px solid #eee";
                const menuName = item.menuName || item.menu || `種目ID:${item.trainingId ?? '-'}`;
                const weight = item.weight ?? '-';
                const reps = item.reps ?? '-';
                const sets = item.sets ?? item.setNumber ?? '-';
                li.textContent = `${menuName}: ${weight}kg x ${reps}回 (${sets}セット)`;
                listElement.appendChild(li);
            });
        })
        .catch(error => {
            listElement.innerHTML = '';
            const li = document.createElement('li');
            li.style.color = 'red';
            li.textContent = 'データの取得に失敗しました';
            listElement.appendChild(li);
        });
}

// 別タブで登録ページを開く
function openRegisterPage(date) {
    window.open(`/training/register?date=${encodeURIComponent(date)}`, '_blank');
}

// メニュー画面用編集機能
let currentEditingTraining = null;
let originalTrainingData = null;

// 編集モーダルを開く
function openEditModal(trainingId, menu, partCode, trainingDate, details) {
    // 画面に表示されているデータを元にトレーニングオブジェクトを作成
    currentEditingTraining = {
        id: trainingId,
        menu: menu,
        partCode: partCode,
        trainingDate: trainingDate,
        details: details.map(detail => ({
            setNumber: detail.setNumber,
            weight: detail.weight,
            reps: detail.reps,
            isCompleted: detail.isCompleted
        }))
    };
    
    originalTrainingData = JSON.parse(JSON.stringify(currentEditingTraining));
    
    // モーダルタイトルを設定
    document.getElementById('editModalTitle').textContent = `${menu} - 編集`;
    
    // セット一覧を描画
    renderEditSets();
    
    // モーダルを表示
    document.getElementById('editTrainingModal').style.display = 'flex';
}

// 編集用セット一覧を描画
function renderEditSets() {
    const container = document.getElementById('editSetsContainer');
    const training = currentEditingTraining;
    
    let html = '';
    
    // セット一覧
    training.details.forEach((detail, setIndex) => {
        html += `
            <div style="display: flex; align-items: center; gap: 10px; padding: 12px; margin: 8px 0; background: #f9f9f9; border-radius: 8px; border: 1px solid #e0e0e0;">
                <span style="font-weight: bold; min-width: 30px; color: #666;">${setIndex + 1}</span>
                <div style="display: flex; gap: 15px; flex-grow: 1; align-items: center;">
                    <input type="number" id="editWeight-${setIndex}" value="${detail.weight != null ? detail.weight : ''}" step="0.5" data-index="${setIndex}" data-change="updateEditSet" placeholder="0" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
                    <label style="color: #666; font-size: 0.9em;">kg</label>
                    <input type="number" id="editReps-${setIndex}" value="${detail.reps != null ? detail.reps : ''}" data-index="${setIndex}" data-change="updateEditSet" placeholder="0" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
                    <label style="color: #666; font-size: 0.9em;">回</label>
                </div>
                <button type="button" data-action="removeEditSet" data-index="${setIndex}" style="background: none; border: none; color: #f44336; font-size: 1.2em; cursor: pointer; padding: 0;">✕</button>
            </div>
        `;
    });
    
    // セット追加ボタン
    html += `
        <button type="button" data-action="addEditSet" style="width: 100%; padding: 12px; margin-top: 15px; background: #e3f2fd; border: 1px solid #2196F3; color: #2196F3; border-radius: 6px; cursor: pointer; font-weight: bold;">
            + セットを追加
        </button>
    `;
    
    // 総ボリューム表示
    const totalVolume = training.details.reduce((sum, detail) => sum + (detail.weight * detail.reps), 0);
    html += `
        <div style="background: #f0f8ff; padding: 12px; border-radius: 6px; margin-top: 15px; text-align: right; font-size: 0.95em; color: #333;">
            総ボリューム: <strong style="color: #ff9800; font-size: 1.1em;">${totalVolume.toFixed(1)} kg</strong>
        </div>
    `;
    
    container.innerHTML = html;

    // Attach handlers for inputs and buttons inserted via innerHTML (CSP-safe)
    container.querySelectorAll('input[data-change]').forEach(inp => {
        const idx = parseInt(inp.dataset.index, 10);
        if (!isNaN(idx)) {
            inp.addEventListener('change', () => updateEditSet(idx));
        }
    });
    const addBtn = container.querySelector('button[data-action="addEditSet"]');
    if (addBtn) addBtn.addEventListener('click', () => addEditSet());
    container.querySelectorAll('button[data-action="removeEditSet"]').forEach(b => {
        b.addEventListener('click', (e) => {
            const idx = parseInt(b.dataset.index, 10);
            removeEditSet(idx);
        });
    });
}

// 編集セットの更新
function updateEditSet(setIndex) {
    const weightValue = document.getElementById(`editWeight-${setIndex}`).value;
    const repsValue = document.getElementById(`editReps-${setIndex}`).value;
    const weight = weightValue === '' ? 0 : parseFloat(weightValue);
    const reps = repsValue === '' ? 0 : parseInt(repsValue);
    
    currentEditingTraining.details[setIndex].weight = weight;
    currentEditingTraining.details[setIndex].reps = reps;
    
    // ボリューム表示を更新
    updateEditVolumeDisplay();
}

// 編集セットの削除
function removeEditSet(setIndex) {
    if (currentEditingTraining.details.length > 1) {
        currentEditingTraining.details.splice(setIndex, 1);
        // セット番号を更新
        currentEditingTraining.details.forEach((detail, idx) => {
            detail.setNumber = idx + 1;
        });
        renderEditSets();
    } else {
        // 最後の1セットは削除できない
    }
}

// 編集セットの追加
function addEditSet() {
    let lastWeight = 0, lastReps = 0;
    
    if (currentEditingTraining.details.length > 0) {
        const lastSet = currentEditingTraining.details[currentEditingTraining.details.length - 1];
        lastWeight = lastSet.weight || 0;
        lastReps = lastSet.reps || 0;
    }
    
    currentEditingTraining.details.push({
        weight: lastWeight,
        reps: lastReps,
        setNumber: currentEditingTraining.details.length + 1,
        completed: false
    });
    
    renderEditSets();
}

// 編集ボリューム表示を更新
function updateEditVolumeDisplay() {
    const totalVolume = currentEditingTraining.details.reduce((sum, detail) => sum + (detail.weight * detail.reps), 0);
    
    const volumeDisplay = document.querySelector('#editSetsContainer strong');
    if (volumeDisplay) {
        volumeDisplay.textContent = `${totalVolume.toFixed(1)} kg`;
    }
}

// 編集確定
function saveEditModal() {
    // CSRFトークンを取得
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    
    const headers = {
        'Content-Type': 'application/json'
    };
    
    if (token && header) {
        headers[header] = token;
    }
    
    // 更新データを送信
    fetch(`/api/training/update/${currentEditingTraining.id}`, {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(currentEditingTraining)
    })
    .then(response => {
        if (response.ok) {
            closeEditModal();
            // ページをリロードして反映
            location.reload();
        }
    })
    .catch(error => {
        // エラー処理
    });
}

// 編集キャンセル
function closeEditModal() {
    document.getElementById('editTrainingModal').style.display = 'none';
    currentEditingTraining = null;
    originalTrainingData = null;
}

// "hh:mm:ss" を秒数に変換するヘルパー関数
function timeToSeconds(timeStr) {
    const parts = timeStr.split(':');
    const hrs = parseInt(parts[0], 10) || 0;
    const mins = parseInt(parts[1], 10) || 0;
    const secs = parseInt(parts[2], 10) || 0;
    return (hrs * 3600) + (mins * 60) + secs;
}

function toggleMainTimer() {
    // ユーザー操作のタイミングでAudioContextを初期化（自動再生ポリシー対策）
    ensureAudioContext();
    const btn = document.getElementById('startBtn');
    if (!isTimerRunning) {
        isTimerRunning = true;
        btn.textContent = "一時停止";
        btn.classList.add('btn-success');
        btn.classList.remove('btn-warning');
        timerInterval = setInterval(() => {
            totalSeconds++;
            updateTimerDisplay();
        }, 1000);
    } else {
        isTimerRunning = false;
        btn.textContent = "再開";
        btn.classList.remove('btn-success');
        btn.classList.add('btn-warning');
        clearInterval(timerInterval);
    }
}

// タイマー統計情報を取得
function getTimerState() {
    return {
        totalSeconds: totalSeconds,
        isRunning: isTimerRunning,
        durationString: formatDuration(totalSeconds)
    };
}

// タイマー表示を更新
function updateTimerDisplay() {
    const timerDisplay = document.getElementById('totalTimer');
    if (timerDisplay) {
        timerDisplay.textContent = formatDuration(totalSeconds);
    }
}

let timeLeft = 0;
let remaining = 0;

function setIntervalTime(seconds) {
    // 現在動いているタイマーの変数が 'timeLeft' だとしたら
    timeLeft = seconds; 
    document.getElementById('intervalTime').innerText = timeLeft;
}

function handleCheck(el) {
    if (!el) return;
    el.classList.toggle('completed');
    if (!isTimerRunning) {
        toggleMainTimer();
    }
    if (el.classList.contains('completed')) {
        startInterval(120); // 120秒開始
    } else {
        stopInterval();
    }
}

let intervalTimerId = null;
let intervalRemaining = 0; // 残り時間を管理する変数を追加

function startInterval(seconds) {
    stopInterval();

    const banner = document.getElementById('intervalBanner');
    const timerDisplay = document.getElementById('intervalTimer');
    if (!banner || !timerDisplay) return;

    banner.style.display = 'block';
    intervalRemaining = seconds; // 初期値をセット
    
    updateIntervalDisplay(timerDisplay, intervalRemaining);

    intervalTimerId = setInterval(() => {
        intervalRemaining--;

        if (intervalRemaining > 0) {
            updateIntervalDisplay(timerDisplay, intervalRemaining);
            if (intervalRemaining === 10) {
                playWarningBeep();
                if (navigator.vibrate) navigator.vibrate(200);
            }
        } else {
            updateIntervalDisplay(timerDisplay, 0);
            stopInterval();
            playEndAlarm();
            showIntervalEndBanner();
        }
    }, 1000);
}

// 共通の表示更新関数
function updateIntervalDisplay(el, seconds) {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    el.textContent = `${m}:${s}`;
}

// ボタン押下で呼ばれる関数
function changeInterval(delta) {
    const timerDisplay = document.getElementById('intervalTimer');
    if (!timerDisplay) return;

    // 変数 intervalRemaining を直接操作する
    intervalRemaining += delta;
    if (intervalRemaining < 0) intervalRemaining = 0;

    // 表示を即時更新
    updateIntervalDisplay(timerDisplay, intervalRemaining);
}

function stopInterval() {
    if (intervalTimerId) {
        clearInterval(intervalTimerId);
        intervalTimerId = null;
    }
    const banner = document.getElementById('intervalBanner');
    if (banner) banner.style.display = 'none';
}

function showIntervalEndBanner() {
    const banner = document.getElementById('intervalBanner');
    if (!banner) return;
    banner.style.display = 'block';
    banner.style.background = '#4caf50';
    banner.innerHTML = 'インターバル終了！次のセットを開始してください';
    setTimeout(() => {
        banner.style.display = 'none';
        banner.style.background = '';
    }, 3000);
}

// グローバル公開
window.handleCheck = handleCheck;
window.startInterval = startInterval;
window.stopInterval = stopInterval;

// 3. セット操作（実技・モーダル共通）
function addSet(btn) {
    // 既存のインライン追加はやめ、モーダルでセット追加する UX に変更
    const card = btn.closest('.training-card');
    if (!card) return;
    const trainingDate = card.getAttribute('data-training-date') || document.getElementById('modalDate')?.value;
    const trainingId = card.getAttribute('data-training-id') || null;

    // モーダルを開く。既存のIDを渡すと編集モード（必要ならそこから追加処理を行う）
    openModal(trainingDate, trainingId);
}

function removeSet(btn) {
    const tbody = btn.closest('.set-tbody');
    btn.closest('tr').remove();
    Array.from(tbody.rows).forEach((row, index) => {
        const numSpan = row.querySelector('.set-num');
        if (numSpan) numSpan.innerText = index + 1;
    });
}

function reindexSets() {
    const rows = document.querySelectorAll('#setList tr');
    rows.forEach((row, index) => {
        row.cells[0].innerText = index + 1;
        const weightInput = row.querySelector('input[name*=".weight"]');
        const repsInput = row.querySelector('input[name*=".reps"]');
        if(weightInput) weightInput.name = `details[${index}].weight`;
        if(repsInput) repsInput.name = `details[${index}].reps`;
    });
}

// 4. モーダル関連
let setIndex = 0; // 宣言はここ1回だけ！
let isSaving = false;

function openModal(date, id = null) {
    const modal = document.getElementById('trainingModal');
    if (!modal) return;

    const partSelect = document.getElementById('modalPart');
    if (partSelect) partSelect.value = "";

    const menuSelect = document.getElementById('modalMenu');
    if (menuSelect) {
        menuSelect.innerHTML = '<option value="">部位を先に選択してください</option>';
        menuSelect.disabled = true;
    }

    modal.classList.remove('hidden');
    document.getElementById('modalDate').value = date;
    document.getElementById('displayDate').innerText = "実施日: " + date;

    const setList = document.getElementById('setList');
    setList.innerHTML = '';
    setIndex = 0;

    if (id) {
        // 編集モード: 画面上のカードから値を読み取りプリフィルする
        document.getElementById('modalTitle').innerText = "トレーニング編集";
        document.getElementById('trainingId').value = id;

        const card = document.querySelector(`.training-card[data-training-id="${id}"]`);
        if (card) {
            const partCode = card.getAttribute('data-part-code') || '';
            const menu = card.getAttribute('data-menu') || '';

            // 部位をセット
            if (partSelect) {
                partSelect.value = partCode;
            }

            // 種目は部位を元にitemsを取得してから選択する
            updateItems(partCode).then(() => {
                if (menuSelect) {
                    // 同名のoptionがあれば選択、なければ新たに追加して選択
                    let found = Array.from(menuSelect.options).some(opt => {
                        if (opt.value === menu) {
                            menuSelect.value = menu;
                            return true;
                        }
                        return false;
                    });
                    if (!found && menu) {
                        const opt = document.createElement('option');
                        opt.value = menu;
                        opt.textContent = menu;
                        menuSelect.appendChild(opt);
                        menuSelect.value = menu;
                    }
                }
            }).catch(() => {
                // 取得失敗してもユーザーは手動で選べるようにする
            });

            // セット一覧をカードからコピー
            const rows = card.querySelectorAll('.set-row');
            rows.forEach((r) => {
                const weight = r.querySelector('.weight')?.value || '';
                const reps = r.querySelector('.reps')?.value || '';

                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${setList.children.length + 1}</td>
                    <td><input type="number" class="weight" step="0.1" placeholder="0" value="${weight}"></td>
                    <td><input type="number" class="reps" placeholder="0" value="${reps}"></td>
                    <td>
                        <button type="button"
                                data-action="removeRow"
                                style="color:red;border:none;background:none;cursor:pointer;">
                            ✕
                        </button>
                    </td>
                `;
                setList.appendChild(tr);
            });
        }
    } else {
        document.getElementById('modalTitle').innerText = "トレーニング登録";
        document.getElementById('trainingId').value = "";

        setTimeout(() => {
            if (document.getElementById('setList')) {
                addSetRow();
            }
        }, 10);
    }
}

function closeModal() {
    const modal = document.getElementById('trainingModal');

    if (modal) {
        modal.classList.add('hidden');
    }
}

function saveRegister() {
    const modal = document.getElementById('trainingModal');
    if (modal && !modal.classList.contains('hidden')) {
        addTrainingCardLocally();
        return;
    }
    saveRegisterBulk();
}

function saveRegisterBulk() {
  const trainingBlocks = document.querySelectorAll('.training-block');
  
  trainingBlocks.forEach((block, blockIndex) => {
    if (!selectedTrainings[blockIndex]) return;
    
    const setRows = block.querySelectorAll('.set-row');
    selectedTrainings[blockIndex].details = [];
    
    setRows.forEach((row, rowIndex) => {
      const weightInput = row.querySelector('.weight');
      const repsInput = row.querySelector('.reps');
      const completedButton = row.querySelector('.btn-check-set');
      
      const weightVal = weightInput ? parseFloat(weightInput.value) : 0;
      const repsVal = repsInput ? parseInt(repsInput.value, 10) : 0;
      const isCompleted = completedButton ? completedButton.classList.contains('active') : false;
      
      selectedTrainings[blockIndex].details.push({
        setNumber: rowIndex + 1,
        weight: isNaN(weightVal) ? 0 : weightVal,
        reps: isNaN(repsVal) ? 0 : repsVal,
        isCompleted: isCompleted
      });
    });
  });

  if (selectedTrainings.length === 0) {
    alert('トレーニング種目を選択してください');
    return;
  }

  const selectedDate = document.getElementById('selectedDate')?.value;
  const data = {
    date: selectedDate,
    trainings: selectedTrainings,
  };

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

  const headers = {
    'Content-Type': 'application/json',
  };

  if (csrfToken && csrfHeader) {
    headers[csrfHeader] = csrfToken;
  }

  fetch('/api/training/register-bulk', {
    method: 'POST',
    headers: headers,
    body: JSON.stringify(data),
  })
    .then((response) => {
      if (response.ok) {
        showSuccessPopup();
        closeModal();
      } else {
        alert('保存に失敗しました');
      }
    })
    .catch((error) => {
      console.error('Error:', error);
      alert('保存中にエラーが発生しました');
    });
}

function updateItems(partCode) {
    const menuSelect = document.getElementById('modalMenu');
    if (!partCode) {
        if (menuSelect) {
            menuSelect.innerHTML = '<option value="">部位を先に選択してください</option>';
            menuSelect.disabled = true;
        }
        return Promise.resolve([]);
    }
    return fetch('/api/training-items?partCode=' + partCode)
        .then(response => response.json())
        .then(items => {
            if (!menuSelect) return items;
            menuSelect.innerHTML = '<option value="">種目を選択してください</option>';
            items.forEach(item => {
                const option = document.createElement('option');
                option.value = item.itemName;
                option.textContent = item.itemName;
                menuSelect.appendChild(option);
            });
            menuSelect.disabled = false;
            return items;
        })
        .catch(err => {
            console.error('updateItems error', err);
            if (menuSelect) {
                menuSelect.innerHTML = '<option value="">部位を先に選択してください</option>';
                menuSelect.disabled = true;
            }
            return [];
        });
}

function addSetRow() {
    const tbody = document.getElementById('setList');

    if (!tbody) {
        console.error("setList が見つかりません");
        return;
    }

    const row = document.createElement('tr');

    row.innerHTML = `
        <td>${tbody.children.length + 1}</td>
        <td><input type="number" class="weight" step="0.1" placeholder="0"></td>
        <td><input type="number" class="reps" placeholder="0"></td>
        <td>
            <button type="button"
                    data-action="removeRow"
                    style="color:red;border:none;background:none;cursor:pointer;">
                ✕
            </button>
        </td>
    `;

    tbody.appendChild(row);
}

function removeRow(btn) {
    const row = btn.closest('tr');
    if (!row) return;
    row.remove();
    reindexSets();
}

window.onclick = function(event) {
    const modal = document.getElementById('trainingModal');
    if (event.target == modal) closeModal();
}

async function finishTraining() {
    if (!confirm("トレーニングを終了して保存しますか？")) return;

    const durationStr = formatDuration(totalSeconds);
    const trainingCards = document.querySelectorAll('.training-card');
    const allData = [];
    const validationErrors = [];

    trainingCards.forEach(card => {
        const rawId = card.getAttribute('data-training-id');
        const id = rawId && rawId !== 'null' ? Number(rawId) : null;
        const memo = card.querySelector('.memo-area')?.value || "";
        const details = [];
        const menu = card.dataset.menu || '不明';

        if (!id) {
            validationErrors.push(`「${menu}」はまだ保存されていません。一度モーダルから保存してください。`);
        }

        card.querySelectorAll('.set-row').forEach((row, index) => {
            const weightVal = row.querySelector('.weight')?.value ?? '';
            const repsVal = row.querySelector('.reps')?.value ?? '';
            const checkBtn = row.querySelector('.btn-check');
            const isCompleted = checkBtn?.classList.contains('completed') ?? false;

            if (weightVal === '' || repsVal === '') {
                validationErrors.push(`「${menu}」のセット ${index + 1} に未入力の項目があります。`);
            }

            details.push({
                weight: weightVal !== '' ? parseFloat(weightVal) : null,
                reps: repsVal !== '' ? parseInt(repsVal, 10) : null,
                setNumber: index + 1,
                completed: isCompleted
            });
        });

        const trainingDate = card.dataset.trainingDate;
        const partCode = card.dataset.partCode;
        const userId = parseInt(card.dataset.userId, 10);

        if (!trainingDate || !partCode || !menu || isNaN(userId)) {
            validationErrors.push(`「${menu}」のデータが不完全です。`);
        }

        allData.push({
            id: id,
            userId: userId,
            trainingDate: trainingDate,
            partCode: partCode,
            menu: menu,
            memo: memo,
            duration: durationStr,
            details: details
        });
    });

    if (validationErrors.length > 0) {
        alert("保存できません。\n\n" + validationErrors.join('\n'));
        return;
    }

    try {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;

        const response = await fetch('/api/training/finish', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token
            },
            body: JSON.stringify(allData)
        });

        if (response.ok) {
            window.location.href = '/menu';
        } else {
            const errorMsg = await response.text().catch(() => '不明なエラー');
            alert("保存に失敗しました。\n" + errorMsg);
        }
    } catch (error) {
        alert("通信エラーが発生しました。\n" + error.message);
    }
}

function addTraining() {
    // 実技画面用：モーダルを開く（日付はサマリーの見出し等から取得するか、Javaから渡す）
    const dateText = document.querySelector('h3 span')?.innerText || "";
    openModal(dateText);
}

function getModalTrainingData() {
    const trainingId = document.getElementById('trainingId')?.value || null;
    const menu = document.getElementById('modalMenu')?.value || '';
    const partCode = document.getElementById('modalPart')?.value || '';
    const partName = document.getElementById('modalPart')?.selectedOptions[0]?.text || '';
    const trainingDate = document.getElementById('modalDate')?.value || '';
    const currentUserId = parseInt(document.getElementById('currentUserId')?.value, 10) || null;

    const details = Array.from(document.querySelectorAll('#setList tr')).map((row, index) => {
        const weightInput = row.querySelector('input.weight');
        const repsInput = row.querySelector('input.reps');
        return {
            setNumber: index + 1,
            weight: weightInput && weightInput.value !== '' ? parseFloat(weightInput.value) : 0,
            reps: repsInput && repsInput.value !== '' ? parseInt(repsInput.value, 10) : 0,
            completed: false
        };
    });

    const existingCard = trainingId ? document.querySelector(`.training-card[data-training-id="${trainingId}"]`) : null;
    const memo = existingCard?.querySelector('.memo-area')?.value || '';

    return {
        id: trainingId,
        userId: currentUserId,
        menu: menu,
        partCode: partCode,
        partName: partName,
        trainingDate: trainingDate,
        memo: memo,
        details: details
    };
}

function updateExistingTrainingCard(trainingData) {
    const card = document.querySelector(`.training-card[data-training-id="${trainingData.id}"]`);
    if (!card) return false;

    card.dataset.partCode = trainingData.partCode;
    card.dataset.menu = trainingData.menu;
    card.dataset.trainingDate = trainingData.trainingDate;
    if (trainingData.userId !== null) {
        card.dataset.userId = trainingData.userId;
    }

    const title = card.querySelector('.exercise-title h3');
    if (title) title.textContent = trainingData.menu;

    const partLabel = card.querySelector('.exercise-title span');
    if (partLabel) partLabel.textContent = trainingData.partName;

    const tbody = card.querySelector('.set-tbody');
    if (tbody) {
        tbody.innerHTML = trainingData.details.map((detail, index) => {
            return `
            <tr class="set-row">
                <td><span class="set-num">${index + 1}</span></td>
                <td><input type="number" class="weight" value="${detail.weight != null ? detail.weight : ''}" step="0.5" placeholder="0"> kg</td>
                <td><input type="number" class="reps" value="${detail.reps != null ? detail.reps : ''}" placeholder="0"> 回</td>
                <td><button class="btn-check" data-action="handleCheck">✓</button></td>
                <td><button type="button" data-action="removeSet" class="btn btn-danger btn-sm">✕</button></td>
            </tr>`;
        }).join('');
    }

    const memoArea = card.querySelector('.memo-area');
    if (memoArea) {
        memoArea.value = trainingData.memo || memoArea.value || '';
    }

    return true;
}

async function addTrainingCardLocally() {
    if (isSaving) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const trainingData = getModalTrainingData();

    if (!trainingData.partCode || !trainingData.menu || !trainingData.trainingDate) {
        alert('部位・種目・実施日を入力してください');
        return;
    }

    const isNew = !trainingData.id;
    const endpoint = isNew ? '/api/training/save' : `/api/training/update/${trainingData.id}`;
    const saveBtn = document.querySelector('#trainingModal [data-action="saveRegister"]');

    isSaving = true;
    if (saveBtn) saveBtn.disabled = true;

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(header && token ? { [header]: token } : {})
            },
            body: JSON.stringify(trainingData)
        });

        if (!response.ok) {
            const errMsg = await response.text().catch(() => '');
            throw new Error(errMsg || '保存に失敗しました');
        }

        if (isNew) {
            // /api/training/save は Long を直接返す
            const savedId = await response.json();
            trainingData.id = typeof savedId === 'number' ? savedId : null;
        }

        const updated = trainingData.id ? updateExistingTrainingCard(trainingData) : false;
        if (!updated) {
            renderNewCard(trainingData.menu, trainingData.partName, trainingData.partCode, trainingData.trainingDate, trainingData.userId, trainingData.details, trainingData.id);
        }

        closeModal();
    } catch (error) {
        alert("エラーが発生しました: " + error.message);
    } finally {
        isSaving = false;
        if (saveBtn) saveBtn.disabled = false;
    }
}
// 画面描画部分を切り出し
function renderNewCard(menu, partName, partCode, trainingDate, userId, details, id) {
    let rowsHtml = details.map(d => `
		<tr class="set-row">
        <td><span class="set-num">${d.setNumber}</span></td>
        <td><input type="number" class="weight" value="${d.weight != null ? d.weight : ''}" step="0.5" placeholder="0"> kg</td>
        <td><input type="number" class="reps" value="${d.reps != null ? d.reps : ''}" placeholder="0"> 回</td>
        <td><button class="btn-check ${(d.isCompleted || d.completed) ? 'completed' : ''}" data-action="handleCheck">✓</button></td>
        <td><button type="button" data-action="removeSet" style="color:#f44336; border:none; background:none; cursor:pointer;">✕</button></td>
    </tr>
    `).join('');

    const newCard = `
        <div class="training-card" data-training-id="${id}"
             data-part-code="${partCode}"
             data-menu="${menu}"
             data-training-date="${trainingDate}"
             data-user-id="${userId}">
            <div class="exercise-title">
                <h3 style="margin:0;">${menu}</h3>
                <span style="font-size:0.8em; color:#888;">${partName}</span>
            </div>
            <table class="set-list"><tbody class="set-tbody">${rowsHtml}</tbody></table>
                <div style="text-align:right; margin-top: 10px;">
                <button class="btn-add" data-action="addSet">＋ セット追加</button>
            </div>
            <textarea class="memo-area" placeholder="メモ" style="width: 100%; margin-top: 10px;"></textarea>
        </div>
    `;

    const container = document.querySelector('.training-container');
    const addBtnWrapper = container.querySelector('[data-action="openModal"]')?.parentElement;
    if (addBtnWrapper) {
        addBtnWrapper.insertAdjacentHTML('beforebegin', newCard);
    } else {
        container.insertAdjacentHTML('beforeend', newCard);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initializeTimer();

    const partSelect = document.getElementById('modalPart');
    if (partSelect) {
        partSelect.addEventListener('change', function() {
            updateItems(this.value);
        });
    }

    const modalForm = document.querySelector('#trainingModal form');
    if (modalForm) {
        modalForm.addEventListener('submit', function(e) {
            // URLに /start/training が含まれている場合だけ、
            // 普通の送信を止めて JSでの保存（API送信）に切り替える
            if (window.location.pathname.includes('/start/training')) {
                e.preventDefault(); // これで画面が切り替わるのを防ぐ
                addTrainingCardLocally(); // 前に作ったAPI保存関数
            }
        });
    }

    const logoutForm = document.querySelector('form[action*="logout"]');
    if (logoutForm) {
        logoutForm.addEventListener('submit', function(e) {
            e.preventDefault();
            handleLogout();
        });
    }
});

function handleLogout() {
    const hasUnsavedChanges = hasTrainingInProgress();
    if (hasUnsavedChanges) {
        if (confirm('トレーニング中です。ログアウトすると進行中のデータが失われます。よろしいですか？')) {
            rollbackTrainingData();
            performLogout();
        }
    } else {
        if (confirm('ログアウトしてもよろしいですか？')) {
            performLogout();
        }
    }
}

function hasTrainingInProgress() {
    const timer = document.getElementById('totalTimer');
    const timerValue = timer ? timer.textContent : '00:00:00';
    return timerValue !== '00:00:00' || document.querySelectorAll('.set-row input').length > 0;
}

function rollbackTrainingData() {
    console.log('Rolling back training data...');
    const modal = document.getElementById('trainingModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function performLogout() {
    if (window.opener || window.history.length <= 1) {
        window.close();
        setTimeout(() => {
            window.location.href = '/login';
        }, 100);
    } else {
        window.location.href = '/menu';
    }
}

// 公開 API 用にタイマー機能をグローバルに
window.getTimerState = getTimerState;

async function deleteTrainingCard(btn) {
    const card = btn.closest('.training-card');
    const trainingId = card.getAttribute('data-training-id');

    if (!trainingId) {
        card.remove(); // まだDBにない（追加前）なら画面消去だけでOK
        return;
    }

    if (confirm("この種目を削除しますか？")) {
        // 合言葉の準備
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;

        // APIを叩いてDBから消す
        const response = await fetch(`/api/training/delete/${trainingId}`, {
            method: 'POST',
            headers: { [header]: token }
        });

        if (response.ok) {
            card.remove(); // DB削除に成功したら画面からも消す
        }
    }
}

let trainingStartTime = null;

// 初期表示
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date();
    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 6);

    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');

    // グラフUIがない画面（menu/start_training等）では何もしない
    if (!startDateInput || !endDateInput) {
        return;
    }

    // input要素に初期値をセット (yyyy-mm-dd形式)
    startDateInput.value = lastWeek.toISOString().split('T')[0];
    endDateInput.value = today.toISOString().split('T')[0];

    searchByPeriod(); // 初回実行
});

async function searchByPeriod() {
    const userId = document.getElementById('userIdForGraph')?.value;
    const startDate = document.getElementById('startDate')?.value;
    const endDate = document.getElementById('endDate')?.value;

    if (!userId || !startDate || !endDate) return;

    try {
        const response = await fetch(`/admin/api/training-volume/${userId}?startDate=${startDate}&endDate=${endDate}`);
        const data = await response.json();
        renderChart(data); // ここで renderChart を呼び出す
    } catch (e) {
        console.error("検索に失敗しました", e);
    }
}

function renderChart(data) {
    const canvas = document.getElementById('volumeChart');
    if (!canvas) {
        console.error("ID: volumeChart のキャンバスが見つかりません");
        return;
    }
    const ctx = canvas.getContext('2d');

    // 古いグラフが残っていれば破棄する
    if (myChart) {
        myChart.destroy();
    }

    // 新しいグラフを作成
    myChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [
                { label: '胸', data: data.chest, borderColor: '#ff6384', tension: 0.3, fill: false },
                { label: '背中', data: data.back, borderColor: '#36a2eb', tension: 0.3, fill: false },
                { label: '腕', data: data.arms, borderColor: '#ffce56', tension: 0.3, fill: false },
                { label: '肩', data: data.shoulders, borderColor: '#9966ff', tension: 0.3, fill: false },
                { label: '脚', data: data.legs, borderColor: '#4bc0c0', tension: 0.3, fill: false }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    ticks: {
                        autoSkip: true,
                        maxTicksLimit: 10,
                        maxRotation: 45,
                        minRotation: 45
                    }
                },
                y: {
                    beginAtZero: true,
                    title: { display: true, text: '合計重量 (kg)' }
                }
            }
        }
    });
}

// 非同期のPromiseエラーを抑制するラッパー
function safeInvoke(fnName, el) {
    const fn = window[fnName];
    if (typeof fn === 'function') {
        try {
            // Promiseを返さないように、返り値を受け取らない呼び出しにする
            fn(el);
        } catch (e) {
            console.error("Invoke error:", e);
        }
    }
}

// Expose key functions to global scope for inline onclick handlers
// (Ensures functions are available even if script execution environment differs)
window.toggleMainTimer = toggleMainTimer;
window.setIntervalTime = setIntervalTime;
window.changeInterval = changeInterval;
window.handleCheck = handleCheck;
window.startInterval = startInterval;
window.addSet = addSet;
window.removeSet = removeSet;
window.addSetRow = addSetRow;
window.finishTraining = finishTraining;
window.deleteTrainingCard = deleteTrainingCard;
window.addTraining = addTraining;
window.openModal = openModal;
window.closeModal = closeModal;
window.saveRegister = saveRegister;
window.addTrainingCardLocally = addTrainingCardLocally;
window.reindexSets = reindexSets;
window.stopInterval = stopInterval;
