/**
 * CSRF Helper - CSRF トークンを自動的に fetch リクエストに付与
 */

// CSRF トークンをメタタグから取得
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]');
    return token ? token.getAttribute('content') : '';
}

// CSRF ヘッダー名をメタタグから取得
function getCsrfHeaderName() {
    const header = document.querySelector('meta[name="_csrf_header"]');
    return header ? header.getAttribute('content') : 'X-CSRF-TOKEN';
}

/**
 * 指定された fetch オプションに CSRF トークンを付与
 * @param {Object} options - fetch オプション
 * @returns {Object} CSRF トークン付きの fetch オプション
 */
function addCsrfToken(options = {}) {
    const token = getCsrfToken();
    if (!token) {
        console.warn('[CSRF Helper] CSRF token not found in meta tag');
        return options;
    }

    const headers = options.headers || {};
    const headerName = getCsrfHeaderName();
    headers[headerName] = token;
    
    return {
        ...options,
        headers: headers
    };
}

/**
 * Fetch API ラッパー - CSRF トークン自動付与
 * @param {string} url - リクエスト URL
 * @param {Object} options - fetch オプション
 * @returns {Promise} fetch Promise
 */
function fetchWithCsrf(url, options = {}) {
    // GET リクエストには CSRF トークンは不要（Spring Security のデフォルト設定）
    if (options.method && !['GET', 'HEAD'].includes(options.method.toUpperCase())) {
        return fetch(url, addCsrfToken(options));
    }
    return fetch(url, options);
}
