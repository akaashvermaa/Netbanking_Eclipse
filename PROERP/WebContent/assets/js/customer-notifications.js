(() => {
    const widget = document.querySelector('.customer-notifications');
    if (!widget) return;

    const fallback = widget.querySelector('[data-notification-link]');
    const toggle = widget.querySelector('[data-notification-toggle]');
    const popup = widget.querySelector('#notification-popup');
    const close = widget.querySelector('[data-notification-close]');
    const content = widget.querySelector('[data-notification-content]');
    const announcement = widget.querySelector('[data-notification-announcement]');
    const endpoint = new URL(fallback.href);
    endpoint.searchParams.set('view', 'popup');
    const storageKey = 'proerp.notifications.' + widget.dataset.customer;
    let known = null;
    let displayed = '';
    let loading = false;
    let ended = false;
    try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey));
        if (Array.isArray(saved)) known = new Set(saved);
    } catch {
        // Browsers can restrict tab storage.
    }

    function setOpen(open, focus = false) {
        popup.hidden = !open;
        toggle.setAttribute('aria-expanded', String(open));
        if (focus) (open ? close : toggle).focus();
        if (!open) announcement.textContent = '';
    }

    async function refresh() {
        if (loading || ended || document.hidden) return;
        loading = true;
        try {
            const response = await fetch(endpoint, { cache: 'no-store' });
            if (response.redirected) {
                endFeed();
                return;
            }
            if (!response.ok) throw new Error('Notifications unavailable');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const feed = page.querySelector('[data-notification-feed]');
            if (!feed) throw new Error('Notification feed missing');
            if (feed.dataset.notificationFeed !== widget.dataset.customer) {
                endFeed();
                return;
            }
            const events = [...feed.querySelectorAll('[data-event]')].map(row => row.dataset.event);
            const incoming = known !== null && events.some(event => !known.has(event));
            known = new Set(events);
            try {
                sessionStorage.setItem(storageKey, JSON.stringify(events));
            } catch {
                // Keep tracking events for the current page.
            }
            const markup = feed.outerHTML;
            if (markup !== displayed && !content.contains(document.activeElement)) {
                content.replaceChildren(feed);
                displayed = markup;
            }
            if (incoming) {
                setOpen(true);
                announcement.textContent = 'New banking notifications.';
            }
        } catch {
            if (!displayed) content.textContent = 'Notifications are temporarily unavailable.';
        } finally {
            loading = false;
        }
    }

    function endFeed() {
        ended = true;
        displayed = '';
        const link = document.createElement('a');
        link.href = fallback.href;
        link.textContent = 'Open notifications';
        content.replaceChildren(link);
    }

    fallback.hidden = true;
    toggle.hidden = false;
    toggle.addEventListener('click', () => {
        const open = popup.hidden;
        setOpen(open, open);
        if (open) refresh();
    });
    close.addEventListener('click', () => setOpen(false, true));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !popup.hidden) {
            setOpen(false, widget.contains(document.activeElement));
        }
    });
    document.addEventListener('pointerdown', event => {
        if (!popup.hidden && !widget.contains(event.target)) setOpen(false);
    });
    document.addEventListener('visibilitychange', refresh);
    window.setInterval(refresh, 20000);
    refresh();
})();
