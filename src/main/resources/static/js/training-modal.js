let myChart;

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
                listElement.innerHTML = '<li>この日の記録はありません</li>';
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
                li.innerText = `${menuName}: ${weight}kg x ${reps}回 (${sets}セット)`;
                listElement.appendChild(li);
            });
        })
        .catch(error => {
            console.error('Error:', error);
            listElement.innerHTML = '<li style="color: red;">データの取得に失敗しました</li>';
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
                    <input type="number" id="editWeight-${setIndex}" value="${detail.weight}" step="0.5" onchange="updateEditSet(${setIndex})" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
                    <label style="color: #666; font-size: 0.9em;">kg</label>
                    <input type="number" id="editReps-${setIndex}" value="${detail.reps}" onchange="updateEditSet(${setIndex})" style="width: 80px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; text-align: center;">
                    <label style="color: #666; font-size: 0.9em;">回</label>
                </div>
                <button type="button" onclick="removeEditSet(${setIndex})" style="background: none; border: none; color: #f44336; font-size: 1.2em; cursor: pointer; padding: 0;">✕</button>
            </div>
        `;
    });
    
    // セット追加ボタン
    html += `
        <button type="button" onclick="addEditSet()" style="width: 100%; padding: 12px; margin-top: 15px; background: #e3f2fd; border: 1px solid #2196F3; color: #2196F3; border-radius: 6px; cursor: pointer; font-weight: bold;">
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
        alert('最後の1セットは削除できません');
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
            alert('トレーニングを更新しました');
            closeEditModal();
            // ページをリロードして反映
            location.reload();
        } else {
            alert('更新に失敗しました');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        alert('更新中にエラーが発生しました');
    });
}

// 編集キャンセル
function closeEditModal() {
    document.getElementById('editTrainingModal').style.display = 'none';
    currentEditingTraining = null;
    originalTrainingData = null;
}

// 2. タイマー関連
let totalSeconds = 0;
let mainTimerInterval;
let isRunning = false;
let intervalCountDown;

document.addEventListener('DOMContentLoaded', () => {
    const initialTimeInput = document.getElementById('initialDuration');
    if (initialTimeInput && initialTimeInput.value && initialTimeInput.value !== "null") {
        totalSeconds = timeToSeconds(initialTimeInput.value);
        updateTimerDisplay(); // 画面表示を "00:00:00" から保存時間に更新
    }
});

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
    if (!isRunning) {
        isRunning = true;
        btn.innerText = "一時停止";
        btn.style.background = "#ff9800";
        mainTimerInterval = setInterval(() => {
            totalSeconds++;
            updateTimerDisplay();
        }, 1000);
    } else {
        isRunning = false;
        btn.innerText = "再開";
        btn.style.background = "#4CAF50";
        clearInterval(mainTimerInterval);
    }
}

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

function updateTimerDisplay() {
    const h = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
    const m = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0');
    const s = String(totalSeconds % 60).padStart(2, '0');
    const display = document.getElementById('totalTimer');
    if (display) display.innerText = `${h}:${m}:${s}`;
}

function handleCheck(btn) {
    btn.classList.toggle('completed');
	if (btn.classList.contains('completed')) {
	        const currentTime = document.getElementById('intervalTime').innerText;
	        seconds = parseInt(currentTime);
	        
	        document.getElementById('intervalBanner').style.display = 'block';
	        
	        startInterval(seconds); 
	    } else {
			if (timerId) clearInterval(timerId);
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
            <td><button class="btn-check" onclick="handleCheck(this)">✓</button></td>
            <td><button type="button" onclick="removeSet(this)" style="color:#f44336; border:none; background:none; cursor:pointer;">✕</button></td>
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
            <td><button type="button" onclick="this.closest('tr').remove(); reindexSets();" style="color:red; border:none; background:none; cursor:pointer;">✕</button></td>
        </tr>
    `;
    tbody.insertAdjacentHTML('beforeend', row);
    setIndex++;
}

window.onclick = function(event) {
    const modal = document.getElementById('trainingModal');
    if (event.target == modal) closeModal();
}

async function finishTraining() {
    if (!confirm("トレーニングを終了して保存しますか？")) return;

	const formatDuration = (seconds) => {
	        const h = Math.floor(seconds / 3600).toString().padStart(2, '0');
	        const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, '0');
	        const s = (seconds % 60).toString().padStart(2, '0');
	        return `${h}:${m}:${s}`;
	    };
	
	const durationStr = formatDuration(totalSeconds); // 例: "00:45:10"
    console.log("計測時間:", durationStr);
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

		allData.push({ 
	            id: id, 
	            memo: memo, 
	            duration: durationStr,
	            details: details 
		        });
    });

    if (hasError) {
        alert("重量または回数が入力されていないセットがあります。");
        return;
    }

    try {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;

		console.log("送信データ:", allData); // これで中身がコンソールで見れます
	    alert("今からJavaに送ります！");
		
        const response = await fetch('/api/training/finish', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token
            },
            body: JSON.stringify(allData) // まとめたデータをJSONにして送る
        });

        if (response.ok) {
            alert("保存が完了しました！");
            // カレンダー画面（menu）に戻る
            window.location.href = '/menu'; 
        } else {
            throw new Error("保存に失敗しました");
        }
    } catch (error) {
        alert(error.message);
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
        menu: menu,
        partCode: partCode,
        trainingDate: trainingDate,
        details: details
    };

	console.log("送信データ:", JSON.stringify(trainingData));
    try {
        // 2. サーバーへ非同期送信 (Fetch API)
        const response = await fetch('/api/training/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' ,[header]: token},
            body: JSON.stringify(trainingData)
        });

        if (!response.ok) throw new Error('保存に失敗しました');
        
        const savedId = await response.json(); // サーバーから返ってきたID

        // 3. 成功したら画面にカードを追加
        // ※ IDを data-training-id にセットしておくことで、後で「削除」や「更新」が可能になります
        renderNewCard(menu, partName, details, savedId);
        
        closeModal();
    } catch (error) {
        alert("エラーが発生しました: " + error.message);
    }
}

// 画面描画部分を切り出し
function renderNewCard(menu, partName, details, id) {
    let rowsHtml = details.map(d => `
		<tr class="set-row">
	        <td><span class="set-num">${d.setNumber}</span></td>
	        <td><input type="number" class="weight" value="${d.weight}" step="0.5"> kg</td>
	        <td><input type="number" class="reps" value="${d.reps}"> 回</td>
	        <td><button class="btn-check ${d.completed ? 'completed' : ''}" onclick="handleCheck(this)">✓</button></td>
	        <td><button type="button" onclick="removeSet(this)" style="color:#f44336; border:none; background:none; cursor:pointer;">✕</button></td>
	    </tr>
    `).join('');

    const newCard = `
        <div class="training-card" data-training-id="${id}">
            <div class="exercise-title">
                <h3 style="margin:0;">${menu}</h3>
                <span style="font-size:0.8em; color:#888;">${partName}</span>
            </div>
            <table class="set-list"><tbody class="set-tbody">${rowsHtml}</tbody></table>
            <div style="text-align:right; margin-top: 10px;">
                <button class="btn-add" onclick="addSet(this)">＋ セット追加</button>
            </div>
            <textarea class="memo-area" placeholder="メモ" style="width: 100%; margin-top: 10px;"></textarea>
        </div>
    `;

    const container = document.querySelector('.training-container');
    const lastDiv = container.querySelector('div[style*="text-align: right"]');
    lastDiv.insertAdjacentHTML('beforebegin', newCard);
}

document.addEventListener('DOMContentLoaded', () => {
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
});

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