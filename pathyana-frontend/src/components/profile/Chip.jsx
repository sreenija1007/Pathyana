/**
 * Selectable pill. Used for allergies, conditions, cuisines, etc.
 *
 * Visual states:
 *   - default:   bordered cream pill
 *   - selected:  filled terracotta pill
 *   - destructive: red-tinted "remove me" pill (with × icon)
 */
export default function Chip({
  selected = false,
  onClick,
  children,
  removable = false,
  onRemove,
}) {
  const baseStyle =
    'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm transition border';

  const variantStyle = selected
    ? 'bg-terracotta-500 text-white border-terracotta-500 hover:bg-terracotta-600'
    : 'bg-white text-ink border-cream-300 hover:border-terracotta-400 hover:text-terracotta-600';

  return (
    <button
      type="button"
      onClick={onClick}
      className={`${baseStyle} ${variantStyle}`}
    >
      <span>{children}</span>
      {removable && (
        <span
          role="button"
          tabIndex={-1}
          aria-label="Remove"
          onClick={(e) => { e.stopPropagation(); onRemove?.(); }}
          className="ml-1 -mr-1 hover:opacity-70"
        >
          ×
        </span>
      )}
    </button>
  );
}
