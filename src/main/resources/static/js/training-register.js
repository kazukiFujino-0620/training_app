let selectedTrainings = []; // 選択されたトレーニングデータを保持
let partsData = []; // 部位データを保持
let itemsData = {}; // 部位ごとの種目データを保持

document.addEventListener('DOMContentLoaded', () => {
    // OKボタンの初期状態を設定
    const okButton = document.querySelector('.btn-selection-ok');
    if (okButton) {
        okButton.disabled = true;
    }
});

// DOMの変更を監視してチェックボックスが追加されたらOKボタン状態を更新
function observeTabContents() {
    const tabContents = document.getElementById('tabContents');
    if (!tabContents) return;
    
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                // チェックボックスが追加されたらOKボタン状態を更新
                setTimeout(() => {
                    updateOkButtonState();
                    // 全てのチェックボックスにイベントリスナーを再設定
                    const checkboxes = document.querySelectorAll('.exercise-item input[type="checkbox"]');
                    checkboxes.forEach(checkbox => {
                        checkbox.removeEventListener('change', handleCheckboxChange);
                        checkbox.addEventListener('change', handleCheckboxChange);
                    });
                }, 100);
            }
        });
    });
    
    observer.observe(tabContents, {
        childList: true,
        subtree: true
    });
}

// チェックボックス変更イベントハンドラ
function handleCheckboxChange(event) {
    console.log('Checkbox changed:', event.target.checked);
    const checkbox = event.target;
    const itemDiv = checkbox.closest('.exercise-item');
    
    if (checkbox.checked) {
        itemDiv.classList.add('selected');
    } else {
        itemDiv.classList.remove('selected');
    }
    
    updateOkButtonState();
}

// ===== 種目選択画面を開く =====
function openExerciseModal() {
    document.getElementById('exerciseSelectionContainer').style.display = 'flex';
    loadTrainingData();
}

// ===== 種目選択画面を閉じる =====
function cancelSelection() {
    document.getElementById('exerciseSelectionContainer').style.display = 'none';
}

// ===== トレーニングデータを読み込む =====
function loadTrainingData() {
    // DOM監視を開始
    observeTabContents();
    
    // 部位データと種目データを一括で取得
    Promise.all([
        fetch('/api/training-parts').then(response => response.json()),
        fetch('/api/training-items-grouped').then(response => response.json())
    ])
    .then(([parts, groupedItems]) => {
        partsData = parts;
        itemsData = groupedItems;
        renderTabs();
    })
    .catch(error => {
        console.error('Error loading training data:', error);
        const tabHeaders = document.getElementById('tabHeaders');
        tabHeaders.innerHTML = '<p style="color: red; padding: 20px;">トレーニングデータの読み込みに失敗しました</p>';
    });
}

// ===== タブを描画する =====
function renderTabs() {
    const tabHeaders = document.getElementById('tabHeaders');
    const tabContents = document.getElementById('tabContents');
    
    // タブヘッダーをクリア
    tabHeaders.innerHTML = '';
    tabContents.innerHTML = '';
    
    // 各部位のタブを作成
    partsData.forEach((part, index) => {
        // タブヘッダー
        const tabHeader = document.createElement('button');
        tabHeader.className = 'tab-header';
        if (index === 0) tabHeader.classList.add('active');
        tabHeader.textContent = part.partName;
        tabHeader.onclick = () => switchTab(part.partCode);
        tabHeaders.appendChild(tabHeader);
        
        // タブコンテンツ
        const tabContent = document.createElement('div');
        tabContent.className = 'tab-content';
        tabContent.id = `tab-${part.partCode}`;
        if (index === 0) tabContent.classList.add('active');
        
        // 種目リストを描画
        const items = itemsData[part.partCode] || [];
        items.forEach(item => {
            const itemDiv = document.createElement('div');
            itemDiv.className = 'exercise-item';
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `item-${item.id}`;
            checkbox.value = JSON.stringify(item);
            
            const label = document.createElement('label');
            label.htmlFor = `item-${item.id}`;
            label.innerHTML = `
                <span class="item-name">${item.itemName}</span>
                <span class="item-info">部位: ${part.partName}</span>
            `;
            
            // イベントリスナーを直接設定
            checkbox.addEventListener('change', handleCheckboxChange);
            
            itemDiv.appendChild(checkbox);
            itemDiv.appendChild(label);
            tabContent.appendChild(itemDiv);
        });
        
        tabContents.appendChild(tabContent);
    });
    
    // 全てのタブコンテンツがDOMに追加された後にOKボタンの状態を更新
    setTimeout(() => {
        updateOkButtonState();
    }, 0);
}

