# Output VPC ID
output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.testvpc1.id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.testvpc1.cidr_block
}

# Output public subnet ID
output "public_subnet_id" {
  description = "ID of the public subnet"
  value       = aws_subnet.public_subnet.id
}

# NEW: Output private subnet ID
output "private_subnet_id" {
  description = "ID of the private subnet"
  value       = aws_subnet.private_subnet.id
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway"
  value       = aws_internet_gateway.testvpc1_igw.id
}

output "public_route_table_id" {
  description = "ID of the public route table"
  value       = aws_route_table.public_rt.id
}

# NEW: Output private route table ID
output "private_route_table_id" {
  description = "ID of the private route table"
  value       = aws_route_table.private_rt.id
}

# Updated summary output
output "infrastructure_summary" {
  description = "Summary of created infrastructure"
  value = {
    vpc_id              = aws_vpc.testvpc1.id
    vpc_name            = "testvpc1"
    vpc_cidr            = aws_vpc.testvpc1.cidr_block
    public_subnet_id    = aws_subnet.public_subnet.id
    public_subnet_cidr  = aws_subnet.public_subnet.cidr_block
    private_subnet_id   = aws_subnet.private_subnet.id
    private_subnet_cidr = aws_subnet.private_subnet.cidr_block
    availability_zones = [
      aws_subnet.public_subnet.availability_zone,
      aws_subnet.private_subnet.availability_zone
    ]
    internet_gateway_id = aws_internet_gateway.testvpc1_igw.id
    dns_hostnames       = aws_vpc.testvpc1.enable_dns_hostnames
    dns_support         = aws_vpc.testvpc1.enable_dns_support
  }
}
