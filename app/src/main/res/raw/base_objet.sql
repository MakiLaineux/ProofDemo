DROP TABLE IF EXISTS "table_proof";
CREATE TABLE table_proof (_id INTEGER PRIMARY KEY, filename TEXT, hash TEXT, tree TEXT, txid TEXT, info TEXT, statut INTEGER, datedem datetime DEFAULT NULL);