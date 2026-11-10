package com.bricklayers.userservice.db;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReplicationStatusService {

    static final String PRIMARY_SQL = """
            SELECT application_name, client_addr::text AS client_addr, state, sync_state,
                   sent_lsn::text AS sent_lsn, replay_lsn::text AS replay_lsn,
                   pg_wal_lsn_diff(sent_lsn, replay_lsn) AS lag_bytes,
                   COALESCE(EXTRACT(EPOCH FROM replay_lag) * 1000, 0) AS replay_lag_ms
            FROM pg_stat_replication
            """;

    static final String REPLICA_SQL = """
            SELECT pg_is_in_recovery() AS in_recovery,
                   pg_last_wal_receive_lsn()::text AS receive_lsn,
                   pg_last_wal_replay_lsn()::text AS replay_lsn,
                   COALESCE(EXTRACT(EPOCH FROM now() - pg_last_xact_replay_timestamp()) * 1000, 0) AS since_last_replay_ms,
                   current_setting('recovery_min_apply_delay') AS apply_delay
            """;

    private final JdbcTemplate primary;
    private final List<ReplicaPool.Replica> replicas;

    public ReplicationStatusService(@Qualifier("primaryDataSource") DataSource primary,
                                    ObjectProvider<ReplicaPool> replicaPool) {
        this.primary = new JdbcTemplate(primary);
        ReplicaPool pool = replicaPool.getIfAvailable();
        this.replicas = pool == null ? List.of() : pool.replicas();
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("topology", replicas.isEmpty() ? "single-primary" : "primary+" + replicas.size() + "-replica");
        status.put("primary", query(primary, PRIMARY_SQL, true));
        for (ReplicaPool.Replica replica : replicas) {
            status.put(replica.name(), query(new JdbcTemplate(replica.dataSource()), REPLICA_SQL, false));
        }
        return status;
    }

    private static Object query(JdbcTemplate jdbc, String sql, boolean list) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (list) {
                return Map.of("available", true, "replicas", rows);
            }
            Map<String, Object> row = new LinkedHashMap<>(rows.isEmpty() ? Map.of() : rows.getFirst());
            row.put("available", true);
            return row;
        } catch (DataAccessException exception) {
            return Map.of("available", false, "error", exception.getMostSpecificCause().getMessage());
        }
    }
}
