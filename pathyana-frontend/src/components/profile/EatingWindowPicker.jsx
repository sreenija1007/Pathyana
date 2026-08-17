import { WINDOW_PROTOCOLS } from '../../lib/profileOptions';

/**
 * Eating window picker.
 *
 * Selecting a protocol auto-fills start/end times for that preset.
 * Selecting CUSTOM keeps whatever's in the time fields.
 *
 * Value shape:
 *   {
 *     protocol: 'NONE' | 'TWELVE_TWELVE' | 'FOURTEEN_TEN' | 'SIXTEEN_EIGHT' | 'CUSTOM',
 *     start: 'HH:MM' or null,
 *     end:   'HH:MM' or null,
 *     bedtime: 'HH:MM' or null,
 *     mealToBedBufferMinutes: number or null,
 *   }
 */
export default function EatingWindowPicker({ value, onChange }) {
  const v = value || { protocol: 'NONE', start: null, end: null, bedtime: null, mealToBedBufferMinutes: null };

  function selectProtocol(protocolValue) {
    const preset = WINDOW_PROTOCOLS.find((p) => p.value === protocolValue);
    onChange({
      ...v,
      protocol: protocolValue,
      // Only auto-fill times when a preset has them; CUSTOM/NONE preserve current values
      start: preset?.start ?? v.start,
      end: preset?.end ?? v.end,
    });
  }

  function updateField(field, fieldValue) {
    onChange({ ...v, [field]: fieldValue });
  }

  const isDisabled = v.protocol === 'NONE';

  return (
    <div className="space-y-4">
      {/* Protocol selection */}
      <div className="flex flex-wrap gap-2">
        {WINDOW_PROTOCOLS.map((p) => (
          <button
            key={p.value}
            type="button"
            onClick={() => selectProtocol(p.value)}
            className={`px-3 py-1.5 text-sm rounded-full border transition ${
              v.protocol === p.value
                ? 'bg-terracotta-500 text-white border-terracotta-500'
                : 'bg-white border-cream-300 hover:border-terracotta-400'
            }`}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* Time inputs */}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="label">First meal</label>
          <input
            type="time"
            className="input py-2"
            value={v.start || ''}
            disabled={isDisabled}
            onChange={(e) => updateField('start', e.target.value || null)}
          />
        </div>
        <div>
          <label className="label">Last meal by</label>
          <input
            type="time"
            className="input py-2"
            value={v.end || ''}
            disabled={isDisabled}
            onChange={(e) => updateField('end', e.target.value || null)}
          />
        </div>
      </div>

      {/* Bedtime and buffer */}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="label">Typical bedtime</label>
          <input
            type="time"
            className="input py-2"
            value={v.bedtime || ''}
            disabled={isDisabled}
            onChange={(e) => updateField('bedtime', e.target.value || null)}
          />
        </div>
        <div>
          <label className="label">Min hours between dinner & bed</label>
          <select
            className="input py-2"
            disabled={isDisabled}
            value={v.mealToBedBufferMinutes ?? ''}
            onChange={(e) =>
              updateField('mealToBedBufferMinutes',
                e.target.value === '' ? null : parseInt(e.target.value, 10))
            }
          >
            <option value="">No preference</option>
            <option value="60">1 hour</option>
            <option value="120">2 hours</option>
            <option value="180">3 hours (acid reflux)</option>
            <option value="240">4 hours</option>
          </select>
        </div>
      </div>

      <p className="text-xs text-ink-muted pt-1">
        The eating window helps Pathyana time-shift your meals — useful for reflux,
        diabetes management, and medication timing. Leave on "No restriction" if
        you'd rather not commit to a schedule.
      </p>
    </div>
  );
}
