// Las cuatro pruebas de punta a punta, en el orden en que se apoyan:
// ida y sin-databridge parten de cero; vuelta deja la cartera como la
// espera el portal.
//
//     node pruebas/todas.mjs --limpiar-bases
import { spawnSync } from 'node:child_process';
import path from 'node:path';

if (!process.argv.includes('--limpiar-bases') && process.env.PRUEBAS_LIMPIAR !== '1') {
  console.log('Estas pruebas vacian las tablas de cartera de las bases de desarrollo. Corre con --limpiar-bases.');
  process.exit(2);
}

const resultado = {};
for (const suite of ['ida', 'sin-databridge', 'vuelta', 'portal']) {
  console.log(`\n========== ${suite} ==========`);
  const r = spawnSync(process.execPath, [path.join(import.meta.dirname, `${suite}.mjs`), '--limpiar-bases'], { stdio: 'inherit' });
  resultado[suite] = r.status === 0;
  if (suite === 'vuelta' && !resultado.vuelta) {
    console.log('vuelta fallo: el portal depende de su estado final, asi que no se corre.');
    resultado.portal = false;
    break;
  }
}

console.log('\n========== resumen ==========');
for (const [suite, bien] of Object.entries(resultado)) console.log(`${bien ? 'OK  ' : 'FAIL'} ${suite}`);
process.exit(Object.values(resultado).every(Boolean) ? 0 : 1);
