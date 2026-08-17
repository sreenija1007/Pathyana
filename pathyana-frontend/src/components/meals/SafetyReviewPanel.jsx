/**
 * Clinical review panel — v2.
 *
 * Three layers of safety information:
 *
 *   1. DETERMINISTIC DRUG-FOOD FINDINGS — from DrugNutrientRuleEngine. Authoritative.
 *   2. DETERMINISTIC CONDITION-FOOD FINDINGS — from ConditionFoodRuleEngine. Authoritative.
 *   3. LLM REVIEWER CONCERNS — advisory clinical nuance.
 *
 * Plus a transparency footer listing medications/conditions Pathyana doesn't
 * have deterministic rules for. The user knows exactly where the safety net
 * has gaps.
 */
export default function SafetyReviewPanel({ review }) {
  if (!review) return null;

  const verdict = (review.verdict || '').toUpperCase();
  const concerns = review.concerns || [];
  const drugFindings = review.deterministicFindings || [];
  const conditionFindings = review.conditionFindings || [];
  const coverage = review.coverage || {};
  const uncoveredMeds = coverage.uncoveredMedications || [];
  const uncoveredConds = coverage.uncoveredConditions || [];

  const noFindings =
    drugFindings.length === 0 && conditionFindings.length === 0 && concerns.length === 0;

  return (
    <section className="card">
      <header className="flex items-start justify-between gap-4 mb-4">
        <div>
          <h2 className="text-xl font-serif font-semibold text-ink">Clinical review</h2>
          {review.summary && (
            <p className="text-sm text-ink-soft mt-1">{review.summary}</p>
          )}
        </div>
        <VerdictBadge verdict={verdict} />
      </header>

      {/* Drug-food findings */}
      {drugFindings.length > 0 && (
        <div className="mb-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
            Drug–food interactions detected
          </h3>
          <div className="space-y-2">
            {drugFindings.map((f, idx) => (
              <FindingCard
                key={`drug-${idx}`}
                severity={f.severity}
                title={`${f.ingredient} × ${f.medication}`}
                body={f.reason}
                source={f.source}
                origin="drug rule engine"
              />
            ))}
          </div>
        </div>
      )}

      {/* Condition-food findings */}
      {conditionFindings.length > 0 && (
        <div className="mb-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
            Condition-based food alerts
          </h3>
          <div className="space-y-2">
            {conditionFindings.map((f, idx) => (
              <FindingCard
                key={`cond-${idx}`}
                severity={f.severity}
                title={`${f.ingredient} • ${f.condition}`}
                body={f.reason}
                source={f.source}
                origin="condition rule engine"
              />
            ))}
          </div>
        </div>
      )}

      {/* LLM advisory concerns */}
      {concerns.length > 0 && (
        <div className="mb-5">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
            Reviewer notes (advisory)
          </h3>
          <div className="space-y-2">
            {concerns.map((c, idx) => (
              <FindingCard
                key={`con-${idx}`}
                severity={c.severity}
                title={c.relatedTo || c.ingredient}
                body={c.reason}
              />
            ))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {noFindings && (
        <div className="bg-forest-50 border border-forest-100 rounded-lg p-4 text-sm text-forest-700 mb-5">
          ✓ No safety concerns flagged for this recipe.
        </div>
      )}

      {/* Coverage transparency footer */}
      {(uncoveredMeds.length > 0 || uncoveredConds.length > 0) && (
        <div className="border-t border-cream-200 pt-4 mt-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
            Coverage gaps
          </h3>
          <div className="bg-saffron-50 border border-saffron-200 rounded-lg p-3 text-sm text-saffron-900">
            <p className="mb-2 font-medium">
              Some of your profile entries aren't deterministically checked.
              The LLM Reviewer still sees them, but no hard safety gate applies.
            </p>
            {uncoveredMeds.length > 0 && (
              <div className="mb-1.5">
                <span className="text-xs uppercase text-saffron-700 tracking-wider mr-2">Medications:</span>
                {uncoveredMeds.map((m, i) => (
                  <span key={i}>
                    <span className="font-medium">{m}</span>
                    {i < uncoveredMeds.length - 1 ? ', ' : ''}
                  </span>
                ))}
              </div>
            )}
            {uncoveredConds.length > 0 && (
              <div>
                <span className="text-xs uppercase text-saffron-700 tracking-wider mr-2">Conditions:</span>
                {uncoveredConds.map((c, i) => (
                  <span key={i}>
                    <span className="font-medium">{c}</span>
                    {i < uncoveredConds.length - 1 ? ', ' : ''}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

function VerdictBadge({ verdict }) {
  const styles = {
    PASS: 'bg-forest-100 text-forest-700 border-forest-200',
    FAIL: 'bg-terracotta-100 text-terracotta-700 border-terracotta-200',
  };
  return (
    <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border ${styles[verdict] || 'bg-cream-100 text-ink-muted border-cream-200'}`}>
      {verdict || 'PENDING'}
    </span>
  );
}

function FindingCard({ severity, title, body, source, origin }) {
  const sev = (severity || '').toUpperCase();
  const sevClass = {
    BLOCK: 'bg-terracotta-50 border-terracotta-200 text-terracotta-900',
    WARN:  'bg-saffron-50 border-saffron-200 text-saffron-900',
    INFO:  'bg-forest-50 border-forest-100 text-forest-800',
  }[sev] || 'bg-cream-50 border-cream-200';
  const badgeClass = {
    BLOCK: 'bg-terracotta-600 text-white',
    WARN:  'bg-saffron-500 text-white',
    INFO:  'bg-forest-500 text-white',
  }[sev] || 'bg-ink-muted text-white';

  return (
    <div className={`border rounded-lg p-3 ${sevClass}`}>
      <div className="flex items-center gap-2 mb-1 flex-wrap">
        <span className={`text-xs font-bold px-2 py-0.5 rounded ${badgeClass}`}>
          {sev || 'NOTE'}
        </span>
        <span className="text-sm font-semibold">{title}</span>
        {origin && (
          <span className="text-xs text-ink-muted ml-auto">{origin}</span>
        )}
      </div>
      <p className="text-sm leading-relaxed">{body}</p>
      {source && (
        <p className="text-xs text-ink-muted mt-1.5">Source: {source}</p>
      )}
    </div>
  );
}
