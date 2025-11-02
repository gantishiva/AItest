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
            
