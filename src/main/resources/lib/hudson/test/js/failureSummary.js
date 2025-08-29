const PREFIX = "test-";
const SHOWLINK_SUFFIX = "-showlink";

function showFailureSummary(summaryId, query) {
    let element = document.getElementById(summaryId);

    element.style.display = "";

    if (typeof query !== 'undefined' && element.innerHTML.trim() === 'Loading...') {
        let rqo = new XMLHttpRequest();
        rqo.open('GET', query, true);
        rqo.onreadystatechange = function() {
            element.innerHTML = rqo.responseText;
            initializeShowHideLinks(element);
        }
        rqo.send(null);
    }

}

function hideFailureSummary(summaryId) {
    document.getElementById(summaryId).style.display = "none";
    document.getElementById(summaryId + SHOWLINK_SUFFIX).style.display = "";
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

    if (document.getElementById(id).style.display === "none") {
        // clear the query parameters
        const cleanUrl = new URL(document.URL);
        cleanUrl.search = "";
        showFailureSummary(id, cleanUrl + id.replace(PREFIX, '') + "summary");
    } else {
        hideFailureSummary(id);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initializeShowHideLinks();

    document.querySelectorAll(".jenkins-button").forEach(button => {
        button.addEventListener("click", event => {
            button.classList.add("jenkins-button--primary");
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
