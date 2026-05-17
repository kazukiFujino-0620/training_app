// Clock functionality
function updateClock() {
    const now = new Date();
    const timeString = now.getFullYear() + '/' + 
        String(now.getMonth() + 1).padStart(2, '0') + '/' + 
        String(now.getDate()).padStart(2, '0') + ' ' + 
        String(now.getHours()).padStart(2, '0') + ':' + 
        String(now.getMinutes()).padStart(2, '0') + ':' + 
        String(now.getSeconds()).padStart(2, '0');
    const clockElement = document.getElementById('current-clock');
    if (clockElement) {
        clockElement.textContent = timeString;
    }
}

// Clock更新を開始
if (document.getElementById('current-clock')) {
    updateClock();
    setInterval(updateClock, 1000);
}

// Set logout button text and behavior based on current page
function initializeLogoutButton() {
    const logoutButton = document.getElementById('logoutButton');
    const logoutForm = document.getElementById('logoutForm');
    if (logoutButton && logoutForm) {
        const currentPath = window.location.pathname;
        console.log('Current path:', currentPath);
        
        // Menu page: show logout button
        if (currentPath === '/menu' || currentPath.startsWith('/menu?')) {
            logoutButton.textContent = 'ログアウト';
            logoutButton.onclick = function(e) {
                e.preventDefault();
                console.log('Logout button clicked');
                logoutForm.submit();
            };
        }
        // Admin pages and training register: show close button
        else if (currentPath === '/training/register' || 
                 currentPath.startsWith('/admin/all-users-training') ||
                 currentPath.startsWith('/admin/user/training-detail')) {
            logoutButton.textContent = '閉じる';
            logoutButton.onclick = function(e) {
                e.preventDefault();
                console.log('Close button clicked');
                window.close();
            };
        }
        // Other pages: show back button
        else {
            logoutButton.textContent = '戻る';
            logoutButton.onclick = function(e) {
                e.preventDefault();
                console.log('Back button clicked');
                window.history.back();
            };
        }
    }
}

// DOMContentLoadedで初期化
document.addEventListener('DOMContentLoaded', initializeLogoutButton);

// Settings dropdown functionality
function toggleSettings() {
    const dropdown = document.getElementById('settingsDropdown');
    if (dropdown) {
        dropdown.classList.toggle('show');
    }
}

// Close dropdown when clicking outside
document.addEventListener('click', function(event) {
    const settingsBtn = document.querySelector('.settings-btn');
    const dropdown = document.getElementById('settingsDropdown');
    
    if (settingsBtn && dropdown && !settingsBtn.contains(event.target) && !dropdown.contains(event.target)) {
        dropdown.classList.remove('show');
    }
});

// Theme toggle functionality
function toggleTheme() {
    const html = document.documentElement;
    const currentTheme = html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    html.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    // Update theme switch state
    const themeSwitch = document.querySelector('.theme-switch');
    if (themeSwitch) {
        themeSwitch.setAttribute('data-theme', newTheme);
    }
    
    // Save theme preference to server
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (csrfToken && csrfHeader) {
        fetch('/api/theme', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader.getAttribute('content')]: csrfToken.getAttribute('content')
            },
            body: JSON.stringify({ theme: newTheme })
        }).catch(error => console.error('Failed to save theme preference:', error));
    }
}

// Load saved theme on page load
function initializeTheme() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
    
    const themeSwitch = document.querySelector('.theme-switch');
    if (themeSwitch) {
        themeSwitch.setAttribute('data-theme', savedTheme);
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeTheme);
} else {
    initializeTheme();
}

// Placeholder functions for other settings
function showSettings() {
    alert('設定画面は準備中です');
}

function showHelp() {
    alert('ヘルプ画面は準備中です');
}

// Initialize settings button
function initializeSettingsButton() {
    const settingsBtn = document.querySelector('.settings-btn');
    if (settingsBtn) {
        settingsBtn.addEventListener('click', toggleSettings);
    }
}

// Initialize theme switch
function initializeThemeSwitch() {
    const themeSwitch = document.querySelector('.theme-switch');
    if (themeSwitch) {
        themeSwitch.addEventListener('click', toggleTheme);
    }
}

// Initialize settings items
function initializeSettingsItems() {
    const settingsItems = document.querySelectorAll('.settings-item');
    settingsItems.forEach(item => {
        const action = item.getAttribute('data-action');
        if (action === 'show-settings') {
            item.addEventListener('click', showSettings);
        } else if (action === 'show-help') {
            item.addEventListener('click', showHelp);
        }
    });
}

// Initialize all common functionality
document.addEventListener('DOMContentLoaded', function() {
    initializeSettingsButton();
    initializeThemeSwitch();
    initializeSettingsItems();
});
