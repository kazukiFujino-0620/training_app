let myChart;
let totalSeconds = 0;
let timerInterval = null;
let isTimerRunning = false;

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
                    <input type="number" id="editWeight-${setIndex}" value="${detail.weight}" step="0.5" data-index="${setIndex}" data-change="updateEditSet" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
                    <label style="color: #666; font-size: 0.9em;">kg</label>
                    <input type="number" id="editReps-${setIndex}" value="${detail.reps}" data-index="${setIndex}" data-change="updateEditSet" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
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
    const weight = parseFloat(document.getElementById(`editWeight-${setIndex}`).value) || 0;
    const reps = parseInt(document.getElementById(`editReps-${setIndex}`).value) || 0;
    
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
        lastWeight = lastSet.weight;
        lastReps = lastSet.reps;
    }
    
    currentEditingTraining.details.push({
        weight: lastWeight,
        reps: lastReps,
        setNumber: currentEditingTraining.details.length + 1,
        isCompleted: false
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

function changeInterval(delta) {
    const timeSpan = document.getElementById('intervalTime');
    
    // 現在の「表示」ではなく、プログラム内の「中身（remaining）」を増減させる
    // タイマーが動いていない時のために、表示から数字を読み取る処理も入れます
    if (remaining === 0) {
        remaining = parseInt(timeSpan.innerText) || 0;
    }

    remaining += delta;
    
    if (remaining < 0) remaining = 0;
    if (remaining > 1200) remaining = 1200; 

    // 画面の表示を更新
    timeSpan.innerText = remaining;
}

function handleCheck(btn) {
    btn.classList.toggle('completed');
    if (btn.classList.contains('completed')) {
        const currentTime = document.getElementById('intervalTime')?.innerText || '0';
        const seconds = parseInt(currentTime, 10) || 0;
        document.getElementById('intervalBanner').style.display = 'block';
        startInterval(seconds);
    } else {
        if (intervalCountDown) clearInterval(intervalCountDown);
        document.getElementById('intervalBanner').style.display = 'none';
    }
	}

function startInterval(seconds) {
    if (intervalCountDown) clearInterval(intervalCountDown);
    
    const banner = document.getElementById('intervalBanner');
    const timeSpan = document.getElementById('intervalTime');
    if (!banner || !timeSpan) return;
    
    banner.style.display = 'block';
    remaining = seconds; 
    timeSpan.innerText = remaining;

    intervalCountDown = setInterval(() => {
        remaining--;
        timeSpan.innerText = remaining;

        // 10秒前の「ピッ」
        if (remaining === 10) {
            new Audio('https://actions.google.com/sounds/v1/alarms/beep_short.ogg').play().catch(()=>{});
        }

        if (remaining <= 0) {
            clearInterval(intervalCountDown);
            
            // 終了時のバイブ
            if (navigator.vibrate) {
                navigator.vibrate([200, 100, 200, 100, 200]);
            }
            
            // ピピピッ、ピピピッ という感じの電子音に変更
            new Audio('https://actions.google.com/sounds/v1/alarms/digital_clock_beep.ogg').play().catch(()=>{});
            
            banner.style.display = 'none';
            alert("インターバル終了！次のセットを開始してください。");
        }
    }, 1000);
}

// 3. セット操作（実技・モーダル共通）
function addSet(btn) {
    const tbody = btn.closest('.training-card').querySelector('.set-tbody');
    const lastRow = tbody.lastElementChild;
    let weight = 0, reps = 0;
    if (lastRow) {
        weight = lastRow.querySelector('.weight').value;
        reps = lastRow.querySelector('.reps').value;
    }
    const nextNum = tbody.children.length + 1;
    const newRow = `
        <tr class="set-row">
            <td><span class="set-num">${nextNum}</span></td>
            <td><input type="number" class="weight" value="${weight}" step="0.5"> kg</td>
            <td><input type="number" class="reps" value="${reps}"> 回</td>
            <td><button class="btn-check" data-action="handleCheck">✓</button></td>
            <td><button type="button" data-action="removeSet" style="color:#f44336; border:none; background:none; cursor:pointer;">✕</button></td>
        </tr>
    `;
    tbody.insertAdjacentHTML('beforeend', newRow);
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
		
    modal.style.display = 'block';
    document.getElementById('modalDate').value = date;
    document.getElementById('displayDate').innerText = "実施日: " + date;
    
    const setList = document.getElementById('setList');
    setList.innerHTML = '';
    setIndex = 0;
    
    if (id) {
        document.getElementById('modalTitle').innerText = "トレーニング編集";
        document.getElementById('trainingId').value = id;
    } else {
        document.getElementById('modalTitle').innerText = "トレーニング登録";
        document.getElementById('trainingId').value = "";
        addSetRow();
    }
}

function closeModal() {
    const modal = document.getElementById('trainingModal');
    if (modal) modal.style.display = 'none';
}

function updateItems(partCode) {
    const menuSelect = document.getElementById('modalMenu');
    if (!partCode) {
        menuSelect.innerHTML = '<option value="">部位を先に選択してください</option>';
        menuSelect.disabled = true;
        return;
    }
    fetch('/api/training-items?partCode=' + partCode)
        .then(response => response.json())
        .then(items => {
            menuSelect.innerHTML = '<option value="">種目を選択してください</option>';
            items.forEach(item => {
                const option = document.createElement('option');
                option.value = item.itemName;
                option.textContent = item.itemName;
                menuSelect.appendChild(option);
            });
            menuSelect.disabled = false;
        });
}

function addSetRow() {
    const tbody = document.getElementById('setList');
    const row = `
        <tr>
            <td>${setIndex + 1}</td>
            <td><input type="number" name="details[${setIndex}].weight" step="0.1" required></td>
            <td><input type="number" name="details[${setIndex}].reps" required></td>
            <td><button type="button" data-action="removeRow" style="color:red; border:none; background:none; cursor:pointer;">✕</button></td>
        </tr>
    `;
    tbody.insertAdjacentHTML('beforeend', row);
    setIndex++;
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

	const durationStr = formatDuration(totalSeconds); // 例: "00:45:10"


	const trainingCards = document.querySelectorAll('.training-card');
    const allData = [];
    let hasError = false;

    trainingCards.forEach(card => {
        const id = card.getAttribute('data-training-id');
        const memo = card.querySelector('.memo-area')?.value || "";
        const details = [];

        // 各セットの行をループ
        card.querySelectorAll('.set-row').forEach((row, index) => {
            const weight = row.querySelector('.weight').value;
            const reps = row.querySelector('.reps').value;
			const checkBtn = row.querySelector('.btn-check');
			
			const isCompleted = checkBtn.classList.contains('completed');

            // 簡単なバリデーション：未入力があれば警告
            if (!weight || !reps) {
                hasError = true;
            }

            details.push({
                weight: weight,
                reps: reps,
                setNumber: index + 1,
				completed: isCompleted
            });
        });

		const trainingDate = card.dataset.trainingDate;
		const partCode = card.dataset.partCode;
		const menu = card.dataset.menu;
		const userId = parseInt(card.dataset.userId, 10);

		if (!trainingDate || !partCode || !menu || isNaN(userId)) {
		    hasError = true;
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

    if (hasError) {
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
            body: JSON.stringify(allData) // まとめたデータをJSONにして送る
        });

        if (response.ok) {
            // カレンダー画面（menu）に戻る
            window.location.href = '/menu'; 
        }
    } catch (error) {
        // エラー処理
    }
}

function addTraining() {
    // 実技画面用：モーダルを開く（日付はサマリーの見出し等から取得するか、Javaから渡す）
    const dateText = document.querySelector('h3 span')?.innerText || "";
    openModal(dateText);
}

async function addTrainingCardLocally() {
	const token = document.querySelector('meta[name="_csrf"]').content;
　   const header = document.querySelector('meta[name="_csrf_header"]').content;
    const menu = document.getElementById('modalMenu').value;
    const partCode = document.getElementById('modalPart').value;
    const partName = document.getElementById('modalPart').selectedOptions[0].text;
    const trainingDate = document.getElementById('modalDate').value;
    const currentUserId = parseInt(document.getElementById('currentUserId')?.value, 10) || null;

    // 1. フォームからデータを集める
    const details = [];
    document.querySelectorAll('#setList tr').forEach((row, index) => {
        details.push({
            setNumber: index + 1,
            weight: row.querySelector('input[name*=".weight"]').value,
            reps: row.querySelector('input[name*=".reps"]').value
        });
    });

    const trainingData = {
        userId: currentUserId,
        menu: menu,
        partCode: partCode,
        trainingDate: trainingDate,
        details: details
    };


    try {
        // 2. サーバーへ非同期送信 (Fetch API)
        const response = await fetch('/api/training/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [header]: token },
            body: JSON.stringify(trainingData)
        });

        if (!response.ok) throw new Error('保存に失敗しました');
        
        const savedId = await response.json(); // サーバーから返ってきたID

        // 3. 成功したら画面にカードを追加
        // ※ IDを data-training-id にセットしておくことで、後で「削除」や「更新」が可能になります
        renderNewCard(menu, partName, partCode, trainingDate, currentUserId, details, savedId);
        
        closeModal();
    } catch (error) {
        alert("エラーが発生しました: " + error.message);
    }
}

// 画面描画部分を切り出し
function renderNewCard(menu, partName, partCode, trainingDate, userId, details, id) {
    let rowsHtml = details.map(d => `
		<tr class="set-row">
        <td><span class="set-num">${d.setNumber}</span></td>
        <td><input type="number" class="weight" value="${d.weight}" step="0.5"> kg</td>
        <td><input type="number" class="reps" value="${d.reps}"> 回</td>
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
    const lastDiv = container.querySelector('div[style*="text-align: right"]');
    lastDiv.insertAdjacentHTML('beforebegin', newCard);
}

document.addEventListener('DOMContentLoaded', () => {
    initializeTimer();
    
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
window.addTrainingCardLocally = addTrainingCardLocally;
window.reindexSets = reindexSets;