package edu.neu.cs6650.assignment4.kvnode.config;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kv")
public class ClusterProperties {

  private String role = "leader";
  private List<String> followerUrls = List.of();
  private List<String> peerUrls = List.of();
  private int writeQuorumSize = 5;
  private int readQuorumSize = 1;

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public List<String> getFollowerUrls() {
    return followerUrls;
  }

  public void setFollowerUrls(List<String> followerUrls) {
    this.followerUrls = sanitizeUrls(followerUrls);
  }

  public List<String> getPeerUrls() {
    return peerUrls;
  }

  public void setPeerUrls(List<String> peerUrls) {
    this.peerUrls = sanitizeUrls(peerUrls);
  }

  public int getWriteQuorumSize() {
    return writeQuorumSize;
  }

  public void setWriteQuorumSize(int writeQuorumSize) {
    this.writeQuorumSize = writeQuorumSize;
  }

  public int getReadQuorumSize() {
    return readQuorumSize;
  }

  public void setReadQuorumSize(int readQuorumSize) {
    this.readQuorumSize = readQuorumSize;
  }

  public boolean isLeaderless() {
    return !peerUrls.isEmpty();
  }

  public boolean isLeader() {
    return "leader".equalsIgnoreCase(role);
  }

  public boolean isFollower() {
    return "follower".equalsIgnoreCase(role);
  }

  private List<String> sanitizeUrls(List<String> urls) {
    if (urls == null) {
      return List.of();
    }

    return urls.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }
}
