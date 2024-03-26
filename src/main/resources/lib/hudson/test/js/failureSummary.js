
const PREFIX = "test-";
const SHOWLINK_SUFFIX = "-showlink";
const HIDELINK_SUFFIX = "-hidelink";

function showFailureSummary(summaryId, query) {
    let element = document.getElementById(summaryId);

    element.style.display = "";
    document.getElementById(summaryId + SHOWLINK_SUFFIX).style.display = "none";
    document.getElementById(summaryId + HIDELINK_SUFFIX).style.display = "";

    if (typeof query !== 'undefined') {
        let rqo = new XMLHttpRequest();
        rqo.open('GET', query, true);
        rqo.onreadystatechange = function() { element.innerHTML = rqo.responseText; }
        rqo.send(null);
    }
}

function hideFailureSummary(summaryId) {
    document.getElementById(summaryId).style.display = "none";
    document.getElementById(summaryId + SHOWLINK_SUFFIX).style.display = "";
    document.getElementById(summaryId + HIDELINK_SUFFIX).style.display = "none";
}


document.addEventListener('DOMContentLoaded', () => {

    // add the onclick behavior for all the "showlinks"
    const testShowlinks = document.querySelectorAll("a[id*=test-][id*=-showlink]");
    testShowlinks.forEach((element) => {
        element.onclick = (_) => {
            console.log("testShowlinks clicked");
            const id = element.id.replace(PREFIX, '').replace(SHOWLINK_SUFFIX, '');
            const summaryId = PREFIX + id;
            console.log(`testShowlinks url ${document.URL + id + 'summary'}`);
            showFailureSummary(summaryId, document.URL + id + "summary");
        }
    });

    // add the onclick behavior for all the "hidelinks"
    const testHidelinks = document.querySelectorAll("a[id*=test-][id*=-hidelink]");
    testHidelinks.forEach((element) => {
        element.onclick = (_) => {
            console.log("testHidelinks clicked");
            const id = element.id.replace(PREFIX, '').replace(HIDELINK_SUFFIX, '');
            const summaryId = PREFIX + id;
            hideFailureSummary(summaryId);
        }
    });

    console.log("listeners added");

});
