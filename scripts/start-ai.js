const { spawn, spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const aiDir = path.join(root, "ms-ai");
const isWin = process.platform === "win32";

function pythonBin() {
  const candidates = isWin ? ["py", "python", "python3"] : ["python3", "python"];
  for (const cmd of candidates) {
    const probe = spawnSync(cmd, ["--version"], { encoding: "utf8", shell: isWin });
    if (probe.status === 0) {
      return cmd;
    }
  }
  return null;
}

const py = pythonBin();
if (!py) {
  console.error("MS-AI: no se encontró Python. Instala Python 3.11+ para el chatbot.");
  process.exit(1);
}

const req = path.join(aiDir, "requirements.txt");
if (fs.existsSync(req)) {
  spawnSync(py, isWin && py === "py" ? ["-3", "-m", "pip", "install", "-q", "-r", req] : ["-m", "pip", "install", "-q", "-r", req], {
    cwd: aiDir,
    stdio: "inherit",
    shell: isWin,
    env: process.env,
  });
}

const args = isWin && py === "py"
  ? ["-3", "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8085"]
  : ["-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8085"];

const child = spawn(py, args, {
  cwd: aiDir,
  stdio: "inherit",
  shell: isWin,
  env: process.env,
});
child.on("exit", (code) => process.exit(code ?? 1));
