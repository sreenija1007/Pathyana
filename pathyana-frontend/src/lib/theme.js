/**
 * Design tokens exported for use in JS (charts, dynamic styles, etc.).
 *
 * Most styling should go through Tailwind classes. This file is for the small number
 * of cases where you need a color value at runtime — e.g., a chart library that
 * accepts hex strings as props.
 *
 * Keep in sync with tailwind.config.js manually for now.
 */
export const colors = {
  terracotta: '#d35e3a',
  saffron:    '#ff9c1a',
  forest:     '#557d4f',
  cream:      '#faf6ec',
  ink:        '#2a221c',
};

export const severityColor = (severity) => {
  switch ((severity || '').toUpperCase()) {
    case 'BLOCK': return '#bd4929'; // terracotta-600 — strong warning
    case 'WARN':  return '#ff9c1a'; // saffron-400 — caution
    case 'INFO':  return '#557d4f'; // forest-500 — informational
    default:      return '#8b7d72'; // ink-muted
  }
};
