document.addEventListener('DOMContentLoaded', () => {
  const allRows = Array.from(document.querySelectorAll(`[data-jp-status]`))

  /**
   * @typedef {Object} Filter
   * @property {function(): void} show - A function to make the rows visible.
   * @property {function(): void} hide - A function to hide the rows.
   * @property {function(function(Filter): void): void} onClick - A function to attach a click event listener to the pill.
   */

  /**
   * @param {string} state
   *
   * @returns {Filter|null} - A filter object, or null if a filter pill with the given state does not exist.
   */
  function createFilter(state) {
    const pill = document.querySelector(`[data-jp-filter-pill='${state}']`)
    if (!pill) {
      return null
    }
    const rows = allRows.filter(row => row.dataset.jpStatus === state)
    let hidden = false
    return {
      show() {
        if (!hidden) {
          return
        }
        rows.forEach(row => row.classList.remove('jenkins-hidden'))
        hidden = false
      },
      hide() {
        if (hidden) {
          return
        }
        rows.forEach(row => row.classList.add('jenkins-hidden'))
        hidden = true
      },
      onClick(callback) {
        const self = this
        pill.addEventListener("click", () => callback(self))
      }
    }
  }

  /**
   * @param {[Filter]} filters
   */
  function track(filters) {
    const all = [...filters]
    const active = new Set()

    function toggle(filter) {
      if (active.has(filter)) {
        active.delete(filter)
      } else {
        active.add(filter)
      }
      if (active.size === 0) {
        all.forEach(f => f.show())
      } else {
        all.filter(f => !active.has(f)).forEach(f => f.hide())
        active.forEach(f => f.show())
      }
    }

    all.forEach(filter => filter.onClick(toggle))
  }

  const states = ['FAILED', 'PASSED', 'SKIPPED'];

  const allFilters = states
    .map(state => createFilter(state))
    .filter(x => x);

  track(allFilters);
})
