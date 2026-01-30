export default function Field({ label, value, onChange, placeholder, hint }) {
  return (
    <div className="field">
      <label className="label">{label}</label>
      <input
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
      />
      {hint ? <div className="hint">{hint}</div> : null}
    </div>
  );
}
