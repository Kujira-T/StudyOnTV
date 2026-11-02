(function () {
  'use strict';
  var queue = [], loading = false;
  function render(element) {
    if (!element || !document.documentElement.contains(element)) return;
    window.renderMathInElement(element, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '\\[', right: '\\]', display: true },
        { left: '\\(', right: '\\)', display: false },
        { left: '$', right: '$', display: false }
      ],
      throwOnError: false, trust: false, strict: 'ignore', maxExpand: 200,
      ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option', 'input']
    });
  }
  function script(src, done) {
    var element = document.createElement('script');
    element.src = src; element.onload = done;
    element.onerror = function () { loading = false; };
    document.head.appendChild(element);
  }
  window.SOTV_MATH = function (element) {
    if (!element) return;
    if (window.renderMathInElement) { render(element); return; }
    if (!/\$|\\[\[(]/.test(element.textContent || '')) return;
    if (queue.indexOf(element) < 0) queue.push(element);
    if (loading) return;
    loading = true;
    var style = document.createElement('link'); style.rel = 'stylesheet'; style.href = 'assets/vendor/katex/katex.min.css'; document.head.appendChild(style);
    script('assets/vendor/katex/katex.min.js', function () {
      script('assets/vendor/katex/contrib/auto-render.min.js', function () {
        loading = false;
        var pending = queue.slice(); queue = [];
        pending.forEach(render);
      });
    });
  };
})();