// ===== タブを切り替える =====
function switchTab(partCode) {
    // 全てのタブヘッダーとコンテンツのアクティブ状態を解除
    document.querySelectorAll('.tab-header').forEach(header => {
        header.classList.remove('active');
    });
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    
    // 選択されたタブをアクティブにする
    const activeHeader = Array.from(document.querySelectorAll('.tab-header'))
        .find(header => header.textContent === partsData.find(p => p.partCode === partCode)?.partName);
    const activeContent = document.getElementById(`tab-${partCode}`);
    
    if (activeHeader) activeHeader.classList.add('active');
    if (activeContent) activeContent.classList.add('active');
}

// ===== 種目の選択状態を切り替える =====
function toggleItemSelection(checkbox) {
    console.log('toggleItemSelection called, checkbox.checked:', checkbox.checked);
    const itemData = JSON.parse(checkbox.value);
    const itemDiv = checkbox.closest('.exercise-item');
    
    if (checkbox.checked) {
        itemDiv.classList.add('selected');
        console.log('Item selected:', itemData.itemName);
    } else {
        itemDiv.classList.remove('selected');
        console.log('Item deselected:', itemData.itemName);
    }
    
    updateOkButtonState();
}

// ===== OKボタンの状態を更新 =====
function updateOkButtonState() {
    const checkedBoxes = document.querySelectorAll('.exercise-item input[type="checkbox"]:checked');
    const okButton = document.querySelector('.btn-selection-ok');
    
    console.log('updateOkButtonState called:');
    console.log('Checked boxes count:', checkedBoxes.length);
    console.log('OK button element:', okButton);
    
    if (checkedBoxes.length > 0) {
        okButton.disabled = false;
        console.log('OK button enabled');
    } else {
        okButton.disabled = true;
        console.log('OK button disabled');
    }
}

// ===== 種目選択を確定する =====
function confirmSelection() {
    const checkedBoxes = document.querySelectorAll('.exercise-item input[type="checkbox"]:checked');
    
    if (checkedBoxes.length === 0) {
        alert('少なくとも1つの種目を選択してください');
        return;
    }
    
    // 選択された種目をトレーニングデータに変換
    selectedTrainings = [];
    checkedBoxes.forEach(checkbox => {
        const itemData = JSON.parse(checkbox.value);
        const partData = partsData.find(p => p.partCode === itemData.partCode);
        
        const training = {
            id: null,
            menu: itemData.itemName,
            partCode: itemData.partCode,
            partName: partData?.partName || '',
            details: [{
                weight: 0,
                reps: 0,
                setNumber: 1,
                isCompleted: false
            }],
            memo: ''
        };
        
        selectedTrainings.push(training);
    });
    
    // 選択画面を閉じる
    document.getElementById('exerciseSelectionContainer').style.display = 'none';
    
    // 画面状態を更新
    document.getElementById('initialState').style.display = 'none';
    document.getElementById('registerContent').style.display = 'block';
    document.getElementById('actionButtons').style.display = 'flex';
    
    // トレーニングブロックを描画
    renderTrainingBlocks();
}

