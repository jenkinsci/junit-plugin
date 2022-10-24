
function showFailureSummary(id,query) {
    var element = document.getElementById(id)
    element.style.display = "";
    document.getElementById(id + "-showlink").style.display = "none";
    document.getElementById(id + "-hidelink").style.display = "";

    if (typeof query !== 'undefined') {
        var rqo = new XMLHttpRequest();
        rqo.open('GET', query, true);
        rqo.onreadystatechange = function() { element.innerHTML = rqo.responseText; }
        rqo.send(null);
    }
}

function hideFailureSummary(id) {
    document.getElementById(id).style.display = "none";
    document.getElementById(id + "-showlink").style.display = "";
    document.getElementById(id + "-hidelink").style.display = "none";
}


document.addEventListener('DOMContentLoaded', (event) => {

    const testShowlinks = document.querySelectorAll("a[id*=-showlink]");
    testShowlinks.forEach((element) => {
        element.onclick = (_) => {
            const testId = element.id.replace('-showlink', '');
            showFailureSummary(testId, document.URL + "/summary");
        }
    });

    const testHidelinks = document.querySelectorAll("a[id*=-hidelink]");
    testHidelinks.forEach((element) => {
        element.onclick = (_) => {
            const testId = element.id.replace('-hidelink', '');
            hideFailureSummary(testId);
        }
    });

});
