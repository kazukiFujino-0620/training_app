// Load saved theme on page load
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('training-app-theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
    
    // Load chart data if available
    loadChartData();
});

// Load chart data from server
function loadChartData() {
    const userId = window.userId;
    
    if (!userId) {
        console.error('User ID not set');
        return;
    }
    
    fetch(`/admin/chart-data?userId=${userId}`)
        .then(response => response.json())
        .then(data => {
            if (data && data.labels) {
                renderChart(data);
            }
        })
        .catch(error => {
            console.error('Error loading chart data:', error);
        });
}

// Toggle date search form
function toggleDateSearch() {
    const form = document.getElementById('dateSearchForm');
    form.classList.toggle('hidden');
}

// Search chart data with date range
function searchChartData() {
    const userId = window.userId;
    
    if (!userId) {
        alert('ユーザーIDが設定されていません');
        return;
    }
    
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    if (!startDate || !endDate) {
        alert('開始日と終了日を両方選択してください');
        return;
    }
    
    if (startDate > endDate) {
        alert('開始日は終了日より前にしてください');
        return;
    }
    
    // Show loading state
    const canvas = document.getElementById('volumeChart');
    const ctx = canvas.getContext('2d');
    if (window.myChart) {
        window.myChart.destroy();
    }
    
    fetch(`/admin/chart-data?userId=${userId}&startDate=${startDate}&endDate=${endDate}`)
        .then(response => response.json())
        .then(data => {
            if (data && data.labels) {
                renderChart(data);
            } else {
                alert('指定された期間のデータがありません');
            }
        })
        .catch(error => {
            console.error('Error loading chart data:', error);
            alert('データの読み込みに失敗しました');
        });;
    
    if (!userId) {
        console.error('User ID not set');
        detailsContainer.innerHTML = `
            <div class="text-center py-8 text-danger">
                <p class="text-lg">エラー: ユーザーIDが設定されていません</p>
            </div>
        `;
        return;
    }
}

// Load training details for selected date
function loadTrainingDetails(dateString) {
    const detailsContainer = document.getElementById('training-details');
    const userId = window.userId || 5;
    
    // Show loading state
    detailsContainer.innerHTML = `
        <div class="text-center py-8">
            <div class="inline-block animate-spin">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12a9 9 0 11-6.219-8.56"/>
                </svg>
            </div>
            <p class="text-muted mt-4">読み込み中...</p>
        </div>
    `;
    
    // Fetch training data for selected date
    fetch(`/admin/training-details?userId=${userId}&date=${dateString}`)
        .then(response => response.json())
        .then(data => {
            if (data && data.length > 0) {
                displayTrainingDetails(data, dateString);
            } else {
                displayNoTrainingData(dateString);
            }
        })
        .catch(error => {
            console.error('Error fetching training details:', error);
            displayError(dateString);
        });
}

// Display training details
function displayTrainingDetails(trainings, dateString) {
    const detailsContainer = document.getElementById('training-details');
    
    let html = `
        <div class="mb-4">
            <h3 class="text-lg font-semibold text-primary">${dateString} のトレーニング</h3>
            <p class="text-sm text-muted">${trainings.length} 種目のトレーニング</p>
        </div>
        <div class="space-y-4">
    `;
    
    trainings.forEach(training => {
        const menu = training.menu || '';
        const partName = training.partName || '';
        const isAllCompleted = training.isAllCompleted || false;
        
        html += `
            <div class="border border-border rounded-lg p-4">
                <div class="flex items-center justify-between mb-3">
                    <div>
                        <h4 class="font-semibold">${menu}</h4>
                        <p class="text-sm text-muted">${partName}</p>
                    </div>
                    <div class="text-right">
                        <span class="badge ${isAllCompleted ? 'badge-success' : 'badge-warning'}">
                            ${isAllCompleted ? '完了' : '進行中'}
                        </span>
                    </div>
                </div>
                <div class="space-y-2">
        `;
        
        if (training.details && training.details.length > 0) {
            training.details.forEach(detail => {
                const setNumber = detail.setNumber || 0;
                const weight = detail.weight || 0;
                const reps = detail.reps || 0;
                const restTime = detail.restTime || 0;
                
                html += `
                    <div class="flex items-center justify-between py-2 border-b border-border last:border-b-0">
                        <span class="text-sm font-medium">${setNumber}セット目</span>
                        <div class="flex items-center gap-4">
                            <span class="text-sm">${weight} kg</span>
                            <span class="text-sm">${reps} 回</span>
                            <span class="text-sm">${restTime} 秒</span>
                        </div>
                    </div>
                `;
            });
        } else {
            html += `<p class="text-sm text-muted">詳細データがありません</p>`;
        }
        
        html += `
                </div>
            </div>
        `;
    });
    
    detailsContainer.innerHTML = html;
}

// Display no training data message
function displayNoTrainingData(dateString) {
    const detailsContainer = document.getElementById('training-details');
    detailsContainer.innerHTML = `
        <div class="text-center py-8 text-muted">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="mx-auto mb-4">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
            <p class="text-lg">${dateString} のトレーニングデータはありません</p>
            <p class="text-sm text-muted">カレンダーで他の日付を選択してください</p>
        </div>
    `;
}

// Display error message
function displayError(dateString) {
    const detailsContainer = document.getElementById('training-details');
    detailsContainer.innerHTML = `
        <div class="text-center py-8 text-danger">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="mx-auto mb-4">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <path d="m9 11 3 3 3"/>
            </svg>
            <p class="text-lg">エラーが発生しました</p>
            <p class="text-sm text-muted">${dateString} のデータ読み込みに失敗しました</p>
            <button onclick="loadTrainingDetails('${dateString}')" class="btn btn-primary mt-4">
                再試行
            </button>
        </div>
    `;
}
