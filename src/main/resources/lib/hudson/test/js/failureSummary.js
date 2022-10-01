// TODO make sure load doesn't happen every time 

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