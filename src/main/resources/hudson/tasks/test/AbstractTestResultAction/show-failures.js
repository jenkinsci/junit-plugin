/**
* Displays all the tests failures, and hides the link allowing to display them from UI.
*/
function showFailures() {
    // Displaying all the hidden elements from the page (those are the failed tests)
    let hiddenElements = document.getElementsByClassName("hidden");

    // DEV MEMO:
    // hiddenElements is not an array but an HTMLCollection.
    // To allow using forEach, we need an array, so I'm using the spread operator below to get that.
    [...hiddenElements].forEach(element => { element.style.display = ""; });

    // Now hiding the link from UI allowing to show all failed tests
    let showFailuresLink = document.getElementById("showLink");
    showFailuresLink.style.display = "none";
}

// Adding an onclick listener to the link in UI allowing to display all failed tests
// DEV MEMO:
// We are doing it after DOM content is loaded as a good practice to ensure we are not slowing down
// the page rendering. In that particular situation the addition of the onclick handler shouldn't
// really impact the page performances, but rather stick with good practices.

document.addEventListener('DOMContentLoaded', (event) => {

  // Retrieving the link from UI allowing to show all failed tests
  // Note: we are retrieving the link by its ID to match how it was already done
  // in the showFailures method above.
  const showFailuresLink = document.getElementById("showLink");

  showFailuresLink.onclick = (_) => showFailures();

});
