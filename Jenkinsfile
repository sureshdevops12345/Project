sshagent(credentials: Github-jenkins-linux) {

    sh "git fetch --no-tags origin '+refs/heads/master:refs/remotes/origin/master'"

            def gitDiff = sh(script: "git diff --name-only origin/master...origin/release", returnStdout: true).trim()

} 
