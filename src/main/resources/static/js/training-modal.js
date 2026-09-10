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

// ita7-1 2-2: 過去分編集画面（/detail、廃止済み）の閲覧レイアウトをmenu.htmlへ
// モーダルとして埋め込むための開閉制御（表示内容はMenuController.menu()がサーバー側で描画済み）。
function openTrainingDetailModal() {
    document.getElementById('trainingDetailModal')?.classList.remove('hidden');
}

function closeTrainingDetailModal() {
    document.getElementById('trainingDetailModal')?.classList.add('hidden');
}

window.openTrainingDetailModal = openTrainingDetailModal;
window.closeTrainingDetailModal = closeTrainingDetailModal;

// ── ログアウト確認 ────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
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

let trainingStartTime = null;

// 初期表示（管理者向けトレーニングボリュームグラフ）
document.addEventListener('DOMContentLoaded', () => {
    const today = new Date();
    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 6);

    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');

    // グラフUIがない画面（menu等）では何もしない
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
