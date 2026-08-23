// Load saved theme on page load
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('training-app-theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
    onEditRoleChange();
});

// 対象ユーザーのロールが「店舗管理者（ROLE_STORE_ADMIN）」または「トレーナー（ROLE_TRAINER）」の場合のみ、
// 店舗兼任設定セクションを表示する。店舗兼任という設定自体がこの2ロールに対してのみ意味を持つため
// （ORG_ADMIN/ADMINは既に上位権限でカバーされる）。
function onEditRoleChange() {
    const roleSelect = document.getElementById('role');
    const section = document.getElementById('storeAssignmentsSection');
    if (!section) return;
    const supportsStoreAssignments = roleSelect
        ? (roleSelect.value === 'ROLE_STORE_ADMIN' || roleSelect.value === 'ROLE_TRAINER')
        : false;
    section.style.display = supportsStoreAssignments ? '' : 'none';
}
