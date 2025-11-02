pipeline {
    agent {
        agent any
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
            }
        }
        
        stage('Terraform Apply') {
            when {
                expression { params.ACTION == 'apply' }
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
                        script {
                            def applyCommand = params.AUTO_APPROVE ? 
                                "terraform apply -input=false -auto-approve tfplan-${env.BUILD_NUMBER}" :
                                "terraform apply -input=false tfplan-${env.BUILD_NUMBER}"
                            
                            bat """
                                echo === Applying Terraform Plan ===
                                echo Command: ${applyCommand}
                                ${applyCommand}
                                
                                echo === Apply Completed Successfully ===
                            """
                        }
                        
                        // Capture and display outputs
                        bat '''
                            echo === Terraform Outputs ===
                            terraform output
                            
                            echo === Saving Outputs to File ===
                            terraform output -json > terraform-outputs-%BUILD_NUMBER%.json
                            
                            echo === Infrastructure Summary ===
                            terraform output infrastructure_summary
                        '''
                        
                        // Archive outputs
                        archiveArtifacts artifacts: "terraform-outputs-${env.BUILD_NUMBER}.json", fingerprint: true
                    }
                }
            }
        }
        
        stage('Terraform Destroy') {
            when {
                expression { params.ACTION == 'destroy' }
            }
            steps {
                script {
                    def destroyMessage = """
⚠️  DESTRUCTIVE ACTION WARNING ⚠️

You are about to DESTROY the following AWS infrastructure:

- VPC: my-dev-vpc (10.0.0.0/16)
- Public Subnets: 10.0.1.0/24, 10.0.2.0/24
- Internet Gateway and Route Tables
- All associated resources

Region: us-east-1
Build: ${env.BUILD_NUMBER}

🚨 THIS ACTION CANNOT BE UNDONE! 🚨

All resources will be permanently deleted.
"""
                    
                    input message: destroyMessage,
                          ok: 'DESTROY INFRASTRUCTURE',
                          submitterParameter: 'DESTROYER'
                    
                    echo "🔥 Destruction approved by: ${env.DESTROYER}"
                }
                
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
                            echo === Creating Destroy Plan ===
                            terraform plan -destroy -input=false -out=destroy-plan-%BUILD_NUMBER%
                            
                            echo === Executing Destroy ===
                            terraform apply -input=false -auto-approve destroy-plan-%BUILD_NUMBER%
                            
                            echo === Infrastructure Destroyed ===
                        '''
                    }
                }
            }
        }
        
        stage('Post-Deployment Validation') {
            when {
                expression { params.ACTION == 'apply' }
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
                            echo === Post-Deployment Validation ===
                            
                            echo Getting VPC ID from Terraform output...
                            for /f "delims=" %%i in ('terraform output -raw vpc_id 2^>nul') do set VPC_ID=%%i
                            
                            if defined VPC_ID (
                                echo VPC ID: %VPC_ID%
                                echo Validating VPC exists in AWS...
                                aws ec2 describe-vpcs --vpc-ids %VPC_ID% --region %AWS_DEFAULT_REGION% --query "Vpcs[0].[VpcId,CidrBlock,State,Tags[?Key==`Name`].Value|[0]]" --output table
                                if %ERRORLEVEL% EQU 0 (
                                    echo ✅ VPC validation successful
                                ) else (
                                    echo ❌ VPC validation failed
                                    exit /b 1
                                )
                            ) else (
                                echo ⚠️  Could not retrieve VPC ID from Terraform output
                            )
                        '''
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "=== Pipeline Execution Summary ==="
                echo "Action: ${params.ACTION}"
                echo "Build Number: ${env.BUILD_NUMBER}"
                echo "Duration: ${currentBuild.durationString}"
                echo "Result: ${currentBuild.currentResult}"
            }
            
            // Archive important files
            dir("${env.TERRAFORM_DIR}") {
                archiveArtifacts artifacts: '*.log, *.txt', allowEmptyArchive: true
            }
        }
        
        success {
            script {
                def successMessage = """
✅ Terraform Pipeline Completed Successfully!

Action: ${params.ACTION}
VPC Name: my-dev-vpc
Region: us-east-1
Build: ${env.BUILD_NUMBER}
Duration: ${currentBuild.durationString}

Infrastructure deployed successfully with local state backend.
"""
                
                echo successMessage
                
                // Send notification (configure as needed)
                // emailext (
                //     subject: "✅ Terraform Pipeline Success - Build ${env.BUILD_NUMBER}",
                //     body: successMessage,
                //     to: "${env.CHANGE_AUTHOR_EMAIL}"
                // )
            }
        }
        
        failure {
            script {
                def failureMessage = """
❌ Terraform Pipeline Failed!

Action: ${params.ACTION}
VPC Name: my-dev-vpc
Region: us-east-1
Build: ${env.BUILD_NUMBER}
Duration: ${currentBuild.durationString}

Please check the build logs for details.
Error: ${currentBuild.description ?: 'See build logs'}
"""
                
                echo failureMessage
                
                // Send notification (configure as needed)
                // emailext (
                //     subject: "❌ Terraform Pipeline Failed - Build ${env.BUILD_NUMBER}",
                //     body: failureMessage,
                //     to: "${env.CHANGE_AUTHOR_EMAIL}"
                // )
            }
        }
        
        cleanup {
            script {
                echo "=== Cleaning up workspace ==="
                
                // Clean up sensitive files (Windows compatible)
                dir("${env.TERRAFORM_DIR}") {
                    bat '''
                        echo Removing sensitive files...
                        if exist tfplan-* del /f /q tfplan-*
                        if exist destroy-plan-* del /f /q destroy-plan-*
                        if exist *.tfstate del /f /q *.tfstate
                        if exist *.tfstate.backup del /f /q *.tfstate.backup
                        if exist .terraform\\terraform.tfstate del /f /q .terraform\\terraform.tfstate
                        echo Cleanup completed
                    '''
                }
            }
            
            // Clean workspace
            cleanWs()
        }
    }
}
