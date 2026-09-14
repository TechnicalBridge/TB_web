const { spawn, spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const isWin = process.platform === "win32";
const root = path.resolve(__dirname, "..");
const wrapper = path.join(root, isWin ? "mvnw.cmd" : "mvnw");

function javaBin(home) {
  return path.join(home, "bin", isWin ? "java.exe" : "java");
}

function javaMajor(home) {
  const bin = javaBin(home);
  if (!home || !fs.existsSync(bin)) {
    return 0;
  }
  const result = spawnSync(bin, ["-version"], { encoding: "utf8" });
  const text = `${result.stderr || ""}${result.stdout || ""}`;
  const match = text.match(/version "(\d+)/);
  if (!match) {
    return 0;
  }
  const major = Number(match[1]);
  return major === 1 ? 8 : major;
}

function candidateHomes() {
  const homes = [];
  const push = (dir) => {
    if (dir && !homes.includes(dir)) {
      homes.push(dir);
    }
  };
  push(process.env.JAVA_HOME);
  if (isWin) {
    const programFiles = process.env.ProgramFiles || "C:\\Program Files";
    for (const rootDir of [
      path.join(programFiles, "Java"),
      path.join(programFiles, "Eclipse Adoptium"),
      path.join(programFiles, "Microsoft"),
    ]) {
      if (!fs.existsSync(rootDir)) continue;
      for (const name of fs.readdirSync(rootDir)) {
        push(path.join(rootDir, name));
      }
    }
  } else {
    for (const dir of ["/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"]) {
      if (!fs.existsSync(dir)) continue;
      for (const name of fs.readdirSync(dir)) {
        const full = path.join(dir, name);
        push(full);
        push(path.join(full, "Contents", "Home"));
      }
    }
  }
  return homes;
}

function resolveJavaHome() {
  let best = null;
  let bestMajor = 0;
  for (const home of candidateHomes()) {
    const major = javaMajor(home);
    if (major >= 17 && major > bestMajor) {
      best = home;
      bestMajor = major;
    }
  }
  return best;
}

const javaHome = resolveJavaHome();
if (!javaHome) {
  console.error("Se necesita JDK 17 o superior para Spring Boot.");
  process.exit(1);
}

const env = { ...process.env, JAVA_HOME: javaHome };
const binPath = path.join(javaHome, "bin");
if (isWin) {
  env.Path = `${binPath}${path.delimiter}${env.Path || env.PATH || ""}`;
} else {
  env.PATH = `${binPath}${path.delimiter}${env.PATH || ""}`;
}

const child = spawn(wrapper, process.argv.slice(2), {
  stdio: "inherit",
  shell: isWin,
  cwd: root,
  env,
});

child.on("exit", (code) => process.exit(code ?? 1));
