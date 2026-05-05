let myChart;

function renderChart(data) {
    const canvas = document.getElementById('volumeChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    // すでにグラフがある場合は破棄しないと重複して表示がおかしくなる
    if (myChart) {
        myChart.destroy();
    }

    myChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.labels, // Java側で作った yyyy-MM-dd のリスト
            datasets: [
                {
                    label: '胸',
                    data: data.chest, // Java側の Map から渡されるリスト
                    borderColor: '#ff6384',
                    tension: 0.3,
                    fill: false
                },
                {
                    label: '背中',
                    data: data.back,
                    borderColor: '#36a2eb',
                    tension: 0.3,
                    fill: false
                },
                {
                    label: '腕',
                    data: data.arms,
                    borderColor: '#ffce56',
                    tension: 0.3,
                    fill: false
                },
                {
                    label: '肩',
                    data: data.shoulders,
                    borderColor: '#9966ff',
                    tension: 0.3,
                    fill: false
                },
                {
                    label: '脚',
                    data: data.legs,
                    borderColor: '#4bc0c0',
                    tension: 0.3,
                    fill: false
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    ticks: {
                        autoSkip: true,
                        maxTicksLimit: 10 // ラベルが多すぎるときに間引く
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