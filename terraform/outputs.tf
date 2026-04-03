output "kv_node_public_ips" {
  description = "Public IP addresses of KV nodes"
  value       = aws_instance.kv_nodes[*].public_ip
}

output "load_tester_public_ip" {
  description = "Public IP of load tester instance"
  value       = aws_instance.load_tester.public_ip
}

output "leaderless_alb_dns_name" {
  description = "DNS name for the leaderless ALB endpoint"
  value       = aws_lb.leaderless_alb.dns_name
}
