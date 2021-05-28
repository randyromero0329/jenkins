pipeline {
  agent {
    label 'jenkins-dev'
  }
  environment {
    registry = "206470280530.dkr.ecr.ap-southeast-1.amazonaws.com/campfire-api"
/*    scannerHome = tool 'SonarScanner' */
  }
  stages {
    stage('Push Notification: Start') {
        steps {
            echo '=========== Notify on Telegram Bot : Build started ============'
          script{
            withCredentials([string(credentialsId: 'telegramToken', variable: 'TOKEN'),
            string(credentialsId: 'telegramChatId', variable: 'CHAT_ID')]) {
             sh 'curl -s -X POST https://api.telegram.org/bot${TOKEN}/sendMessage -d chat_id=${CHAT_ID} -d parse_mode="HTML" -d text="<b>Hello! Build started on campfire-api-dev</b>"'
            }
         }
       }
     }    
    stage('GitLab Checkout: ENV') {
      steps {
          echo '=========== Gitlab Checkout: env & copy env to temporary directory ============'
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[credentialsId: 'git-jenkins-key', url: 'git@gitlab.paynamics.net:Campfire/campfire-env.git']]])  
        script {
            sh 'mkdir -p ~/temp-campfire-api-dev' 
            sh 'cp campfire-api/dev.env ~/temp-campfire-api-dev/.env' 
        }
     }
    }
    stage('GitLab Checkout: API') {
      steps {
        echo '=========== Gitlab Checkout: api & paste env to api.env ============'          
        checkout([$class: 'GitSCM', branches: [[name: '*/master-dev']], extensions: [], userRemoteConfigs: [[credentialsId: 'git-jenkins-key', url: 'git@gitlab.paynamics.net:Campfire/campfire-api.git']]])
        script {
            sh 'cp ~/temp-campfire-api-dev/.env .env' 
        }
     }
    }  
   stage ('Remove temp folder') {
    steps {
        script {
            echo '=========== Remove temporary directory ============'            
            sh 'rm -rf ~/temp-campfire-api-dev'
        }
    }    
    }
/*    stage('SQ Scan Analysis') {
     steps {
         script {
             echo '=========== SonarQube analysis ============'    
            withSonarQubeEnv('SonarQube') {
            sh '${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=sonar-campire-api-dev -Dsonar.projectKey=campire-api-dev -Dsonar.sources=. -Dsonar.host.url=https://sonarqube.paynamics.net -Dsonar.login=8285ec71973538e1e2a96ee5b84da2c638277919'
               }
         }
           }    
        }
    stage("SQ Quality Gate") {
     steps {
        echo '=========== SonarQube quality gate ============' 
        waitForQualityGate abortPipeline: false
            }
        }  */      
    stage('Docker Build') {
      steps {
        script {
           echo '=========== Docker Build ============'    
          dockerImage = docker.build registry
        }
      }
    }
    stage('Docker Push to ECR') {
      steps {
        script {
          echo '=========== Docker push to ECR repository ============'      
          sh 'aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 206470280530.dkr.ecr.ap-southeast-1.amazonaws.com'
          sh 'docker push 206470280530.dkr.ecr.ap-southeast-1.amazonaws.com/campfire-api:latest'
        }
      }
    }
    stage('Docker Push to ECS') {
      steps {
        script {
          echo '=========== Docker push to ECS cluster tasks ============'    
          sh 'aws ecs update-service --capacity-provider-strategy capacityProvider=FARGATE,weight=1 capacityProvider=FARGATE_SPOT,weight=1,base=5 --cluster campfire --service service-campfire-api --force-new-deployment'
        }
      }
    }
    stage('Remove Unused docker image') {
      steps {
        echo '=========== Remove unused/old docker images ============'  
        sh "docker system prune -f"
      }
    }
    stage('Push Notification: Finished') {
        steps {
            echo '=========== Notify on Telegram Bot : Build successful ============'
          script{
            withCredentials([string(credentialsId: 'telegramToken', variable: 'TOKEN'),
            string(credentialsId: 'telegramChatId', variable: 'CHAT_ID')]) {
             sh 'curl -s -X POST https://api.telegram.org/bot${TOKEN}/sendMessage -d chat_id=${CHAT_ID} -d parse_mode="HTML" -d text="<b>Congratulations! Build successful for campfire-api-dev</b>"'
            }
         }
       }
     }      
  }
}