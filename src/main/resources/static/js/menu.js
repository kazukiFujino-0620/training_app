console.log('menu.js loaded');

// Initialize calendar click handlers using event delegation
function initializeCalendar() {
  console.log('initializeCalendar called');
  const calendarCells = document.querySelectorAll('.calendar-cell');
  console.log('Found calendar cells:', calendarCells.length);
  
  if (calendarCells.length === 0) {
    console.log('No calendar cells found');
    return;
  }

  calendarCells.forEach(cell => {
    cell.addEventListener('click', function(e) {
      e.preventDefault();
      e.stopPropagation();
      const dateString = this.getAttribute('data-date');
      console.log('Calendar cell clicked, date:', dateString);
      if (dateString) {
        // Redirect to menu with selected date parameter
        window.location.href = "/menu?date=" + dateString;
      }
    });
  });
}

// Initialize all event handlers
document.addEventListener('DOMContentLoaded', function() {
  console.log('DOMContentLoaded - menu.js');
  initializeCalendar();
});
