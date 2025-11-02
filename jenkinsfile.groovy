pipeline {
    agent {
        label 'windows'
    }
    
    parameters {
        booleanParam(
            name: 'AUTO_APPROVE',
            defaultValue: false,
            description: 'Auto approve terraform apply (use with caution)'
        )
        choice(
            name: 'ACTION',
            choices: ['plan', 'apply', 'destroy'],
            description: 'Select Terraform action to perform'
        )
    }
    
    environment {
        AWS_DEFAULT_REGION = 'us-east-1'
        TF_IN_AUTOMATION = 'true'
        TF_INPUT = '0'
        TF_CLI_ARGS = '-no-color'
        TERRAFORM_DIR = 'terraform-vpc'
    }
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    echo "=== Checking out source code from GitHub ==="
                    echo "Repository: ${env.GIT_URL}"
                    echo "Branch: ${env.GIT_BRANCH}"
                }
                
                // Checkout source code
                checkout scm
                
                // Display Git information (Windows compatible)
                bat '''
                    echo === Git Information ===
                    git --version
                    git log --oneline -5
                    git branch -a
                    echo Current commit: 
                    git rev-parse HEAD
                    echo Current branch: 
                    git rev-parse --abbrev-ref HEAD
                '''
            }
        }
        
        stage('Setup Environment') {
            steps {
                script {
                    bat '''
                        echo === Environment Setup ===
                        echo Terraform Directory: %TERRAFORM_DIR%
                        echo AWS Region: %AWS_DEFAULT_REGION%
                        echo Build Number: %BUILD_NUMBER%
                        echo Workspace: %WORKSPACE%
                        
                        echo === Checking Terraform Installation ===
                        terraform version || echo Terraform not found in PATH
                        
                        echo === Checking AWS CLI Installation ===
                        aws --version || echo AWS CLI not found in PATH
                        
                        echo === Listing Terraform files ===
                        dir %TERRAFORM_DIR%
                    '''
                }
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
                    dir("${env.TERRAFORM_DIR}") {
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
        }
        
        stage('Terraform Validate') {
            steps {
                dir("${env.TERRAFORM_DIR}") {
                    bat '''
                        echo === Validating Terraform Configuration ===
                        terraform validate
                        
                        echo === Checking Terraform Format ===
                        terraform fmt -check=true -diff=true || (
                            echo WARNING: Terraform files are not properly formatted
                            echo Run "terraform fmt" to fix formatting issues
                        )
                    '''
                }
            }
        }
        
        stage('Terraform Plan') {
            when {
                anyOf {
                    expression { params.ACTION == 'plan' }
                    expression { params.ACTION == 'apply' }
                }
            }
            steps {
                withCredentials([
                    [
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    dir("${env.TERRAFORM_DIR}") {
                        bat '''
                            echo === Creating Terraform Plan ===
                            terraform plan -input=false -out=tfplan-%BUILD_NUMBER%
                            
                            echo === Saving Plan Output ===
                            terraform show -no-color tfplan-%BUILD_NUMBER% > tfplan-%BUILD_NUMBER%.txt
                            
                            echo === Plan Summary ===
                            terraform show -no-color tfplan-%BUILD_NUMBER% | findstr /C:"Plan:" || echo No changes detected
                        '''
                        
                        // Archive the plan files
                        archiveArtifacts artifacts: "tfplan-${env.BUILD_NUMBER}*", fingerprint: true
                    }
                }
            }
        }
        
        stage('Review Plan') {
            when {
                expression { params.ACTION == 'apply' && !params.AUTO_APPROVE }
            }
            steps {
                script {
                    dir("${env.TERRAFORM_DIR}") {
                        // Read plan summary for review
                        def planExists = fileExists("tfplan-${env.BUILD_NUMBER}.txt")
                        def planSummary = planExists ? 
                            readFile("tfplan-${env.BUILD_NUMBER}.txt").take(2000) : 
                            "Plan file not found"
                        
                        def approvalMessage = """
🔍 TERRAFORM PLAN REVIEW REQUIRED

Environment: Development
VPC Name: my-dev-vpc
Region: us-east-1
Build: ${env.BUILD_NUMBER}

Please review the Terraform plan carefully before approving the apply operation.

The plan will create:
- VPC with CIDR 10.0.0.0/16
- 2 Public subnets (10.0.1.0/24, 10.0.2.0/24)
- Internet Gateway
- Route tables and associations

⚠️  This will create AWS resources that may incur costs.
"""
                        
                        input message: approvalMessage,
                              ok: 'Approve Apply',
                              parameters: [
                                  text(name: 'PLAN_REVIEW', 
                                       defaultValue: planSummary, 
                                       description: 'Terraform Plan Details (First 2000 characters)')
                              ],
                              submitterParameter: 'APPROVER'
                        
                        echo "✅ Plan approved by: ${env.APPROVER}"
                    }
                }
        
