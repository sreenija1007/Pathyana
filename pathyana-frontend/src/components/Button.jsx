/**
 * Reusable button.
 *
 * Variants:
 *   - primary:   filled terracotta, the dominant CTA
 *   - secondary: bordered, neutral background, for "Cancel" / secondary actions
 *   - ghost:     no background, for text-only buttons in flows
 *
 * Sizes:
 *   - sm | md (default) | lg
 *
 * Any other props (onClick, type, disabled, etc.) pass through to the underlying <button>.
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...rest
}) {
  const base = 'inline-flex items-center justify-center font-medium rounded-lg ' +
               'transition focus:outline-none focus:ring-2 focus:ring-offset-2 ' +
               'disabled:opacity-50 disabled:cursor-not-allowed';

  const variants = {
    primary:
      'bg-terracotta-500 text-white hover:bg-terracotta-600 ' +
      'focus:ring-terracotta-400 shadow-soft',
    secondary:
      'bg-white text-terracotta-700 border border-terracotta-300 ' +
      'hover:bg-terracotta-50 focus:ring-terracotta-400',
    ghost:
      'bg-transparent text-ink-soft hover:bg-cream-100 focus:ring-cream-300',
  };

  const sizes = {
    sm: 'text-sm px-3 py-1.5',
    md: 'text-base px-5 py-2.5',
    lg: 'text-lg px-7 py-3.5',
  };

  return (
    <button
      className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
