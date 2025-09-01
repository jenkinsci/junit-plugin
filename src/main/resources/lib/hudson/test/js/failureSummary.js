const PREFIX = "test-";
const CACHE = {};

document.addEventListener('DOMContentLoaded', () => {
    initializeShowHideLinks();
    tryShowConfetti();
});

function initializeShowHideLinks() {
    document.querySelectorAll('[id$="-showlink"]').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();

            let link = e.currentTarget;
            const id = link.id.replace(/-showlink$/, '');
            link.classList.toggle("active")

            const table = link.closest("table tbody");
            const tableRow = link.closest("tr");
            let nextRow = tableRow.nextElementSibling;

            // Create the row if it doesn't exist
            if (nextRow == null || nextRow.dataset.type === 'test-row') {
                const nextRow = document.createElement("tr");
                const td = document.createElement("td");
                td.colSpan = 10;
                td.textContent = "Loading";
                nextRow.appendChild(td);
                table.insertBefore(nextRow, tableRow.nextSibling);

                // Clear the query parameters
                const cleanUrl = new URL(document.URL);
                cleanUrl.search = "";
                showSummary(td, cleanUrl + id.replace(PREFIX, '') + "summary");
            } else {
                nextRow.remove();
            }
        });
    });
}

function showSummary(element, query) {
    function setInnerHTML() {
        element.innerHTML = CACHE[query];
        element.querySelectorAll("code").forEach(code => {
            Prism.highlightElement(code);
        })
    }

    if (CACHE[query]) {
        setInnerHTML();
        return;
    }

    let rqo = new XMLHttpRequest();
    rqo.open('GET', query, true);
    rqo.onreadystatechange = function() {
        CACHE[query] = rqo.responseText;
        setInnerHTML();
    }
    rqo.send(null);
}

function tryShowConfetti() {
    const canvas = document.getElementById('confetti-canvas');

    if (canvas) {
        canvas.confetti = canvas.confetti || confetti.create(canvas, { resize: true });

        const defaults = {
            startVelocity: 20,
            spread: 80,
            ticks: 200,
            zIndex: 0,
            particleCount: 10,
            disableForReducedMotion: true
        };

        function randomInRange(min, max) {
            return Math.random() * (max - min) + min;
        }

        function conf() {
            canvas.confetti({
                ...defaults,
                origin: { x: randomInRange(0, 1), y: -0.1 }
            });
        }

        setInterval(conf, 200);
        conf();
    }
}
