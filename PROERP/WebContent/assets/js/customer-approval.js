const approvalForm = document.getElementById('approval-check');
const approvalStatus = document.getElementById('approval-status');
let checking = false;
let timer;

async function checkApproval() {
    if (checking) return;
    clearTimeout(timer);
    checking = true;
    let finished = false;
    try {
        const body = new URLSearchParams(new FormData(approvalForm));
        body.set('poll', '1');
        const response = await fetch(approvalForm.action, { method: 'POST', body });
        if (response.redirected || response.status === 403) {
            finished = true;
            window.location.replace(response.redirected ? response.url : approvalForm.action);
        } else if (response.status === 204) {
            approvalStatus.textContent = 'Approval pending.';
        } else {
            approvalStatus.textContent = 'Could not check approval. Trying again shortly.';
        }
    } catch {
        approvalStatus.textContent = 'Could not reach PROERP. Trying again shortly.';
    } finally {
        checking = false;
        if (!finished) timer = setTimeout(checkApproval, 5000);
    }
}

approvalForm.addEventListener('submit', event => {
    event.preventDefault();
    checkApproval();
});
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) checkApproval();
});
timer = setTimeout(checkApproval, 5000);
