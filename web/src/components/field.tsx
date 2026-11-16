import type { FieldErrors } from "@/lib/validation";

type Common = {
  name: string;
  label: string;
  hint?: string;
  errors?: FieldErrors;
  defaultValue?: string;
  required?: boolean;
  autoComplete?: string;
  span2?: boolean;
};

/** Label, hint and error are wired to the control with aria so a screen reader announces them together. */
function describedBy(name: string, hint: string | undefined, error: string | undefined) {
  return [hint ? `${name}-hint` : null, error ? `${name}-error` : null].filter(Boolean).join(" ") || undefined;
}

export function Field(
  props: Common & {
    type?: "text" | "email" | "password" | "number";
    inputMode?: "text" | "numeric" | "email";
    min?: number;
    max?: number;
    maxLength?: number;
    placeholder?: string;
  },
) {
  const { name, label, hint, errors, defaultValue, required, autoComplete, span2, type = "text", inputMode, min, max, maxLength, placeholder } = props;
  const error = errors?.[name];
  return (
    <div className={`field${span2 ? " span-2" : ""}`}>
      <label htmlFor={name}>
        {label}
        {required ? <span className="sr-only"> (required)</span> : null}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        className="input"
        defaultValue={defaultValue}
        required={required}
        autoComplete={autoComplete}
        inputMode={inputMode}
        min={min}
        max={max}
        maxLength={maxLength}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, hint, error)}
        autoCapitalize={type === "email" || type === "password" ? "none" : undefined}
        spellCheck={type === "email" || type === "password" ? false : undefined}
      />
      {hint ? (
        <p className="hint" id={`${name}-hint`}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="error" id={`${name}-error`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function TextArea(props: Common & { rows?: number; maxLength?: number; placeholder?: string }) {
  const { name, label, hint, errors, defaultValue, required, span2, rows = 3, maxLength, placeholder } = props;
  const error = errors?.[name];
  return (
    <div className={`field${span2 ? " span-2" : ""}`}>
      <label htmlFor={name}>{label}</label>
      <textarea
        id={name}
        name={name}
        className="textarea"
        rows={rows}
        defaultValue={defaultValue}
        required={required}
        maxLength={maxLength}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, hint, error)}
      />
      {hint ? (
        <p className="hint" id={`${name}-hint`}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="error" id={`${name}-error`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
