/**
 * A section of the profile form — title, optional helper text, and content.
 *
 * Visual structure (left = title/help, right = inputs) on wider screens.
 * Stacks on mobile.
 */
export default function FormSection({ title, helper, children }) {
  return (
    <section className="grid md:grid-cols-3 gap-6 py-8 border-b border-cream-200 last:border-b-0">
      <div className="md:col-span-1">
        <h2 className="text-lg font-serif font-semibold text-ink">{title}</h2>
        {helper && (
          <p className="text-sm text-ink-soft mt-1.5 leading-relaxed">{helper}</p>
        )}
      </div>
      <div className="md:col-span-2 space-y-4">{children}</div>
    </section>
  );
}
