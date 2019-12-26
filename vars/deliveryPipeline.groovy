def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    def label = "mypod-${UUID.randomUUID().toString()}"
    podTemplate(label: label,
            serviceAccount: 'jenkins',
            containers: [

                    containerTemplate(
                            name: 'gradle',
                            image: 'gradle:6.0.1-jdk8',
                            ttyEnabled: true,
                            command: 'cat'
                    ),

                    containerTemplate(
                            name: 'docker',
                            image: 'docker:17.12.1-ce',
                            ttyEnabled: true,
                            command: 'cat',
                            envVars: [
                                    envVar(key: 'DOCKER_HOST', value: 'tcp://dind:2375')
                            ]
                    ),
                    containerTemplate(
                            name: 'helm',
                            image: 'lachlanevenson/k8s-helm:v2.8.1',
                            ttyEnabled: true,
                            command: 'cat'
                    )
            ],
            volumes: [
                    emptyDirVolume(mountPath: '/var/lib/docker', memory: false)
            ]) {

        node(label) {

            // Checkout code
            container('jnlp') {
                stage('Checkout code') {
                    checkout scm
                    env.commit = sh returnStdout: true, script: 'git rev-parse HEAD'
                }
            }

            container('gradle') {
                stage('Test') {

                    try {
                        sh 'gradle clean test --no-daemon' //run a gradle task
                    } catch (ex) {

                        echo ex.message

                    } finally {
                        junit '**/build/test-results/test/*.xml'
                    }
                }
            }

            // Build and push image
            container('docker') {

                stage('Build image') {
                    env.version = sh returnStdout: true, script: 'cat build.number'
                    withEnv(['VERSION=' + env.version.trim(), 'COMMIT=' + env.commit.trim()]) {
                        sh """
                            docker build \
                            -t liccioni/first-app:${VERSION}.${COMMIT}  \
                            -t liccioni/first-app:latest \
                            .
                           """
                    }
                }

                stage('Push image') {
                    withDockerRegistry([credentialsId: 'docker-registry-credentials', url: 'http://docker-registry:5000']) {
                        withEnv(['VERSION=' + env.VERSION.trim(), 'COMMIT=' + env.COMMIT.trim()]) {
                            sh "docker push liccioni/first-app:${VERSION}.${COMMIT}"
                            sh 'docker push liccioni/first-app:latest'
                        }
                    }
                }
            }

            // Deploy Helm Chart
            container('helm') {
                stage('Deploy Helm Chart') {
                    dir('charts') {
                        withEnv(['VERSION=' + env.version.trim(), 'COMMIT=' + env.commit.trim()]) {
                            sh """
                helm upgrade --install first-app \
                  --namespace production \
                  --set image.repository=liccioni/first-app \
                  --set image.tag=${VERSION}.${COMMIT} \
                  --set ingress.enabled=true \
                  --set ingress.hosts[0]=first-app-cicd.192.168.26.11.nip.io \
                  first-app
              """
                        }
                    }
                }
            }

        }
    }
}