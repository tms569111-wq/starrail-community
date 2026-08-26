(() => {
    document.querySelectorAll('[data-copy-target]').forEach(button => {
        button.addEventListener('click', async () => {
            const target = document.getElementById(button.dataset.copyTarget);
            if (!target) return;
            await navigator.clipboard.writeText(target.textContent.trim());
            const original = button.textContent;
            button.textContent = '복사됨';
            window.setTimeout(() => button.textContent = original, 1200);
        });
    });

    document.querySelectorAll('textarea[maxlength]').forEach(textarea => {
        const counter = textarea.closest('form')?.querySelector('[data-character-count]');
        const update = () => {
            if (counter) counter.textContent = textarea.value.length + ' / ' + textarea.maxLength;
        };
        textarea.addEventListener('input', update);
        update();
    });

    document.querySelectorAll('[data-confirm-delete]').forEach(form => {
        form.addEventListener('submit', event => {
            if (!window.confirm('댓글을 삭제할까요?')) event.preventDefault();
        });
    });

    document.querySelectorAll('[data-replies-url]').forEach(button => {
        button.addEventListener('click', async () => {
            const target = document.getElementById(button.dataset.repliesTarget);
            if (!target || button.dataset.loaded === 'true') return;
            button.disabled = true;
            const original = button.textContent;
            button.textContent = '답글 불러오는 중…';
            try {
                const response = await fetch(button.dataset.repliesUrl, {
                    headers: {'X-Requested-With': 'fetch'}
                });
                if (!response.ok) throw new Error('reply-load-failed');
                target.innerHTML = await response.text();
                button.dataset.loaded = 'true';
                button.textContent = '답글을 불러왔습니다';
            } catch (error) {
                button.disabled = false;
                button.textContent = original;
                target.textContent = '답글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
            }
        });
    });

    document.querySelectorAll('[data-profile-fetch-form]').forEach(form => {
        form.addEventListener('submit', event => {
            const button = form.querySelector('button[type="submit"]');
            if (!button || button.disabled) {
                event.preventDefault();
                return;
            }
            button.disabled = true;
            button.textContent = button.dataset.loadingText || '불러오는 중…';
            const status = form.querySelector('[data-profile-fetch-status]');
            if (status) status.textContent = '캐릭터 정보를 가져오는 중입니다. 잠시만 기다려 주세요.';
        });
    });

    document.querySelectorAll('[data-profile-cooldown]').forEach(timer => {
        const form = timer.closest('form');
        const button = form?.querySelector('button[type="submit"]');
        const originalLabel = button?.textContent;
        let remaining = Number(timer.dataset.profileCooldown);

        const update = () => {
            if (!Number.isFinite(remaining) || remaining <= 0) {
                timer.textContent = '이제 다시 불러올 수 있습니다.';
                if (button) {
                    button.disabled = false;
                    button.textContent = originalLabel;
                }
                return;
            }
            const minutes = Math.floor(remaining / 60);
            const seconds = String(remaining % 60).padStart(2, '0');
            timer.textContent = `너무 시도가 잦습니다. ${minutes}분 ${seconds}초 뒤 다시 시도해 주세요.`;
            remaining -= 1;
            window.setTimeout(update, 1000);
        };
        update();
    });
})();
