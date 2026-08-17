import { useState } from 'react';
import Chip from './Chip';

/**
 * Multi-select via chips.
 *
 * Props:
 *   options:   string[] — chips always shown
 *   value:     string[] — currently selected items (may include items not in `options`)
 *   onChange:  (next: string[]) => void
 *   placeholder: text for the "add custom" input
 *   allowCustom: if true, shows an inline input to add free-text entries
 *
 * Behavior:
 *   - Clicking a preset chip toggles it in/out of `value`
 *   - Custom entries (not in `options`) are rendered as removable chips with an × button
 *   - Pressing Enter or comma in the custom input adds the entry
 */
export default function ChipPicker({
  options = [],
  value = [],
  onChange,
  placeholder = 'Add custom...',
  allowCustom = true,
}) {
  const [draft, setDraft] = useState('');

  // The "extras" are entries in value but not in options — render them as removable chips
  const extras = value.filter((v) => !options.includes(v));

  function toggle(opt) {
    if (value.includes(opt)) {
      onChange(value.filter((v) => v !== opt));
    } else {
      onChange([...value, opt]);
    }
  }

  function addCustom() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    if (!value.includes(trimmed)) {
      onChange([...value, trimmed]);
    }
    setDraft('');
  }

  function removeCustom(name) {
    onChange(value.filter((v) => v !== name));
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      addCustom();
    }
  }

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {options.map((opt) => (
          <Chip
            key={opt}
            selected={value.includes(opt)}
            onClick={() => toggle(opt)}
          >
            {opt}
          </Chip>
        ))}
        {extras.map((name) => (
          <Chip
            key={name}
            selected
            removable
            onClick={() => {}}
            onRemove={() => removeCustom(name)}
          >
            {name}
          </Chip>
        ))}
      </div>

      {allowCustom && (
        <div className="mt-3 flex gap-2">
          <input
            type="text"
            className="input flex-1 py-2 text-sm"
            placeholder={placeholder}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button
            type="button"
            onClick={addCustom}
            disabled={!draft.trim()}
            className="px-4 py-2 text-sm font-medium text-terracotta-600 border border-terracotta-300 rounded-lg hover:bg-terracotta-50 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            Add
          </button>
        </div>
      )}
    </div>
  );
}
