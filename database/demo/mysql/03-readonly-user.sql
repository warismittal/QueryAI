-- Least-privilege, read-only login that QueryAI uses to query the MySQL demo database.
-- Change the password for anything other than local demos.
CREATE USER IF NOT EXISTS 'queryai_reader'@'%' IDENTIFIED BY 'reader_pass';
GRANT SELECT, SHOW VIEW ON commerce.* TO 'queryai_reader'@'%';
FLUSH PRIVILEGES;
