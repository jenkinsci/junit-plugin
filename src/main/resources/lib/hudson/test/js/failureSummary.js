const PREFIX = "test-";
const SHOWLINK_SUFFIX = "-showlink";
const HIDELINK_SUFFIX = "-hidelink";

function showFailureSummary(summaryId, query) {
    let element = document.getElementById(summaryId);

    element.style.display = "";
    document.getElementById(summaryId + SHOWLINK_SUFFIX).style.display = "none";
    document.getElementById(summaryId + HIDELINK_SUFFIX).style.display = "";

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
    document.getElementById(summaryId + HIDELINK_SUFFIX).style.display = "none";
}

function initializeShowHideLinks(container) {
    container = container || document;

    container.querySelectorAll('a[id$="-showlink"], a[id$="-hidelink"]').forEach(link => {
        link.addEventListener('click', handleShowHideClick);
        link.style.cursor = 'pointer';
    });
}

function handleShowHideClick(event) {
    event.preventDefault();

    let link = event.target.closest('a[id$="-showlink"], a[id$="-hidelink"]');
    const id = link.id.replace(/-showlink$/, '').replace(/-hidelink$/, '');

    if (link.id.endsWith('-showlink')) {
        showFailureSummary(id, document.URL + id.replace(PREFIX, '') + "summary");
    } else {
        hideFailureSummary(id);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initializeShowHideLinks();
});
