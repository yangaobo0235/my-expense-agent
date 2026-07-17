import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const schemaUrl = process.env.OPENAPI_SCHEMA_URL ?? 'http://localhost:25101/v3/api-docs';
const committedSchema = resolve('src/api/generated/schema.ts');
const tempDir = mkdtempSync(join(tmpdir(), 'my-expense-agent-openapi-'));
const generatedSchema = join(tempDir, 'schema.ts');
const npx = process.platform === 'win32' ? 'npx.cmd' : 'npx';

function normalize(value) {
  return value.replace(/\r\n/g, '\n').trimEnd();
}

try {
  execFileSync(
    npx,
    ['openapi-typescript', schemaUrl, '-o', generatedSchema],
    { stdio: 'inherit' },
  );

  const expected = normalize(readFileSync(committedSchema, 'utf8'));
  const actual = normalize(readFileSync(generatedSchema, 'utf8'));

  if (expected !== actual) {
    console.error('\nOpenAPI schema is out of date.');
    console.error(`Source: ${schemaUrl}`);
    console.error(`Expected committed file: ${committedSchema}`);
    console.error('\nRun `npm run api:generate` after starting my-expense-agent-backend, then commit the updated schema.');
    process.exitCode = 1;
  } else {
    console.log('OpenAPI schema is up to date.');
  }
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}
