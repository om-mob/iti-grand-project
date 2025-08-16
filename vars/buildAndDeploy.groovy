// The call method allows the file to be executed like a function
def call(Map config) {
    pipeline {
        agent any

        stages {
            stage('Build and Push Docker Image') {
                steps {
                    script {
                        echo "Building Docker image: build-${BUILD_NUMBER}"
                        def customImage = docker.build("${config.dockerhubUser}/${config.imageRepo}")

                        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-credentials') {
                            echo "Pushing Docker image..."
                            customImage.push("build-${BUILD_NUMBER}")
                            customImage.push('latest')
                        }
                    }
                }
            }

            stage('Update & Commit Manifests') {
                steps {
                    script {
                        // We must clone the repo here to be able to push back
                        git url: "${config.gitUrl}", branch: "${config.gitBranch}"

                        echo "Updating Kubernetes deployment with new image..."
                        def imageName = "${config.dockerhubUser}/${config.imageRepo}"
                        def imageTag = "build-${BUILD_NUMBER}"
                        sh "sed -i 's|image: .*|image: ${imageName}:${imageTag}|' k8s/deployment.yaml"
                        
                        echo "Committing and pushing manifest changes to Git..."
                        withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                            sh "git config --global user.email 'jenkins@example.com'"
                            sh "git config --global user.name 'Jenkins CI'"
                            sh "git add k8s/deployment.yaml"
                            // We add [skip ci] to our own commit message for good practice, even if not used by Jenkins now
                            sh "git commit -m 'CI: Update image to ${imageName}:${imageTag} [skip ci]'"
                            sh "git push https://${GIT_USER}:${GIT_TOKEN}@github.com/${config.githubRepo}.git HEAD:main"
                        }
                        echo "Manifest pushed to Git. ArgoCD will now take over."
                    }
                }
            }
        }
    }
}