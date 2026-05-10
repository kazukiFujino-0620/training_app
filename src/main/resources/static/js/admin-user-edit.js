// Load saved theme on page load
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('training-app-theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
});