// ===== トレーニングブロック表示 =====
function renderTrainingBlocks() {
    const container = document.getElementById('trainingBlocksContainer');
    container.innerHTML = '';
    
    selectedTrainings.forEach((training, trainingIndex) => {
        const blockDiv = document.createElement('div');
        blockDiv.className = 'training-block';
        blockDiv.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
                <div class="exercise-header" style="margin-bottom: 0;">
                    ${training.menu}
                </div>
                <button type="button" data-action="removeTraining" data-index="${trainingIndex}" style="color:#f44336; border:none; background:none; cursor:pointer; font-size:1.2em; padding: 0;">
                    ✕
                </button>
            </div>
            
            <div class="set-container" id="setContainer-${trainingIndex}">
                <!-- セット行がここに追加される -->
            </div>
            
            <div class="volume-display" id="volumeDisplay-${trainingIndex}">
                総ボリューム: <strong>0 kg</strong>
            </div>
            
            <div class="set-actions">
                <button type="button" class="btn-set-action" data-action="removeSet" data-index="${trainingIndex}">
                    − セット削除
                </button>
                <button type="button" class="btn-set-action" data-action="addSet" data-index="${trainingIndex}" style="background: #e3f2fd; border-color: #2196F3;">
                    + セット追加
                </button>
            </div>
        `;
        
        container.appendChild(blockDiv);
        renderSetRows(trainingIndex);
    });
}

function renderSetRows(trainingIndex) {
    const training = selectedTrainings[trainingIndex];
    const setContainer = document.getElementById(`setContainer-${trainingIndex}`);
    setContainer.innerHTML = '';
    
    training.details.forEach((detail, setIndex) => {
        const setDiv = document.createElement('div');
        setDiv.className = 'set-row';
        setDiv.id = `set-${trainingIndex}-${setIndex}`;
        
        const isCompleted = detail.isCompleted ? 'active' : '';
        
        setDiv.innerHTML = `
            <span class="set-num">${setIndex + 1}</span>
            <div class="set-input-group">
                  <input type="number" class="weight" value="${detail.weight}" step="0.5" 
                      data-change="updateTrainingDetail" data-training-index="${trainingIndex}" data-set-index="${setIndex}" data-field="weight">
                <label>kg</label>
                  <input type="number" class="reps" value="${detail.reps}" 
                      data-change="updateTrainingDetail" data-training-index="${trainingIndex}" data-set-index="${setIndex}" data-field="reps">
                <label>回</label>
            </div>
                <button type="button" class="btn-check-set ${isCompleted}" data-action="toggleSetCompletion" data-training-index="${trainingIndex}" data-set-index="${setIndex}">✓</button>
                <button type="button" class="btn-remove-set" data-action="removeIndividualSet" data-training-index="${trainingIndex}" data-set-index="${setIndex}">✕</button>
        `;
        
        setContainer.appendChild(setDiv);
    });
    
    updateVolumeDisplay(trainingIndex);

    // Attach change handlers for inputs added via innerHTML
    setContainer.querySelectorAll('input[data-change]').forEach(inp => {
        inp.addEventListener('change', (e) => {
            const tIdx = parseInt(inp.dataset.trainingIndex, 10);
            const sIdx = parseInt(inp.dataset.setIndex, 10);
            const field = inp.dataset.field;
            updateTrainingDetail(tIdx, sIdx, field, inp.value);
        });
    });
}

function updateTrainingDetail(trainingIndex, setIndex, field, value) {
    const training = selectedTrainings[trainingIndex];
    if (field === 'weight') {
        training.details[setIndex].weight = parseFloat(value) || 0;
    } else if (field === 'reps') {
        training.details[setIndex].reps = parseInt(value) || 0;
    }
    updateVolumeDisplay(trainingIndex);
}

function toggleSetCompletion(trainingIndex, setIndex) {
    const training = selectedTrainings[trainingIndex];
    training.details[setIndex].isCompleted = !training.details[setIndex].isCompleted;
    renderSetRows(trainingIndex);
}

function updateVolumeDisplay(trainingIndex) {
    const training = selectedTrainings[trainingIndex];
    let totalVolume = 0;
    
    training.details.forEach(detail => {
        totalVolume += (detail.weight * detail.reps);
    });
    
    const display = document.getElementById(`volumeDisplay-${trainingIndex}`);
    display.innerHTML = `総ボリューム: <strong>${totalVolume.toFixed(1)} kg</strong>`;
}

function addSet(trainingIndex) {
    const training = selectedTrainings[trainingIndex];
    let lastWeight = 0, lastReps = 0;
    
    if (training.details.length > 0) {
        const lastSet = training.details[training.details.length - 1];
        lastWeight = lastSet.weight;
        lastReps = lastSet.reps;
    }
    
    training.details.push({
        weight: lastWeight,
        reps: lastReps,
        setNumber: training.details.length + 1,
        isCompleted: false
    });
    
    renderSetRows(trainingIndex);
}

function removeSet(trainingIndex) {
    const training = selectedTrainings[trainingIndex];
    if (training.details.length > 1) {
        training.details.pop();
        renderSetRows(trainingIndex);
    } else {
        alert('最後の1セットは削除できません');
    }
}

function removeIndividualSet(trainingIndex, setIndex) {
    const training = selectedTrainings[trainingIndex];
    if (training.details.length > 1) {
        training.details.splice(setIndex, 1);
        // セット番号を更新
        training.details.forEach((detail, idx) => {
            detail.setNumber = idx + 1;
        });
        renderSetRows(trainingIndex);
    } else {
        alert('最後の1セットは削除できません');
    }
}

function removeTraining(trainingIndex) {
    selectedTrainings.splice(trainingIndex, 1);
    
    if (selectedTrainings.length === 0) {
        document.getElementById('initialState').style.display = 'block';
        document.getElementById('registerContent').style.display = 'none';
        document.getElementById('actionButtons').style.display = 'none';
    } else {
        renderTrainingBlocks();
    }
}

// ===== 保存処理 =====
function saveRegister() {
    if (selectedTrainings.length === 0) {
        alert('トレーニング種目を選択してください');
        return;
    }
    
    const selectedDate = document.getElementById('selectedDate').value;
    const data = {
        date: selectedDate,
        trainings: selectedTrainings
    };
    
    // CSRFトークンを取得
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    
    const headers = {
        'Content-Type': 'application/json'
    };
    
    // CSRFトークンが存在する場合はヘッダーに追加
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    
    fetch('/api/training/register-bulk', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(data)
    })
    .then(response => {
        if (response.ok) {
            // 成功ポップアップを表示
            showSuccessPopup();
        } else {
            alert('保存に失敗しました');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        alert('保存中にエラーが発生しました');
    });
}

// ===== 成功ポップアップ表示 =====
function showSuccessPopup() {
    // ポップアップ要素を作成
    const popup = document.createElement('div');
    popup.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 2000;
    `;
    
    const popupContent = document.createElement('div');
    popupContent.style.cssText = `
        background: white;
        border-radius: 12px;
        padding: 30px;
        text-align: center;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
        max-width: 400px;
    `;
    
    popupContent.innerHTML = `
        <div style="font-size: 3em; color: #4CAF50; margin-bottom: 20px;">✓</div>
        <h3 style="color: #333; margin-bottom: 15px;">トレーニング登録完了</h3>
        <p style="color: #666; margin-bottom: 25px;">トレーニングデータが正常に保存されました。</p>
        <button data-action="closeSuccessPopup" style="
            background: #4CAF50;
            color: white;
            border: none;
            padding: 12px 30px;
            border-radius: 6px;
            cursor: pointer;
            font-weight: bold;
            font-size: 1em;
        ">OK</button>
    `;
    
    popup.appendChild(popupContent);
    document.body.appendChild(popup);
}

document.addEventListener('DOMContentLoaded', function() {
    const logoutForm = document.querySelector('form[action*="logout"]');
    if (logoutForm) {
        logoutForm.addEventListener('submit', function(e) {
            e.preventDefault();
            handleLogout();
        });
    }
});

function handleLogout() {
    const hasUnsavedChanges = hasTrainingDataInProgress();
    if (hasUnsavedChanges) {
        if (confirm('トレーニング登録中です。ログアウトすると入力中のデータが失われます。よろしいですか？')) {
            rollbackTrainingData();
            performLogout();
        }
    } else {
        if (confirm('ログアウトしてもよろしいですか？')) {
            performLogout();
        }
    }
}

function hasTrainingDataInProgress() {
    const registerContent = document.getElementById('registerContent');
    const isRegistering = registerContent && registerContent.style.display === 'block';
    const hasTrainingBlocks = document.querySelectorAll('.training-block').length > 0;
    return isRegistering && hasTrainingBlocks;
}

function rollbackTrainingData() {
    console.log('Rolling back training data...');
    const initialState = document.getElementById('initialState');
    const registerContent = document.getElementById('registerContent');
    const actionButtons = document.getElementById('actionButtons');
    const trainingBlocks = document.getElementById('trainingBlocksContainer');
    if (initialState) initialState.style.display = 'block';
    if (registerContent) registerContent.style.display = 'none';
    if (actionButtons) actionButtons.style.display = 'none';
    if (trainingBlocks) trainingBlocks.innerHTML = '';
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

// ===== 成功ポップアップを閉じてメニューへ遷移 =====
function closeSuccessPopup() {
    // ポップアップを削除
    const popup = document.querySelector('div[style*="position: fixed"]');
    if (popup) {
        popup.remove();
    }
    
    // メニュー画面へ遷移
    const selectedDate = document.getElementById('selectedDate').value;
    window.location.href = `/menu?date=${encodeURIComponent(selectedDate)}`;
}

// ===== キャンセル処理 =====
function cancelRegister() {
    const selectedDate = document.getElementById('selectedDate').value;
    window.location.href = `/menu?date=${encodeURIComponent(selectedDate)}`;
}
