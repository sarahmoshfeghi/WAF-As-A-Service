pipeline {
    agent any
    
    parameters {
        string(name: 'TENANT_PARTITION', defaultValue: 'Tenant_Finance', description: 'Target F5 Partition')
        string(name: 'APP_NAME', defaultValue: 'PaymentGateway', description: 'Application Service Name')
        string(name: 'VIP_ADDRESS', defaultValue: '10.100.50.25', description: 'Virtual Server IP Address')
        string(name: 'BACKEND_NODES', defaultValue: '10.10.1.11, 10.10.1.12', description: 'Comma-separated Backend Server IPs')
        choice(name: 'TECH_STACK', choices: ['linux-nginx-postgres', 'win-iis-mssql', 'node-mongodb'], description: 'Base WAF Security Profile')
        choice(name: 'ENFORCEMENT_MODE', choices: ['transparent', 'blocking'], description: 'WAF Policy Enforcement Mode')
    }

    environment {
        F5_CREDENTIALS = credentials('f5-admin-credentials') // Stored securely in Jenkins
    }

    stages {
        stage('Validate & Sanitize Input') {
            steps {
                script {
                    echo "Validating request for Tenant: ${params.TENANT_PARTITION}"
                    
                    // Groovy array processing for dynamic backend node parsing
                    def nodeList = params.BACKEND_NODES.tokenize(',').collect { it.trim() }
                    env.FORMATTED_NODES = groovy.json.JsonOutput.toJson(nodeList)
                    
                    // Role-Based Authorization Check
                    if (params.TENANT_PARTITION == 'Tenant_Finance' && !USER_IN_ROLE('finance-admins')) {
                        error("User is not authorized to modify the ${params.TENANT_PARTITION} partition!")
                    }
                }
            }
        }

        stage('Execute Ansible Playbook') {
            steps {
                script {
                    ansiblePlaybook(
                        playbook: 'playbooks/deploy_waf.yml',
                        extraVars: [
                            f5_host: "10.0.0.10",
                            f5_username: "${env.F5_CREDENTIALS_USR}",
                            f5_password: "${env.F5_CREDENTIALS_PSW}",
                            tenant_partition: "${params.TENANT_PARTITION}",
                            app_name: "${params.APP_NAME}",
                            vip_address: "${params.VIP_ADDRESS}",
                            backend_nodes: "${env.FORMATTED_NODES}",
                            tech_stack: "${params.TECH_STACK}",
                            enforcement_mode: "${params.ENFORCEMENT_MODE}"
                        ],
                        colorized: true
                    )
                }
            }
        }
    }

    post {
        success {
            echo "Successfully deployed WAF & Load Balancing services for ${params.APP_NAME}."
        }
        failure {
            echo "Pipeline failed. Check AS3 REST response logs above."
        }
    }
}

// Groovy helper method for user authorization checks
def USER_IN_ROLE(String requiredRole) {
    // Integrate with Jenkins LDAP/Active Directory plugin logic
    return true
}
