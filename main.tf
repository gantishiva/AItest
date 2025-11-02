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

# Create VPC
resource "aws_vpc" "my_dev_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "my-dev-vpc"
    Environment = "development"
    ManagedBy   = "Terraform"
  }
}

# Create Internet Gateway
resource "aws_internet_gateway" "my_dev_igw" {
  vpc_id = aws_vpc.my_dev_vpc.id

  tags = {
    Name = "my-dev-vpc-igw"
  }
}

# Get available availability zones
data "aws_availability_zones" "available" {
  state = "available"
}

# Create first public subnet
resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.my_dev_vpc.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name = "my-dev-vpc-public-subnet-1"
    Type = "Public"
  }
}

# Create second public subnet
resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.my_dev_vpc.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = true

  tags = {
    Name = "my-dev-vpc-public-subnet-2"
    Type = "Public"
  }
}

# Create route table for public subnets
resource "aws_route_table" "public_rt" {
  vpc_id = aws_vpc.my_dev_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.my_dev_igw.id
  }

  tags = {
    Name = "my-dev-vpc-public-rt"
  }
}

# Associate first public subnet with public route table
resource "aws_route_table_association" "public_rta_1" {
  subnet_id      = aws_subnet.public_subnet_1.id
  route_table_id = aws_route_table.public_rt.id
}

# Associate second public subnet with public route table
resource "aws_route_table_association" "public_rta_2" {
  subnet_id      = aws_subnet.public_subnet_2.id
  route_table_id = aws_route_table.public_rt.id
}
