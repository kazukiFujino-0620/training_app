let selectedTrainings = []; // 選択されたトレーニングデータを保持
let partsData = []; // 部位データを保持
let itemsData = {}; // 部位ごとの種目データを保持

document.addEventListener("DOMContentLoaded", () => {
  // 1. OKボタンの初期状態を設定
  const okButton = document.querySelector(".btn-selection-ok");
  if (okButton) {
    okButton.disabled = true;
  }

  // 2. URLからメニュー画面のデータを抜き取って復元する
  const urlParams = new URLSearchParams(window.location.search);
  const editDataParam = urlParams.get("editData");

  if (editDataParam) {
    try {
      const decodedJson = decodeURIComponent(escape(atob(decodeURIComponent(editDataParam))));
      const editData = JSON.parse(decodedJson);

      console.log("復元する編集データ:", editData);

      selectedTrainings = [{
        id: editData.id,
        menu: editData.menu,
        partCode: editData.partCode,
        details: editData.details.map(d => ({
          setNumber: d.setNumber,
          weight: (d.weight !== null && !isNaN(d.weight)) ? parseFloat(d.weight) : 0,
          reps: (d.reps !== null && !isNaN(d.reps)) ? parseInt(d.reps, 10) : 0,
          isCompleted: d.isCompleted || false
        }))
      }];

      const initialState = document.getElementById("initialState");
      const registerContent = document.getElementById("registerContent");
      const actionButtons = document.getElementById("actionButtons");
      
      if (initialState) initialState.style.display = "none";
      if (registerContent) registerContent.style.display = "block";
      if (actionButtons) actionButtons.style.display = "flex"; 

      setTimeout(() => {
        renderTrainingBlocks();
      }, 350);

    } catch (e) {
      console.error("URLデータの復元に失敗しました:", e);
    }
  }

  // ⭕ 独自の属性「data-vol-action」だけを監視するように変更（干渉を100%回避）
  const container = document.getElementById("trainingBlocksContainer");
  if (container) {
    container.addEventListener("click", (e) => {
      const target = e.target;
      if (target.matches('button[data-vol-action="removeTraining"]')) {
        removeTraining(parseInt(target.dataset.index, 10));
      } else if (target.matches('button[data-vol-action="addSet"]')) {
        e.preventDefault();
        e.stopPropagation();
        addSet(parseInt(target.dataset.index, 10));
      } else if (target.matches('button[data-vol-action="removeSet"]')) {
        e.preventDefault();
        e.stopPropagation();
        removeSet(parseInt(target.dataset.index, 10));
      } else if (target.matches('button[data-vol-action="toggleSetCompletion"]')) {
        toggleSetCompletion(parseInt(target.dataset.trainingIndex, 10), parseInt(target.dataset.setIndex, 10));
      } else if (target.matches('button[data-vol-action="removeIndividualSet"]')) {
        toggleSetCompletion(parseInt(target.dataset.trainingIndex, 10), parseInt(target.dataset.setIndex, 10));
        removeIndividualSet(parseInt(target.dataset.trainingIndex, 10), parseInt(target.dataset.setIndex, 10));
      }
    });
  }
});

// DOMの変更を監視してチェックボックスが追加されたらOKボタン状態を更新
function observeTabContents() {
  const tabContents = document.getElementById("tabContents");
  if (!tabContents) return;

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === "childList" && mutation.addedNodes.length > 0) {
        setTimeout(() => {
          updateOkButtonState();
          const checkboxes = document.querySelectorAll('.exercise-item input[type="checkbox"]');
          checkboxes.forEach((checkbox) => {
            checkbox.removeEventListener("change", handleCheckboxChange);
            checkbox.addEventListener("change", handleCheckboxChange);
          });
        }, 100);
      }
    });
  });

  observer.observe(tabContents, {
    childList: true,
    subtree: true,
  });
}

