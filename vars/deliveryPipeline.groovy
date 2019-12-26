def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        stages {
            stage('Build') {
                steps {
                    echo toAlphanumeric(text: "a_B-c-dhhhh.1")
                }
            }
            stage('Test') {
                steps {
                    script {
                        try {
                            sh './gradlew clean test --no-daemon' //run a gradle task
                        } finally {
                            junit '**/build/test-results/test/*.xml' //make the junit test results available in any case (success & failure)
                        }
                    }

                }
            }
            stage('Deploy') {
                steps {
                    echo 'Deploying....'
                }
            }
        }
        triggers {
            cron('H */8 * * *')
            pollSCM('* * * * *')
        }
    }
}