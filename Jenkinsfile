pipeline {
    agent any
    environment {
        K8S_LOCAL_PORT = 51971
        SERVICE = 'gateway'
        NAME = "humbertosandmann/${env.SERVICE}"
    }
    stages {
        stage('Dependecies') {
            steps {
                build job: 'store.auth', wait: true
            }
        }
        stage('Build') { 
            steps {
                sh 'mvn clean package'
            }
        }      
        stage('Build & Push Image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credential', usernameVariable: 'USERNAME', passwordVariable: 'TOKEN')]) {
                    sh "docker login -u $USERNAME -p $TOKEN"
                    sh "docker buildx create --use --platform=linux/arm64,linux/amd64 --node multi-platform-builder-${env.SERVICE} --name multi-platform-builder-${env.SERVICE}"
                    sh "docker buildx build --platform=linux/arm64,linux/amd64 --push --tag ${env.NAME}:latest --tag ${env.NAME}:${env.BUILD_ID} -f Dockerfile ."
                    sh "docker buildx rm --force multi-platform-builder-${env.SERVICE}"
                }
            }
        }
        stage('Deploy on Local K8s') {
            when { 
                environment name: 'LOCAL', value: 'true'
            }
            steps {
                withCredentials([ string(credentialsId: 'minikube-credential', variable: 'api_token') ]) {
                    sh "kubectl --token $api_token --server https://host.docker.internal:${env.K8S_LOCAL_PORT}  --insecure-skip-tls-verify=true apply -f ./k8s/deployment.yaml"
                    sh "kubectl --token $api_token --server https://host.docker.internal:${env.K8S_LOCAL_PORT}  --insecure-skip-tls-verify=true apply -f ./k8s/service.yaml"
                }
            }
        }
        stage('Deploy on MicroK8s') {
            when { 
                environment name: 'MicroK8s', value: 'true'
            }
            steps {
                sh "microk8s kubectl apply -f ./k8s/deployment.yaml"
                sh "microk8s kubectl apply -f ./k8s/service.yaml"
            }
        }
        stage('Deploy on AWS K8s') {
            when { 
                environment name: 'AWS', value: 'true'
            }
            steps {
                sh "kubectl apply -f ./k8s/deployment.yaml"
                sh "kubectl apply -f ./k8s/service.yaml"
            }
        }

    }
}