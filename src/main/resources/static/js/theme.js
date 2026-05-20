/**
 * Theme Management JavaScript
 * Handles dark/light mode switching and persistence
 */

const initialTheme = (() => {
    try {
        return localStorage.getItem('training-app-theme');
    } catch (e) {
        console.warn('Failed to read theme from localStorage:', e);
        return null;
    }
})();

if (initialTheme) {
    document.documentElement.setAttribute('data-theme', initialTheme);
}

class ThemeManager {
    constructor() {
        this.storageKey = 'training-app-theme';
        this.defaultTheme = 'light';
        this.currentTheme = this.getStoredTheme() || this.defaultTheme;
        
        this.init();
    }
    
    init() {
        // Apply stored theme on page load
        this.applyTheme(this.currentTheme);
        
        // Listen for system theme changes
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
                if (!this.getStoredTheme()) {
                    // Only auto-switch if user hasn't manually set a preference
                    const systemTheme = e.matches ? 'dark' : 'light';
                    this.applyTheme(systemTheme);
                }
            });
        }
        
        // Add keyboard shortcut for theme switching (Ctrl/Cmd + Shift + T)
        document.addEventListener('keydown', (e) => {
            if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'T') {
                e.preventDefault();
                this.toggleTheme();
            }
        });

        this.initPageHandlers();
    }
    
    initPageHandlers() {
        this.initializeFormLoading();
        this.preventFormResubmission();
        installDataActionDispatcher();
        installDataChangeDispatcher();
    }

    initializeFormLoading() {
        const formSelectors = ['.login-form', '#forgetPasswordForm', '#resetPasswordForm', '.signup-form'];
        formSelectors.forEach(selector => {
            const form = document.querySelector(selector);
            if (form) {
                form.addEventListener('submit', function() {
                    this.classList.add('loading');
                });
            }
        });
    }

    preventFormResubmission() {
        if (window.history.replaceState) {
            window.history.replaceState(null, null, window.location.href);
        }
    }
    
    getStoredTheme() {
        try {
            return localStorage.getItem(this.storageKey);
        } catch (e) {
            console.warn('Failed to read theme from localStorage:', e);
            return null;
        }
    }
    
    setStoredTheme(theme) {
        try {
            localStorage.setItem(this.storageKey, theme);
        } catch (e) {
            console.warn('Failed to save theme to localStorage:', e);
        }
    }
    
    applyTheme(theme) {
        const html = document.documentElement;
        const body = document.body;
        
        // Remove existing theme class
        html.removeAttribute('data-theme');
        body.classList.remove('theme-light', 'theme-dark');
        
        // Apply new theme
        html.setAttribute('data-theme', theme);
        body.classList.add(`theme-${theme}`);
        
        // Update theme switch UI
        this.updateThemeSwitch(theme);
        
        // Update meta theme-color for mobile browsers
        this.updateMetaThemeColor(theme);
        
        // Dispatch custom event
        this.dispatchThemeChange(theme);
        
        this.currentTheme = theme;
    }
    
    toggleTheme() {
        const newTheme = this.currentTheme === 'dark' ? 'light' : 'dark';
        this.setTheme(newTheme);
        return newTheme;
    }
    
    setTheme(theme) {
        if (theme !== 'light' && theme !== 'dark') {
            console.warn('Invalid theme:', theme, 'Using default:', this.defaultTheme);
            theme = this.defaultTheme;
        }
        
        this.applyTheme(theme);
        this.setStoredTheme(theme);
        
        // Save to server if user is logged in
        this.saveThemeToServer(theme);
    }
    
    updateThemeSwitch(theme) {
        const themeSwitches = document.querySelectorAll('.theme-switch');
        themeSwitches.forEach(switchElement => {
            switchElement.setAttribute('data-theme', theme);
        });
        
        const themeLabels = document.querySelectorAll('.theme-toggle-label');
        themeLabels.forEach(label => {
            label.textContent = theme === 'dark' ? 'ダークモード' : 'ライトモード';
        });
    }
    
    updateMetaThemeColor(theme) {
        const metaThemeColor = document.querySelector('meta[name="theme-color"]');
        if (metaThemeColor) {
            const color = theme === 'dark' ? '#1e293b' : '#ffffff';
            metaThemeColor.setAttribute('content', color);
        }
    }
    
    dispatchThemeChange(theme) {
        const event = new CustomEvent('themechange', {
            detail: { theme, previousTheme: this.currentTheme }
        });
        document.dispatchEvent(event);
    }
    
    async saveThemeToServer(theme) {
        try {
            const csrfToken = document.querySelector('meta[name="_csrf"]');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
            
            if (!csrfToken || !csrfHeader) {
                return; // No CSRF tokens available, likely not logged in
            }
            
            const response = await fetch('/api/theme', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader.getAttribute('content')]: csrfToken.getAttribute('content')
                },
                body: JSON.stringify({ theme })
            });
            
            if (!response.ok) {
                console.warn('Failed to save theme preference to server');
            }
        } catch (error) {
            console.error('Error saving theme to server:', error);
        }
    }
    
    getSystemTheme() {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return 'dark';
        }
        return 'light';
    }
    
    resetToSystem() {
        localStorage.removeItem(this.storageKey);
        const systemTheme = this.getSystemTheme();
        this.applyTheme(systemTheme);
    }
    
    // Utility method to check if current theme is dark
    isDark() {
        return this.currentTheme === 'dark';
    }
    
    // Utility method to get current theme
    getCurrentTheme() {
        return this.currentTheme;
    }
}

