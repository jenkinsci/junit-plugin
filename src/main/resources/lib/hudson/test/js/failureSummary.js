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
});
