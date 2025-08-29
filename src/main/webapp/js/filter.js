const failPill = document.querySelector("[data-jp-pill='fail']")
const passPill = document.querySelector("[data-jp-pill='pass']")
const skipPill = document.querySelector("[data-jp-pill='skip']")

function filter(pill, state) {
  const rows = document.querySelectorAll(`[data-jp-status='${state}']`)
  pill.addEventListener("click", () => {
    rows.forEach(row => row.classList.toggle('jenkins-hidden'))
  })
}

failPill && filter(failPill, 'FAILED')
passPill && filter(passPill, 'PASSED')
skipPill && filter(skipPill, 'SKIPPED')
