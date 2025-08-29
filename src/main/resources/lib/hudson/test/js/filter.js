document.addEventListener('DOMContentLoaded', () => {
  const failPill = document.querySelector("[data-jp-pill='fail']")
  const passPill = document.querySelector("[data-jp-pill='pass']")
  const skipPill = document.querySelector("[data-jp-pill='skip']")
  const allRows = Array.from(document.querySelectorAll(`[data-jp-status]`))


  class Filter {
    _pill
    _rows

    constructor(pill, state) {
      this._pill = pill
      this._rows = allRows.filter(row => row.dataset.jpStatus === state)
    }

    hide() {
      this._rows.forEach(row => row.classList.add('jenkins-hidden'))
    }

    show() {
      this._rows.forEach(row => row.classList.remove('jenkins-hidden'))
    }

    register(callback) {
      const self = this;
      this._pill.addEventListener("click", () => {
        console.log("click", self._pill)
        callback(self)
      })
    }
  }

  function track(filters) {
    const all = [...filters]
    let previous = null

    function action(filter) {
      if (previous === filter) {
        previous = null
        all.forEach(filter => filter.show())
      } else {
        if (!previous) {
          all.filter(f => f !== filter).forEach(filter => filter.hide())
        } else {
          previous.hide()
        }
        filter.show()
        previous = filter
      }
    }

    all.forEach(filter => filter.register(action))
  }

  const fail = failPill && new Filter(failPill, 'FAILED')
  const pass = passPill && new Filter(passPill, 'PASSED')
  const skip = skipPill && new Filter(skipPill, 'SKIPPED')

  const all = [ fail, pass, skip].filter(x => x)

  track(all)
})