// Initialize theme manager
const themeManager = new ThemeManager();

function getActionArgs(el) {
    const args = [];
    if (el.dataset.index) args.push(parseInt(el.dataset.index, 10));
    if (el.dataset.trainingIndex) args.push(parseInt(el.dataset.trainingIndex, 10));
    if (el.dataset.setIndex) args.push(parseInt(el.dataset.setIndex, 10));
    if (el.dataset.date) args.push(el.dataset.date);
    if (el.dataset.delta) args.push(parseInt(el.dataset.delta, 10));
    if (el.dataset.seconds) args.push(parseInt(el.dataset.seconds, 10));
    if (el.dataset.field) args.push(el.dataset.field);
    if (el.dataset.value) args.push(el.dataset.value);
    return args;
}

function invokeDataAction(el, action) {
    const fn = window[action] || window[action.replace(/-([a-z])/g, (_, c) => c.toUpperCase())];
    if (typeof fn !== 'function') return false;
    const args = getActionArgs(el);
    if (args.length === 0 && el.value !== undefined) {
        args.push(el.value);
    }
    try {
        fn.apply(null, args);
    } catch (err) {
        console.error('Error invoking data-action', action, err);
    }
    return true;
}

function installDataActionDispatcher() {
    if (window.__dataActionDispatcherInstalled) return;
    window.__dataActionDispatcherInstalled = true;

    document.addEventListener('click', function(e) {
        const el = e.target.closest('[data-action]');
        if (!el) return;
        const action = el.dataset.action;
        if (!action) return;
        if (invokeDataAction(el, action)) {
            e.preventDefault();
        }
    });
}

function installDataChangeDispatcher() {
    if (window.__dataChangeDispatcherInstalled) return;
    window.__dataChangeDispatcherInstalled = true;

    document.addEventListener('change', function(e) {
        const el = e.target.closest('[data-change]');
        if (!el) return;
        const action = el.dataset.change;
        if (!action) return;
        if (invokeDataAction(el, action)) {
            e.preventDefault();
        }
    });
}

// Global functions for backward compatibility
window.toggleTheme = () => themeManager.toggleTheme();
window.setTheme = (theme) => themeManager.setTheme(theme);
window.getCurrentTheme = () => themeManager.getCurrentTheme();
window.isDarkTheme = () => themeManager.isDark();

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ThemeManager;
}
