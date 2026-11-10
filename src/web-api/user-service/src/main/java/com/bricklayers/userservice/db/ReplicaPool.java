package com.bricklayers.userservice.db;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicaPool implements AutoCloseable {

    public record Replica(String name, DataSource dataSource, NodeHealth health) {
    }

    private final List<Replica> replicas;
    private final AtomicInteger next = new AtomicInteger();

    public ReplicaPool(List<Replica> replicas) {
        this.replicas = List.copyOf(replicas);
    }

    public List<Replica> replicas() {
        return replicas;
    }

    public boolean isEmpty() {
        return replicas.isEmpty();
    }

    public Replica nextAvailable() {
        int size = replicas.size();
        for (int attempt = 0; attempt < size; attempt++) {
            Replica candidate = replicas.get(Math.floorMod(next.getAndIncrement(), size));
            if (candidate.health().isUp()) {
                return candidate;
            }
        }
        return null;
    }

    public void probeAll() {
        replicas.forEach(replica -> replica.health().probe());
    }

    @Override
    public void close() throws Exception {
        for (Replica replica : replicas) {
            if (replica.dataSource() instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
