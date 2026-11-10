SELECT format('CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD %L', :'repl_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'replicator') \gexec

SELECT format('ALTER ROLE replicator WITH REPLICATION LOGIN PASSWORD %L', :'repl_password') \gexec

SELECT pg_create_physical_replication_slot('users_replica_slot')
WHERE NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'users_replica_slot');

SELECT rolname, rolreplication FROM pg_roles WHERE rolname = 'replicator';
SELECT slot_name, slot_type, active FROM pg_replication_slots;
