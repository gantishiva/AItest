# Output resources that will be deleted
output "resources_to_delete" {
  description = "Summary of resources that will be deleted"
  value = {
    vpc_id              = aws_vpc.testvpc1.id
    vpc_cidr            = aws_vpc.testvpc1.cidr_block
    public_subnet_id    = aws_subnet.public_subnet.id
    private_subnet_id   = aws_subnet.private_subnet.id
    internet_gateway_id = aws_internet_gateway.testvpc1_igw.id
    public_rt_id        = aws_route_table.public_rt.id
    private_rt_id       = aws_route_table.private_rt.id
  }
}

output "deletion_summary" {
  description = "Deletion summary"
  value = "VPC testvpc1 and all associated resources will be deleted"
}
