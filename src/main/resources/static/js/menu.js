console.log('menu.js loaded');

// Initialize calendar click handlers using event delegation
function initializeCalendar() {
  console.log('initializeCalendar called');
  const calendarCells = document.querySelectorAll('.calendar-cell');
  console.log('Found calendar cells:', calendarCells.length);
  
  if (calendarCells.length === 0) {
    console.log('No calendar cells found');
    return;
  }

  calendarCells.forEach(cell => {
    cell.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      const dateString = this.getAttribute('data-date');
      console.log('Calendar cell clicked, date:', dateString);
      if (dateString) {
        // Redirect to menu with selected date parameter
        window.location.href = "/menu?date=" + dateString;
      }
    });
  });
}

// Initialize edit modal buttons
function initializeEditButtons() {
  const editButtons = document.querySelectorAll('[data-action="edit"]');
  editButtons.forEach(button => {
    button.addEventListener('click', function() {
      const id = this.getAttribute('data-id');
      const menu = this.getAttribute('data-menu');
      const partCode = this.getAttribute('data-part-code');
      const trainingDate = this.getAttribute('data-training-date');
      
      // ボタンの属性からカンマ区切りの文字列を取得
      const weightsStr = this.getAttribute('data-weights') || "";
      const repsStr = this.getAttribute('data-reps') || "";

      // カンマで分割して配列にする
      const weightsArray = weightsStr ? weightsStr.split(',') : [];
      const repsArray = repsStr ? repsStr.split(',') : [];

      // details の構造に組み立て直す
      const detailsArray = weightsArray.map((weight, index) => {
        return {
          setNumber: index + 1,
          weight: parseFloat(weight) || 0,
          reps: parseInt(repsArray[index], 10) || 0,
          isCompleted: false
        };
      });

      // 1. 送信用にデータをパッケージング
      const editData = {
        id: id,
        menu: menu,
        partCode: partCode,
        details: detailsArray
      };

      console.log("【チェック】送信直前のパッケージデータ:", editData);

      // 2. URLパラメータで送れるように暗号化（Base64エンコード）
      const encodedData = encodeURIComponent(btoa(unescape(encodeURIComponent(JSON.stringify(editData)))));
      
      // 3. 正しい登録画面へジャンプ
      window.location.href = `/training/register?date=${trainingDate}&editData=${encodedData}`;
    });
  });
}

// Initialize register buttons
function initializeRegisterButtons() {
  const registerButtons = document.querySelectorAll('[data-action="register"]');
  registerButtons.forEach(button => {
    button.addEventListener('click', function() {
      const date = this.getAttribute('data-date');
      if (typeof openRegisterPage === 'function') {
        openRegisterPage(date);
      }
    });
  });
}

// Initialize delete form confirmation
function initializeDeleteForms() {
  const deleteForms = document.querySelectorAll('form[data-action="delete"]');
  deleteForms.forEach(form => {
    form.addEventListener('submit', function(e) {
      if (!confirm('本当に削除しますか？')) {
        e.preventDefault();
        return false;
      }
    });
  });
}

// Initialize modal buttons
function initializeModalButtons() {
  const cancelButton = document.querySelector('[data-action="close-modal"]');
  const saveButton = document.querySelector('[data-action="save-modal"]');

  if (cancelButton && typeof closeEditModal === 'function') {
    cancelButton.addEventListener('click', closeEditModal);
  }

  if (saveButton && typeof saveEditModal === 'function') {
    saveButton.addEventListener('click', saveEditModal);
  }
}

// Initialize all event handlers
document.addEventListener('DOMContentLoaded', function() {
  console.log('DOMContentLoaded - menu.js');
  initializeCalendar();
  initializeEditButtons();
  initializeRegisterButtons();
  initializeDeleteForms();
  initializeModalButtons();
});
