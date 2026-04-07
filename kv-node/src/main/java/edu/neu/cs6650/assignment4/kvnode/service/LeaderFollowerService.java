package edu.neu.cs6650.assignment4.kvnode.service;

import edu.neu.cs6650.assignment4.kvnode.config.ClusterProperties;
import edu.neu.cs6650.assignment4.kvnode.model.KVEntry;
import edu.neu.cs6650.assignment4.kvnode.model.KVInternalRequest;
import edu.neu.cs6650.assignment4.kvnode.model.KVRequest;
import edu.neu.cs6650.assignment4.kvnode.model.KVResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LeaderFollowerService implements KvModeService {

  private final KvStore kvStore;
  private final ClusterProperties clusterProperties;
  private final RestTemplate restTemplate;

  public LeaderFollowerService(KvStore kvStore,
                               ClusterProperties clusterProperties,
                               RestTemplate restTemplate) {
    this.kvStore = kvStore;
    this.clusterProperties = clusterProperties;
    this.restTemplate = restTemplate;
  }

  @Override
  public KVResponse put(KVRequest request) {
    // 1. Leader sleeps + writes locally
    DelaySimulator.sleepForWrite();
    KVEntry saved = kvStore.putWithNextVersion(request.key(), request.value());

    // 2. Replicate to followers based on WRITE_QUORUM_SIZE (W)
    KVInternalRequest internalReq =
        new KVInternalRequest(request.key(), request.value(), saved.version());

    List<String> followers = clusterProperties.getFollowerUrls();
    int writeQuorum = clusterProperties.getWriteQuorumSize(); // W
    // Leader counts as 1, so replicate to (W - 1) followers
    int replicaTarget = Math.min(writeQuorum - 1, followers.size());

    for (int i = 0; i < replicaTarget; i++) {
      String followerUrl = followers.get(i);
      try {
        restTemplate.put(followerUrl + "/kv/internal", internalReq);
      } catch (Exception e) {
        System.out.println("Replication failed to " + followerUrl + ": " + e.getMessage());
      }
    }

    return new KVResponse(request.key(), null, saved.version());
  }

  @Override
  public Optional<KVResponse> get(String key) {
    // R=1: just read locally
    // R>1: read from (R-1) followers and return highest version
    int readQuorum = clusterProperties.getReadQuorumSize(); // R

    DelaySimulator.sleepForRead();
    Optional<KVEntry> localEntry = kvStore.get(key);

    if (readQuorum <= 1) {
      return localEntry.map(e -> toReadResponse(key, e));
    }

    // Collect responses from (R-1) followers, pick highest version
    KVEntry best = localEntry.orElse(null);
    List<String> followers = clusterProperties.getFollowerUrls();
    int toQuery = Math.min(readQuorum - 1, followers.size());

    for (int i = 0; i < toQuery; i++) {
      try {
        KVResponse resp = restTemplate.getForObject(
            followers.get(i) + "/kv/internal?key=" + key, KVResponse.class);
        if (resp != null && (best == null || resp.version() > best.version())) {
          best = new KVEntry(resp.value(), resp.version());
        }
      } catch (Exception e) {
        System.out.println("Read quorum failed from " + followers.get(i) + ": " + e.getMessage());
      }
    }

    return Optional.ofNullable(best).map(e -> toReadResponse(key, e));
  }

  @Override
  public Optional<KVResponse> localRead(String key) {
    DelaySimulator.sleepForRead();
    return kvStore.get(key).map(e -> toReadResponse(key, e));
  }

  @Override
  public void applyInternalWrite(KVInternalRequest request) {
    DelaySimulator.sleepForWrite();
    kvStore.putWithVersion(request.key(), request.value(), request.version());
  }

  private KVResponse toReadResponse(String key, KVEntry entry) {
    return new KVResponse(key, entry.value(), entry.version());
  }
}