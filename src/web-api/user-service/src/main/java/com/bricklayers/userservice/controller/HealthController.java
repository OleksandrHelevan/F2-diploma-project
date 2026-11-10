package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.db.NodeHealth;
import com.bricklayers.userservice.db.ReplicaPool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  HealthController
  @version  1.0.0
  @since 03.11.2026 - 14.00
*/

@RestController
public class HealthController {

    private final NodeHealth primary;
    private final List<ReplicaPool.Replica> replicas;

    public HealthController(NodeHealth primaryHealth, ObjectProvider<ReplicaPool> replicaPool) {
        this.primary = primaryHealth;
        ReplicaPool pool = replicaPool.getIfAvailable();
        this.replicas = pool == null ? List.of() : pool.replicas();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean primaryUp = primary.isUp();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("primary", primaryUp ? "UP" : "DOWN");
        if (replicas.isEmpty()) {
            body.put("status", primaryUp ? "UP" : "DOWN");
            return ResponseEntity.status(primaryUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        long replicasUp = replicas.stream().filter(replica -> replica.health().isUp()).count();
        replicas.forEach(replica -> body.put(replica.name(), replica.health().isUp() ? "UP" : "DOWN"));
        if (primaryUp && replicasUp == replicas.size()) {
            body.put("status", "UP");
        } else if (primaryUp) {
            body.put("status", "DEGRADED");
        } else if (replicasUp > 0) {
            body.put("status", "READ_ONLY");
        } else {
            body.put("status", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
