import pg from 'pg';
const c = new pg.Client({ host:'172.16.101.2', port:5432, user:'postgres', password:'root', database:'postgres' });
await c.connect();
const dbs = await c.query('SELECT datname FROM pg_database ORDER BY 1');
console.log(dbs.rows.map(r => r.datname));
await c.end();
