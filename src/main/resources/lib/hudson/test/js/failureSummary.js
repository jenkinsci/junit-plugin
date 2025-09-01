const PREFIX = "test-";
const SHOWLINK_SUFFIX = "-showlink";

/**
 * @param {Element} element
 * @param {string} query
 */
function showFailureSummary(element, query) {
    // TODO - validate this caches
    if (typeof query !== 'undefined' && !element.classList.contains("jenkins-hidden")) {
        let rqo = new XMLHttpRequest();
        rqo.open('GET', query, true);
        rqo.onreadystatechange = function() {
            element.innerHTML = rqo.responseText;
            initializeShowHideLinks(element);
        }
        rqo.send(null);
    }
}

function initializeShowHideLinks(container) {
    container = container || document;

    container.querySelectorAll('[id$="-showlink"]').forEach(link => {
        link.addEventListener('click', handleShowHideClick);
    });
}

function handleShowHideClick(event) {
    event.preventDefault();

    let link = event.target.closest('[id$="-showlink"]');
    const id = link.id.replace(/-showlink$/, '').replace(/-hidelink$/, '');
    link.classList.toggle("active")

    const nextRow = link.closest("tr").nextElementSibling;

    if (nextRow.classList.contains("jenkins-hidden")) {
        // clear the query parameters
        const cleanUrl = new URL(document.URL);
        cleanUrl.search = "";
        showFailureSummary(nextRow.querySelector("td"), cleanUrl + id.replace(PREFIX, '') + "summary");
    }

    nextRow.classList.toggle("jenkins-hidden");
}

document.addEventListener('DOMContentLoaded', () => {
    initializeShowHideLinks();

    document.querySelectorAll(".jp-pill").forEach(button => {
        button.addEventListener("click", () => {
            button.classList.toggle("jenkins-button--primary");
        })
    })

    var canvas = document.getElementById('my-canvas');

    if (canvas) {
        // only initialize once
        canvas.confetti = canvas.confetti || confetti.create(canvas, { resize: true });

        var defaults = {
            startVelocity: 20,        // much slower speed
            spread: 80,              // narrow spread so it falls downwards
            ticks: 200,              // particles live longer
            zIndex: 0,
            particleCount: 10,        // fewer particles per burst
            disableForReducedMotion: true
        };

        function randomInRange(min, max) {
            return Math.random() * (max - min) + min;
        }

        function conf() {
            canvas.confetti({
                ...defaults,
                origin: { x: randomInRange(0, 1), y: -0.1 } // start just above the canvas
            });
        }

// trigger small bursts quickly to simulate rain
        setInterval(conf, 200);

        conf();
    }
});
