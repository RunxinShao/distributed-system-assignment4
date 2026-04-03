terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

resource "aws_security_group" "kv_nodes_sg" {
  name        = "${var.project_name}-kv-nodes-sg"
  description = "Allow HTTP traffic for KV nodes and load tester"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr_block]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "kv_nodes" {
  count                  = 5
  ami                    = var.ami_id
  instance_type          = "t3.micro"
  key_name               = var.key_name
  subnet_id              = element(var.subnet_ids, count.index % length(var.subnet_ids))
  vpc_security_group_ids = [aws_security_group.kv_nodes_sg.id]

  tags = {
    Name = "${var.project_name}-kv-node-${count.index + 1}"
    Role = "kv-node"
  }
}

resource "aws_instance" "load_tester" {
  ami                    = var.ami_id
  instance_type          = "t3.micro"
  key_name               = var.key_name
  subnet_id              = element(var.subnet_ids, 0)
  vpc_security_group_ids = [aws_security_group.kv_nodes_sg.id]

  tags = {
    Name = "${var.project_name}-load-tester"
    Role = "load-tester"
  }
}

resource "aws_lb" "leaderless_alb" {
  name               = "${var.project_name}-leaderless-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.kv_nodes_sg.id]
  subnets            = var.subnet_ids
}

resource "aws_lb_target_group" "leaderless_target_group" {
  name        = "${var.project_name}-leaderless-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "instance"
  vpc_id      = var.vpc_id

  health_check {
    path                = "/local_read?key=healthcheck"
    matcher             = "200,404"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
  }
}

resource "aws_lb_target_group_attachment" "leaderless_targets" {
  count            = 5
  target_group_arn = aws_lb_target_group.leaderless_target_group.arn
  target_id        = aws_instance.kv_nodes[count.index].id
  port             = 8080
}

resource "aws_lb_listener" "leaderless_http" {
  load_balancer_arn = aws_lb.leaderless_alb.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.leaderless_target_group.arn
  }
}