function handleCheckboxChange(event) {
  const checkbox = event.target;
  const itemDiv = checkbox.closest(".exercise-item");

  if (checkbox.checked) {
    itemDiv.classList.add("selected");
  } else {
    itemDiv.classList.remove("selected");
  }

  updateOkButtonState();
}

function openExerciseModal() {
  document.getElementById("exerciseSelectionContainer").style.display = "flex";
  loadTrainingData();
}

function cancelSelection() {
  document.getElementById("exerciseSelectionContainer").style.display = "none";
}

function loadTrainingData() {
  observeTabContents();

  Promise.all([
    fetch("/api/training-parts").then((response) => response.json()),
    fetch("/api/training-items-grouped").then((response) => response.json()),
  ])
    .then(([parts, groupedItems]) => {
      partsData = parts;
      itemsData = groupedItems;
      renderTabs();
    })
    .catch((error) => {
      console.error("Error loading training data:", error);
      const tabHeaders = document.getElementById("tabHeaders");
      tabHeaders.innerHTML = '<p style="color: red; padding: 20px;">トレーニングデータの読み込みに失敗しました</p>';
    });
}

function renderTabs() {
  const tabHeaders = document.getElementById("tabHeaders");
  const tabContents = document.getElementById("tabContents");

  tabHeaders.innerHTML = "";
  tabContents.innerHTML = "";

  partsData.forEach((part, index) => {
    const tabHeader = document.createElement("button");
    tabHeader.className = "tab-header";
    if (index === 0) tabHeader.classList.add("active");
    tabHeader.textContent = part.partName;
    tabHeader.onclick = () => switchTab(part.partCode);
    tabHeaders.appendChild(tabHeader);

    const tabContent = document.createElement("div");
    tabContent.className = "tab-content";
    tabContent.id = `tab-${part.partCode}`;
    if (index === 0) tabContent.classList.add("active");

    const items = itemsData[part.partCode] || [];
    items.forEach((item) => {
      const itemDiv = document.createElement("div");
      itemDiv.className = "exercise-item";

      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.id = `item-${item.id}`;
      checkbox.value = JSON.stringify(item);

      const label = document.createElement("label");
      label.htmlFor = `item-${item.id}`;
      label.innerHTML = `
                <span class="item-name">${item.itemName}</span>
                <span class="item-info">部位: ${part.partName}</span>
            `;

      checkbox.addEventListener("change", handleCheckboxChange);

      itemDiv.appendChild(checkbox);
      itemDiv.appendChild(label);
      tabContent.appendChild(itemDiv);
    });

    tabContents.appendChild(tabContent);
  });

  setTimeout(() => {
    updateOkButtonState();
  }, 0);
}

function switchTab(partCode) {
  document.querySelectorAll(".tab-header").forEach((header) => {
    header.classList.remove("active");
  });
  document.querySelectorAll(".tab-content").forEach((content) => {
    content.classList.remove("active");
  });

  const activeHeader = Array.from(document.querySelectorAll(".tab-header")).find(
    (header) => header.textContent === partsData.find((p) => p.partCode === partCode)?.partName,
  );
  const activeContent = document.getElementById(`tab-${partCode}`);

  if (activeHeader) activeHeader.classList.add("active");
  if (activeContent) activeContent.classList.add("active");
}

function updateOkButtonState() {
  const checkedBoxes = document.querySelectorAll('.exercise-item input[type="checkbox"]:checked');
  const okButton = document.querySelector(".btn-selection-ok");

  if (checkedBoxes.length > 0) {
    okButton.disabled = false;
  } else {
    okButton.disabled = true;
  }
}

