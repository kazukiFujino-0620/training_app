(function () {
    'use strict';

    const SESSION_TIMEOUT_MS    = 2 * 60 * 60 * 1000;
    const WARN_BEFORE_MS        = 15 * 60 * 1000;
    const PING_INTERVAL_TRAINING = 3 * 60 * 1000;
    const PING_INTERVAL_DEFAULT  = 10 * 60 * 1000;

    const SESSION_START_KEY = 'sessionStart';

    const isTrainingPage = window.location.pathname === '/start/training';

    let countdownTimerId = null;

    function getSessionStart() {
        return parseInt(sessionStorage.getItem(SESSION_START_KEY) || '0', 10);
    }

    function resetSessionStart() {
        sessionStorage.setItem(SESSION_START_KEY, Date.now().toString());
    }

    function showWarningModal() {
        const modal = document.getElementById('sessionWarningModal');
        if (!modal || !modal.classList.contains('hidden')) return;

        const note = document.getElementById('sessionTrainingNote');
        if (note) note.style.display = isTrainingPage ? '' : 'none';

        modal.classList.remove('hidden');
        startCountdown();
    }

    function hideWarningModal() {
        const modal = document.getElementById('sessionWarningModal');
        if (modal) modal.classList.add('hidden');
        stopCountdown();
    }

    function showExpiredModal() {
        hideWarningModal();
        const modal = document.getElementById('sessionExpiredModal');
        if (!modal) return;

        const note = document.getElementById('sessionExpiredTrainingNote');
        if (note) note.style.display = isTrainingPage ? '' : 'none';

        modal.classList.remove('hidden');
    }

    function startCountdown() {
        stopCountdown();
        updateCountdownDisplay();
        countdownTimerId = setInterval(updateCountdownDisplay, 1000);
    }

    function stopCountdown() {
        if (countdownTimerId !== null) {
            clearInterval(countdownTimerId);
            countdownTimerId = null;
        }
    }

    function updateCountdownDisplay() {
        const remaining = Math.max(0, (getSessionStart() + SESSION_TIMEOUT_MS) - Date.now());
        const totalSeconds = Math.ceil(remaining / 1000);
        const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
        const ss = String(totalSeconds % 60).padStart(2, '0');
        const el = document.getElementById('sessionCountdown');
        if (el) el.textContent = mm + ':' + ss;
    }

    async function sendPing() {
        try {
            const res = await fetch('/api/session/ping', {
                method: 'GET',
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
                credentials: 'same-origin'
            });

            if (res.ok) {
                resetSessionStart();
                hideWarningModal();
                return;
            }

            if (res.status === 401) {
                showExpiredModal();
                return;
            }
        } catch (_) {
            showExpiredModal();
        }
    }

    function checkWarning() {
        const remaining = (getSessionStart() + SESSION_TIMEOUT_MS) - Date.now();
        if (remaining <= 0) {
            showExpiredModal();
        } else if (remaining <= WARN_BEFORE_MS) {
            showWarningModal();
        }
    }

    function init() {
        if (!sessionStorage.getItem(SESSION_START_KEY)) {
            resetSessionStart();
        }

        const interval = isTrainingPage ? PING_INTERVAL_TRAINING : PING_INTERVAL_DEFAULT;
        setInterval(async () => {
            await sendPing();
            checkWarning();
        }, interval);

        const continueBtn = document.getElementById('sessionContinueBtn');
        if (continueBtn) {
            continueBtn.addEventListener('click', async () => {
                await sendPing();
            });
        }

        const logoutBtn = document.getElementById('sessionLogoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                document.getElementById('logoutForm')?.submit();
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
