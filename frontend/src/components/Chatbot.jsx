import { useState } from "react";
import { preguntarAlAsistente } from "../api/asistente";

const welcome = {
  role: "assistant",
  content: "Hola. Puedo consultar tu saldo, cuotas y cómo pagar. Es una lectura segura: no modifico tu deuda.",
};

export default function Chatbot() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([welcome]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);

  async function send(e) {
    e.preventDefault();
    const content = text.trim();
    if (!content || busy) return;
    const next = [...messages, { role: "user", content }];
    setMessages(next);
    setText("");
    setBusy(true);
    try {
      const respuesta = await preguntarAlAsistente(content, next);
      setMessages([...next, { role: "assistant", content: respuesta }]);
    } catch (err) {
      setMessages([...next, { role: "assistant", content: err.message || "MS-AI no está disponible." }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="chat-dock">
      {open ? (
        <div className="chat-panel card">
          <div className="chat-head">
            <strong>Asistente NLP</strong>
            <button className="btn btn-ghost btn-sm" type="button" onClick={() => setOpen(false)}>Cerrar</button>
          </div>
          <div className="messages">
            {messages.map((m, i) => (
              <div key={i} className={`bubble ${m.role === "user" ? "user" : "bot"}`}>
                {m.content}
              </div>
            ))}
          </div>
          <form className="composer" onSubmit={send}>
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="¿Cuál es mi saldo?"
              disabled={busy}
            />
            <button className="btn btn-cyan btn-sm" disabled={busy}>{busy ? "…" : "Enviar"}</button>
          </form>
        </div>
      ) : null}
      <button className="chat-fab" type="button" onClick={() => setOpen((v) => !v)}>
        {open ? "×" : "IA"}
      </button>
    </div>
  );
}
