# Output VPC ID
output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.my_dev_vpc.id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.my_dev_vpc.cidr_block
}

# Output subnet IDs
output "public_subnet_1_id" {
  description = "ID of the first public subnet"
  value       = aws_subnet.public_subnet_1.id
}

output "public_subnet_2_id" {
  description = "ID of the second public subnet"
  value       = aws_subnet.public_subnet_2.id
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway"
  value       = aws_internet_gateway.my_dev_igw.id
}

output "public_route_table_id" {
  description = "ID of the public route table"
  value       = aws_route_table.public_rt.id
}

# Summary output
output "infrastructure_summary" {
  description = "Summary of created infrastructure"
  value = {
    vpc_id              = aws_vpc.my_dev_vpc.id
    vpc_cidr            = aws_vpc.my_dev_vpc.cidr_block
    public_subnet_1_id  = aws_subnet.public_subnet_1.id
    public_subnet_2_id  = aws_subnet.public_subnet_2.id
    internet_gateway_id = aws_internet_gateway.my_dev_igw.id
    availability_zones  = [
      aws_subnet.public_subnet_1.availability_zone,
      aws_subnet.public_subnet_2.availability_zone
    ]
  }
}
