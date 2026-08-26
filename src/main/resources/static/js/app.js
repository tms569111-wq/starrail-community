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
            const button = form.querySelector('[data-profile-fetch-button]');
            if (!button || button.disabled) {
                event.preventDefault();
                return;
            }
            button.disabled = true;
            button.textContent = button.dataset.loadingText || '캐릭터 정보를 가져오는 중입니다…';
            form.setAttribute('aria-busy', 'true');
        });
    });

    document.querySelectorAll('[data-profile-cooldown]').forEach(panel => {
        const output = panel.querySelector('[data-profile-countdown]');
        const seconds = Number.parseInt(panel.dataset.profileCooldown || '0', 10);
        const deadline = Date.now() + Math.max(0, seconds) * 1000;
        let timer;

        const update = () => {
            const remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
            const minutes = String(Math.floor(remaining / 60)).padStart(2, '0');
            const rest = String(remaining % 60).padStart(2, '0');
            if (output) output.textContent = `${minutes}:${rest}`;
            if (remaining > 0) return;

            document.querySelectorAll('[data-profile-fetch-button]').forEach(button => {
                button.disabled = false;
            });
            panel.hidden = true;
            window.clearInterval(timer);
        };

        update();
        timer = window.setInterval(update, 1000);
    });
})();
