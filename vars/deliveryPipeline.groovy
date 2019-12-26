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
                    echo getCommitHash()
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