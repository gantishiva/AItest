variable "vpc_name" {
  description = "Name of the VPC"
  type        = string
  default     = "testvpc1"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/17"
}

variable "public_subnet_cidr" {
  description = "CIDR block for public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

# NEW: Private subnet variable
variable "private_subnet_cidr" {
  description = "CIDR block for private subnet"
  type        = string
  default     = "10.0.2.0/24"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}
