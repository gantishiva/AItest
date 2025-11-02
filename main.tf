# Configure the AWS Provider
terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

# VPC with CIDR 10.0.0.0/17
resource "aws_vpc" "testvpc1" {
  cidr_block           = "10.0.0.0/17"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "testvpc1"
  }
}

# Create Internet Gateway
resource "aws_internet_gateway" "testvpc1_igw" {
  vpc_id = aws_vpc.testvpc1.id

  tags = {
    Name = "testvpc1-igw"
  }
}

# Get available availability zones
data "aws_availability_zones" "available" {
  state = "available"
}

# Existing public subnet
resource "aws_subnet" "public_subnet" {
  vpc_id                  = aws_vpc.testvpc1.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "testvpc1-public-subnet"
    Type = "Public"
  }
}

# NEW: Private subnet
resource "aws_subnet" "private_subnet" {
  vpc_id                  = aws_vpc.testvpc1.id
  cidr_block              = "10.0.2.0/24" # Corrected CIDR range
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = false

  tags = {
    Name = "testvpc1-private-subnet"
    Type = "Private"
  }
}

# Public route table (existing)
resource "aws_route_table" "public_rt" {
  vpc_id = aws_vpc.testvpc1.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.testvpc1_igw.id
  }

  tags = {
    Name = "testvpc1-public-rt"
  }
}

# NEW: Private route table
resource "aws_route_table" "private_rt" {
  vpc_id = aws_vpc.testvpc1.id

  tags = {
    Name = "testvpc1-private-rt"
  }
}

# Associate public subnet with public route table (existing)
resource "aws_route_table_association" "public_rta" {
  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.public_rt.id
}

# NEW: Associate
