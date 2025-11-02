pipeline {
    agent {
        label 'windows'
    }
    
    parameters {
        
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
        TF_CLI_ARGS = '-no-color'
    }
    
    stages {
        stage('Pre-flight Check') {
            steps {
                script {
                    if (!params.CONFIRM_DELETION) {
                        error("❌ DELETION NOT CONFIRMED: You must check 'CONFIRM_DELETION' to proceed")
                    }
                    
                    echo """
                    🚨 VPC DELETION PIPELINE (Windows) 🚨
                    
                    VPC: testvpc1
                    Region: us-east-1
                    Build: ${env.BUILD_NUMBER}
                    Agent: Windows
                    
                    ⚠️  THIS WILL DELETE ALL VPC RESOURCES ⚠️
                    """
                }
            }
        }
        
        stage('Checkout') {
            steps {
                echo "=== Pulling Terraform code from Git ==="
                checkout scm
                
                bat '''
                    echo Repository: %GIT_URL%
                    echo Branch: %GIT_BRANCH%
                    echo Build Number: %BUILD_NUMBER%
                    echo Workspace: %WORKSPACE%
                    
                    echo === Git Information ===
                    git --version
                    git log --oneline -3
                    git rev-parse HEAD
                '''
            }
        }
        
        stage('Environment Setup') {
            steps {
                bat '''
                    echo === Environment Setup ===
                    echo AWS Region: %AWS_DEFAULT_REGION%
                    echo Terraform Automation: %TF_IN_AUTOMATION%
                    echo Current Directory: %CD%
                    
                    echo === Tool Versions ===
                    terraform version || echo Terraform not found
                    aws --version || echo AWS CLI not found
                    
                    echo === Workspace Contents ===
                    dir
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
                    bat '''
                        echo === Initializing Terraform ===
                        terraform init -input=false
                        
                        echo === Terraform Configuration ===
                        terraform version
                        terraform providers
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
                    bat '''
                        echo === Current VPC Resources ===
                        terraform output resources_to_delete || echo No current state found
                        
                        echo === Checking VPC in AWS ===
                        aws ec2 describe-vpcs --filters "Name=tag:Name,Values=testvpc1" --region %AWS_DEFAULT_REGION% --output table || echo VPC not found
                        
                        echo === Current Terraform State ===
                        terraform show || echo No state file found
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
                    bat '''
                        echo === Creating Destroy Plan ===
                        terraform plan -destroy -input=false -out=destroy-%BUILD_NUMBER%.tfplan
                        
                        echo === Destroy Plan Details ===
                        terraform show -no-color destroy-%BUILD_NUMBER%.tfplan > destroy-plan-%BUILD_NUMBER%.txt
                        type destroy-plan-%BUILD_NUMBER%.txt
                    '''
                    
                    // Archive the destroy plan
                    archiveArtifacts artifacts: "destroy-${env.BUILD_NUMBER}.tfplan, destroy-plan-${env.BUILD_NUMBER}.txt", fingerprint: true
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
                            "terraform apply -input=false -auto-approve destroy-${env.BUILD_NUMBER}.tfplan" :
                            "terraform apply -input=false destroy-${env.BUILD_NUMBER}.tfplan"
                        
                        bat """
                            echo === Executing VPC Deletion ===
                            echo Command: ${applyCommand}
                            echo Timestamp: %DATE% %TIME%
                            ${applyCommand}
                            
                            echo === VPC Deletion Completed ===
                            echo Timestamp: %DATE% %TIME%
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
                    bat '''
                        echo === Verifying VPC Deletion ===
                        
                        echo Checking if VPC testvpc1 still exists...
                        for /f "delims=" %%i in ('aws ec2 describe-vpcs --filters "Name=tag:Name,Values=testvpc1" --region %AWS_DEFAULT_REGION% --query "Vpcs[0].VpcId" --output text 2^>nul') do set VPC_EXISTS=%%i
                        
                        if "%VPC_EXISTS%"=="None" (
                            echo ✅ VPC testvpc1 successfully deleted
                        ) else if "%VPC_EXISTS%"=="" (
                            echo ✅ VPC testvpc1 successfully deleted
                        ) else (
                            echo ❌ VPC still exists: %VPC_EXISTS%
                            exit /b 1
                        )
                        
                        echo === Final Terraform State ===
                        terraform show || echo No resources in state ^(expected after destroy^)
                        
                        echo === Verification Complete ===
                    '''
                }
            }
        }
        
        stage('Cleanup') {
            steps {
                bat '''
                    echo === Cleaning up temporary files ===
                    if exist destroy-*.tfplan del /f /q destroy-*.tfplan
                    if exist destroy-plan-*.txt del /f /q destroy-plan-*.txt
                    if exist .terraform\\terraform.tfstate del /f /q .terraform\\terraform.tfstate
                    echo Cleanup completed
                '''
            }
        }
    }
    
    post {
        always {
            script {
                bat '''
                    echo === Pipeline Summary ===
                    echo VPC: testvpc1
                    echo Build: %BUILD_NUMBER%
                    echo Agent: Windows
                    echo Timestamp: %DATE% %TIME%
                '''
                
                echo "Duration: ${currentBuild.durationString}"
                echo "Result: ${currentBuild.currentResult}"
            }
            
            // Archive terraform state files
            archiveArtifacts artifacts: 'terraform.tfstate*', allowEmptyArchive: true, fingerprint: true
        }
        
        success {
            script {
                def successMessage = """
✅ VPC DELETION SUCCESSFUL!

VPC: testvpc1
Build: ${env.BUILD_NUMBER}
Duration: ${currentBuild.durationString}
Agent: Windows
Completed: ${new Date()}

All VPC resources have been permanently deleted:
- VPC (10.0.0.0/17)
- Public Subnet (10.0.1.0/24)
- Private Subnet (10.0.2.0/24)
- Internet Gateway
- Route Tables
- All associations
"""
                
                echo successMessage
                
                // Send notification (configure as needed)
                // emailext (
                //     subject: "✅ VPC Deletion Success - testvpc1",
                //     body: successMessage,
                //     to: "${env.CHANGE_AUTHOR_EMAIL}"
                // )
            }
        }
        
        failure {
            script {
                def failureMessage = """
❌ VPC DELETION FAILED!

VPC: testvpc1
Build: ${env.BUILD_NUMBER}
Duration: ${currentBuild.durationString}
Agent: Windows
Failed: ${new Date()}

The deletion process encountered errors.
Some resources may still exist and require manual cleanup.

Please check the build logs for details.
"""
                
                echo failureMessage
                
                // Send notification (configure as needed)
                // emailext (
                //     subject: "❌ VPC Deletion Failed - testvpc1",
                //     body: failureMessage,
                //     to: "${env.CHANGE_AUTHOR_EMAIL}"
                // )
            }
        }
        
        cleanup {
            // Clean workspace
            cleanWs()
        }
    }
}
