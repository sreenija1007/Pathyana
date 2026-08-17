/**
 * Pathyana wordmark. Used in the navbar and footer.
 *
 * Designed to scale: pass `size` ("sm" | "md" | "lg") to control rendered size.
 * Uses a simple text wordmark for now — a real logo mark can be slotted in later.
 */
export default function Logo({ size = 'md' }) {
  const sizes = {
    sm: 'text-xl',
    md: 'text-2xl',
    lg: 'text-4xl',
  };

  return (
    <div className={`font-serif font-bold ${sizes[size]} text-terracotta-600 tracking-tight`}>
      pathyana
      <span className="text-saffron-400">.</span>
    </div>
  );
}
