package edu.neu.cs6650.assignment4.kvnode.service;

import edu.neu.cs6650.assignment4.kvnode.model.KVEntry;
import edu.neu.cs6650.assignment4.kvnode.model.KVInternalRequest;
import edu.neu.cs6650.assignment4.kvnode.model.KVRequest;
import edu.neu.cs6650.assignment4.kvnode.model.KVResponse;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LeaderFollowerService implements KvModeService {

  private final KvStore kvStore;

  public LeaderFollowerService(KvStore kvStore) {
    this.kvStore = kvStore;
  }

  @Override
  public KVResponse put(KVRequest request) {
    DelaySimulator.sleepForWrite();
    KVEntry saved = kvStore.putWithNextVersion(request.key(), request.value());
    return new KVResponse(request.key(), null, saved.version());
  }

  @Override
  public Optional<KVResponse> get(String key) {
    DelaySimulator.sleepForRead();
    return kvStore.get(key).map(entry -> toReadResponse(key, entry));
  }

  @Override
  public Optional<KVResponse> localRead(String key) {
    DelaySimulator.sleepForRead();
    return kvStore.get(key).map(entry -> toReadResponse(key, entry));
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
