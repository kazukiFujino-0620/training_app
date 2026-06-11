let _scheduledTimeoutId = null;
let _cancelled = false;

self.addEventListener('message', (event) => {
    if (event.data.type === 'SCHEDULE_INTERVAL_END') {
        _cancelled = false;
        if (_scheduledTimeoutId !== null) {
            clearTimeout(_scheduledTimeoutId);
        }
        const { delayMs } = event.data;
        _scheduledTimeoutId = setTimeout(() => {
            _scheduledTimeoutId = null;
            if (_cancelled) return;
            self.registration.showNotification('⏰ インターバル終了！', {
                body: 'トレーニングを再開してください',
                icon: '/favicon.ico',
                badge: '/favicon.ico',
                vibrate: [300, 120, 300, 120, 300],
                requireInteraction: true,
                tag: 'interval-end'
            });
        }, delayMs);

    } else if (event.data.type === 'CANCEL_INTERVAL') {
        _cancelled = true;
        if (_scheduledTimeoutId !== null) {
            clearTimeout(_scheduledTimeoutId);
            _scheduledTimeoutId = null;
        }
    }
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            for (const client of clientList) {
                if (client.url.includes('/start/training') && 'focus' in client) {
                    return client.focus();
                }
            }
            if (clients.openWindow) {
                return clients.openWindow('/start/training');
            }
        })
    );
});
