export default function Logo({ size = 56 }) {
  return (
    <img
      className="brand-mark"
      src="/logo.png"
      alt="DIGITAL BOT"
      width={size}
      height={size}
      style={{ width: size, height: size }}
    />
  );
}