function confirmSelection() {
  const checkedBoxes = document.querySelectorAll('.exercise-item input[type="checkbox"]:checked');

  if (checkedBoxes.length === 0) {
    alert("少なくとも1つの種目を選択してください");
    return;
  }

  selectedTrainings = [];
  checkedBoxes.forEach((checkbox) => {
    const itemData = JSON.parse(checkbox.value);
    const partData = partsData.find((p) => p.partCode === itemData.partCode);

    const training = {
      id: null,
      menu: itemData.itemName,
      partCode: itemData.partCode,
      partName: partData?.partName || "",
      details: [
        {
          weight: 0,
          reps: 0,
          setNumber: 1,
          isCompleted: false,
        },
      ],
      memo: "",
    };

    selectedTrainings.push(training);
  });

  document.getElementById("exerciseSelectionContainer").style.display = "none";
  document.getElementById("initialState").style.display = "none";
  document.getElementById("registerContent").style.display = "block";
  document.getElementById("actionButtons").style.display = "flex";

  renderTrainingBlocks();
}

// ===== トレーニングブロック表示 =====
function renderTrainingBlocks() {
  const container = document.getElementById("trainingBlocksContainer");
  container.innerHTML = "";

  selectedTrainings.forEach((training, trainingIndex) => {
    const blockDiv = document.createElement("div");
    blockDiv.className = "training-block";
    
    // ⭕ class名を元の「btn-set-action」に戻し（CSSデザインを復元）、属性を「data-vol-action」に統一
    blockDiv.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
                <div class="exercise-header" style="margin-bottom: 0;">
                    ${training.menu}
                </div>
                <button type="button" data-vol-action="removeTraining" data-index="${trainingIndex}" style="color:#f44336; border:none; background:none; cursor:pointer; font-size:1.2em; padding: 0;">
                    ✕
                </button>
            </div>

            <div class="prev-record-container" id="prev-container-${trainingIndex}"></div>

            <div class="set-container" id="setContainer-${trainingIndex}">
                </div>

            <div class="volume-display" id="volumeDisplay-${trainingIndex}">
                総ボリューム: <strong>0 kg</strong>
            </div>

            <div class="set-actions">
                <button type="button" class="btn-set-action" data-vol-action="removeSet" data-index="${trainingIndex}">
                    − セット削除
                </button>
                <button type="button" class="btn-set-action" data-vol-action="addSet" data-index="${trainingIndex}" style="background: #e3f2fd; border-color: #2196F3;">
                    + セット追加
                </button>
            </div>
        `;

    container.appendChild(blockDiv);
    renderSetRows(trainingIndex);

    // 前回記録パネルをロード
    if (typeof createPrevRecordPanel === 'function') {
      var prevContainer = document.getElementById('prev-container-' + trainingIndex);
      if (prevContainer && training.menu) {
        createPrevRecordPanel(prevContainer).load(training.menu);
      }
    }
  });
}

function renderSetRows(trainingIndex) {
  const training = selectedTrainings[trainingIndex];
  const setContainer = document.getElementById(`setContainer-${trainingIndex}`);
  setContainer.innerHTML = "";

  training.details.forEach((detail, setIndex) => {
    const setDiv = document.createElement("div");
    setDiv.className = "set-row";
    setDiv.id = `set-${trainingIndex}-${setIndex}`;

    const isCompleted = detail.isCompleted ? "active" : "";

    // ⭕ ここも data-vol-action に変更
    setDiv.innerHTML = `
            <span class="set-num">${setIndex + 1}</span>
            <div class="set-input-group">
                  <input type="number" class="weight" value="${detail.weight || ""}" step="0.5" placeholder="0"
                      data-training-index="${trainingIndex}" data-set-index="${setIndex}" data-field="weight">
                <label>kg</label>
                  <input type="number" class="reps" value="${detail.reps || ""}" placeholder="0"
                      data-training-index="${trainingIndex}" data-set-index="${setIndex}" data-field="reps">
                <label>回</label>
            </div>
                <button type="button" class="btn-check-set ${isCompleted}" data-vol-action="toggleSetCompletion" data-training-index="${trainingIndex}" data-set-index="${setIndex}">✓</button>
                <button type="button" class="btn-remove-set" data-vol-action="removeIndividualSet" data-training-index="${trainingIndex}" data-set-index="${setIndex}">✕</button>
        `;

    setContainer.appendChild(setDiv);
  });

  updateVolumeDisplay(trainingIndex);

  setContainer.querySelectorAll("input").forEach((inp) => {
    inp.addEventListener("input", (e) => {
      const tIdx = parseInt(inp.dataset.trainingIndex, 10);
      const sIdx = parseInt(inp.dataset.setIndex, 10);
      const field = inp.dataset.field;
      updateTrainingDetail(tIdx, sIdx, field, inp.value);
    });
  });
}

function updateTrainingDetail(trainingIndex, setIndex, field, value) {
  const training = selectedTrainings[trainingIndex];
  if (!training || !training.details[setIndex]) return;

  if (field === "weight") {
    const parsedWeight = parseFloat(value);
    training.details[setIndex].weight = (value === '' || isNaN(parsedWeight)) ? 0 : parsedWeight;
  } else if (field === "reps") {
    const parsedReps = parseInt(value, 10);
    training.details[setIndex].reps = (value === '' || isNaN(parsedReps)) ? 0 : parsedReps;
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
  if (!training) return;

  let totalVolume = 0;
  training.details.forEach((detail) => {
    let w = parseFloat(detail.weight);
    let r = parseInt(detail.reps, 10);
    if (isNaN(w)) w = 0;
    if (isNaN(r)) r = 0;
    totalVolume += w * r;
  });

  const display = document.getElementById(`volumeDisplay-${trainingIndex}`);
  if (display) {
    display.innerHTML = `総ボリューム: <strong>${totalVolume.toFixed(1)} kg</strong>`;
  }
}

function addSet(trainingIndex) {
  const training = selectedTrainings[trainingIndex];
  let lastWeight = 0, lastReps = 0;

  if (training.details.length > 0) {
    const lastSet = training.details[training.details.length - 1];
    lastWeight = (lastSet.weight && !isNaN(lastSet.weight)) ? parseFloat(lastSet.weight) : 0;
    lastReps = (lastSet.reps && !isNaN(lastSet.reps)) ? parseInt(lastSet.reps, 10) : 0;
  }

  training.details.push({
    weight: lastWeight,
    reps: lastReps,
    setNumber: training.details.length + 1,
    isCompleted: false,
  });

  renderSetRows(trainingIndex);
}

function removeSet(trainingIndex) {
  const training = selectedTrainings[trainingIndex];
  if (training.details.length > 1) {
    training.details.pop();
    renderSetRows(trainingIndex);
  } else {
    alert("最後の1セットは削除できません");
  }
}

function removeIndividualSet(trainingIndex, setIndex) {
  const training = selectedTrainings[trainingIndex];
  if (training.details.length > 1) {
    training.details.splice(setIndex, 1);
    training.details.forEach((detail, idx) => {
      detail.setNumber = idx + 1;
    });
    renderSetRows(trainingIndex);
  } else {
    alert("最後の1セットは削除できません");
  }
}

function removeTraining(trainingIndex) {
  selectedTrainings.splice(trainingIndex, 1);

  if (selectedTrainings.length === 0) {
    document.getElementById("initialState").style.display = "block";
    document.getElementById("registerContent").style.display = "none";
    document.getElementById("actionButtons").style.display = "none";
  } else {
    renderTrainingBlocks();
  }
}

// ===== 保存処理 =====
function saveRegister() {
  const trainingBlocks = document.querySelectorAll(".training-block");
    
  trainingBlocks.forEach((block, blockIndex) => {
    if (!selectedTrainings[blockIndex]) return;
    
    const setRows = block.querySelectorAll(".set-row");
    selectedTrainings[blockIndex].details = [];
    
    setRows.forEach((row, rowIndex) => {
      const weightInput = row.querySelector(".weight");
      const repsInput = row.querySelector(".reps");
      const completedButton = row.querySelector(".btn-check-set");
      
      const weightVal = weightInput ? parseFloat(weightInput.value) : 0;
      const repsVal = repsInput ? parseInt(repsInput.value, 10) : 0;
      const isCompleted = completedButton ? completedButton.classList.contains("active") : false;
      
      selectedTrainings[blockIndex].details.push({
        setNumber: rowIndex + 1,
        weight: isNaN(weightVal) ? 0 : weightVal,
        reps: isNaN(repsVal) ? 0 : repsVal,
        isCompleted: isCompleted
      });
    });
  });

  if (selectedTrainings.length === 0) {
    alert("トレーニング種目を選択してください");
    return;
  }

  const selectedDate = document.getElementById("selectedDate").value;
  const data = {
    date: selectedDate,
    trainings: selectedTrainings,
  };

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

  const headers = {
    "Content-Type": "application/json",
  };

  if (csrfToken && csrfHeader) {
    headers[csrfHeader] = csrfToken;
  }

  fetch("/api/training/register-bulk", {
    method: "POST",
    headers: headers,
    body: JSON.stringify(data),
  })
    .then((response) => {
      if (response.ok) {
        showSuccessPopup();
      } else {
        alert("保存に失敗しました");
      }
    })
    .catch((error) => {
      console.error("Error:", error);
      alert("保存中にエラーが発生しました");
    });
}

function showSuccessPopup() {
  const popup = document.createElement("div");
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

  const popupContent = document.createElement("div");
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

  const closeBtn = popupContent.querySelector('button[data-action="closeSuccessPopup"]');
  if (closeBtn) {
    closeBtn.addEventListener('click', closeSuccessPopup);
  }
}

document.addEventListener("DOMContentLoaded", function () {
  const logoutForm = document.querySelector('form[action*="logout"]');
  if (logoutForm) {
    logoutForm.addEventListener("submit", function (e) {
      e.preventDefault();
      handleLogout();
    });
  }
});

function handleLogout() {
  const hasUnsavedChanges = hasTrainingDataInProgress();
  if (hasUnsavedChanges) {
    if (confirm("トレーニング登録中です。ログアウトすると入力中のデータが失われます。よろしいですか？")) {
      rollbackTrainingData();
      performLogout();
    }
  } else {
    if (confirm("ログアウトしてもよろしいですか？")) {
      performLogout();
    }
  }
}

function hasTrainingDataInProgress() {
  const registerContent = document.getElementById("registerContent");
  const isRegistering = registerContent && registerContent.style.display === "block";
  const hasTrainingBlocks = document.querySelectorAll(".training-block").length > 0;
  return isRegistering && hasTrainingBlocks;
}

function rollbackTrainingData() {
  const initialState = document.getElementById("initialState");
  const registerContent = document.getElementById("registerContent");
  const actionButtons = document.getElementById("actionButtons");
  const trainingBlocks = document.getElementById("trainingBlocksContainer");
  if (initialState) initialState.style.display = "block";
  if (registerContent) registerContent.style.display = "none";
  if (actionButtons) actionButtons.style.display = "none";
  if (trainingBlocks) trainingBlocks.innerHTML = "";
}

function performLogout() {
  if (window.opener || window.history.length <= 1) {
    window.close();
    setTimeout(() => {
      window.location.href = "/login";
    }, 100);
  } else {
    window.location.href = "/menu";
  }
}

function closeSuccessPopup() {
  const popup = document.querySelector('div[style*="position: fixed"]');
  if (popup) {
    popup.remove();
  }
  const selectedDate = document.getElementById("selectedDate").value;
  window.location.href = `/menu?date=${encodeURIComponent(selectedDate)}`;
}

function cancelRegister() {
  const selectedDate = document.getElementById("selectedDate").value;
  window.location.href = `/menu?date=${encodeURIComponent(selectedDate)}`;
}