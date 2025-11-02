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

# VPC - will be destroyed
resource "aws_vpc" "testvpc1" {
  cidr_block           = "10.0.0.0/17"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "testvpc1"
  }
}

# Internet Gateway - will be destroyed
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

# Public subnet - will be destroyed
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

# Private subnet - will be destroyed
resource "aws_subnet" "private_subnet" {
  vpc_id                  = aws_vpc.testvpc1.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = false

  tags = {
    Name = "testvpc1-private-subnet"
    Type = "Private"
  }
}

# Public route table - will be destroyed
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

# Private route table - will be destroyed
resource "aws_route_table" "private_rt" {
  vpc_id = aws_vpc.testvpc1.id

  tags = {
    Name = "testvpc1-private-rt"
  }
}

# Route table associations - will be destroyed
resource "aws_route_table_association" "public_rta" {
  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.public_rt.id
}

resource "aws_route_table_association" "private_rta" {
  subnet_id      = aws_subnet.private_subnet.id
  route_table_id = aws_route_table.private_rt.id
}
