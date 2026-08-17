/**
 * Status badge for medications and conditions.
 *
 * Three states:
 *   checked      — we have deterministic rules; safe gate is active
 *   advisory     — we don't have rules; only the LLM advisory layer applies
 *   loading      — still fetching coverage info
 *
 * Compact enough to sit inline next to a medication/condition label.
 */
export default function CoverageBadge({ status, size = 'sm' }) {
  const sizes = {
    xs: 'text-[10px] px-1.5 py-0.5',
    sm: 'text-xs px-2 py-0.5',
  };
  const sizeClass = sizes[size] || sizes.sm;

  if (status === 'loading') {
    return (
      <span className={`inline-flex items-center gap-1 rounded-full bg-cream-200 text-ink-muted ${sizeClass}`}>
        …
      </span>
    );
  }

  if (status === 'checked') {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-full bg-forest-100 text-forest-700 border border-forest-200 ${sizeClass}`}
        title="Pathyana has deterministic rules for this. Authoritative safety check applies."
      >
        <ShieldIcon /> checked
      </span>
    );
  }

  if (status === 'advisory') {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-full bg-saffron-100 text-saffron-800 border border-saffron-200 ${sizeClass}`}
        title="Pathyana doesn't have specific rules for this. The LLM Reviewer provides advisory checks only."
      >
        <AlertIcon /> advisory only
      </span>
    );
  }

  return null;
}

function ShieldIcon() {
  return (
    <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
      <path
        fillRule="evenodd"
        d="M10 1.944A11.954 11.954 0 012.166 5C2.056 5.649 2 6.319 2 7c0 5.225 3.34 9.67 8 11.317C14.66 16.67 18 12.225 18 7c0-.682-.057-1.35-.166-2.001A11.954 11.954 0 0110 1.944zm3.707 6.05a1 1 0 00-1.414-1.414L9 9.586 7.707 8.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
        clipRule="evenodd"
      />
    </svg>
  );
}

function AlertIcon() {
  return (
    <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
      <path
        fillRule="evenodd"
        d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 6a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 6zm0 9a1 1 0 100-2 1 1 0 000 2z"
        clipRule="evenodd"
      />
    </svg>
  );
}
