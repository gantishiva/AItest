pipeline {
    agent any
    
    parameters {
        booleanParam(
            name: 'CONFIRM_DELETION',
            defaultValue: false,
            description: 'Confirm that you want to DELETE the VPC testvpc1 and all resources'
        )
        booleanParam(
            name: 'AUTO_APPROVE',
            defaultValue: false,
            description: 'Auto approve deletion (skip manual confirmation)'
        )
    }
    
    environment {
        AWS_DEFAULT_REGION = 'us-east-1'
        TF_IN_AUTOMATION = 'true'
        TF_INPUT = '0'
        TERRAFORM_DIR = '.'
    }
    
    stages {
        stage('Pre-flight Check') {
            steps {
                script {
                    if (!params.CONFIRM_DELETION) {
                        error("❌ DELETION NOT CONFIRMED: You must check 'CONFIRM_DELETION' to proceed")
                    }
                    
                    echo """
                    🚨 VPC DELETION PIPELINE 🚨
                    
                    VPC: testvpc1
                    Region: us-east-1
                    Build: ${env.BUILD_NUMBER}
                    
                    ⚠️  THIS WILL DELETE ALL VPC RESOURCES ⚠️
                    """
                }
            }
        }
        
        stage('Checkout') {
            steps {
                echo "=== Pulling Terraform code from Git ==="
                checkout scm
                
                sh '''
                    echo "Repository: ${GIT_URL}"
                    echo "Branch: ${GIT_BRANCH}"
                    echo "Commit: $(git rev-parse HEAD)"
                '''
            }
        }
        
        stage('Terraform Init') {
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    sh '''
                        echo "=== Initializing Terraform ==="
                        terraform init
                        terraform version
                    '''
                }
            }
        }
        
        stage('Show Current Resources') {
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    sh '''
                        echo "=== Current VPC Resources ==="
                        terraform output resources_to_delete || echo "No current state found"
                        
                        echo "=== Checking VPC in AWS ==="
                        aws ec2 describe-vpcs --filters "Name=tag:Name,Values=testvpc1" --region ${AWS_DEFAULT_REGION} --output table || echo "VPC not found"
                    '''
                }
            }
        }
        
        stage('Terraform Destroy Plan') {
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    sh '''
                        echo "=== Creating Destroy Plan ==="
                        terraform plan -destroy -out=destroy.tfplan
                        
                        echo "=== Destroy Plan Details ==="
                        terraform show destroy.tfplan
                    '''
                    
                    // Archive the destroy plan
                    archiveArtifacts artifacts: 'destroy.tfplan', fingerprint: true
                }
            }
        }
        
        stage('Manual Approval') {
            when {
                not { params.AUTO_APPROVE }
            }
            steps {
                script {
                    def approvalMessage = """
🚨 FINAL CONFIRMATION REQUIRED 🚨

You are about to PERMANENTLY DELETE:

VPC: testvpc1
- Public Subnet (10.0.1.0/24)
- Private Subnet (10.0.2.0/24)
- Internet Gateway
- Route Tables
- All associations

⚠️  THIS CANNOT BE UNDONE! ⚠️

Are you absolutely sure?
"""
                    
                    input message: approvalMessage,
                          ok: 'YES, DELETE VPC',
                          submitterParameter: 'APPROVER'
                    
                    echo "✅ Deletion approved by: ${env.APPROVER}"
                }
            }
        }
        
        stage('Apply Terraform Destroy') {
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    script {
                        def applyCommand = params.AUTO_APPROVE ? 
                            "terraform apply -auto-approve destroy.tfplan" :
                            "terraform apply destroy.tfplan"
                        
                        sh """
                            echo "=== Executing VPC Deletion ==="
                            echo "Command: ${applyCommand}"
                            ${applyCommand}
                            
                            echo "=== VPC Deletion Completed ==="
                        """
                    }
                }
            }
        }
        
        stage('Verify Deletion') {
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    sh '''
                        echo "=== Verifying VPC Deletion ==="
                        
                        # Check if VPC still exists
                        VPC_EXISTS=$(aws ec2 describe-vpcs --filters "Name=tag:Name,Values=testvpc1" --region ${AWS_DEFAULT_REGION} --query 'Vpcs[0].VpcId' --output text 2>/dev/null)
                        
                        if [ "$VPC_EXISTS" = "None" ] || [ -z "$VPC_EXISTS" ]; then
                            echo "✅ VPC testvpc1 successfully deleted"
                        else
                            echo "❌ VPC still exists: $VPC_EXISTS"
                            exit 1
                        fi
                        
                        # Show terraform state (should be empty)
                        echo "=== Final Terraform State ==="
                        terraform show || echo "No resources in state (expected after destroy)"
                    '''
                }
            }
        }
    }
    
    post {
        always {
            echo "=== Pipeline Summary ==="
            echo "VPC: testvpc1"
            echo "Build: ${env.BUILD_NUMBER}"
            echo "Duration: ${currentBuild.durationString}"
            echo "Result: ${currentBuild.currentResult}"
            
            // Archive terraform state files
            archiveArtifacts artifacts: 'terraform.tfstate*', allowEmptyArchive: true
        }
        
        success {
            echo """
            ✅ VPC DELETION SUCCESSFUL!
            
            VPC testvpc1 and all associated resources have been permanently deleted.
            Build: ${env.BUILD_NUMBER}
            Duration: ${currentBuild.durationString}
            """
        }
        
        failure {
            echo """
            ❌ VPC DELETION FAILED!
            
            The deletion process encountered errors.
            Some resources may still exist and require manual cleanup.
            Build: ${env.BUILD_NUMBER}
            """
        }
        
        cleanup {
            // Clean up workspace
            cleanWs()
        }
    }
}
